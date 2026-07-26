package com.homie.app.service;

import com.homie.app.dto.ProfileUpdateDto;
import com.homie.app.dto.RegistrationDto;
import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.RoomRepository;
import com.homie.app.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds the business logic for working with users.
 *
 * Sprint 1 only had registration here. Sprint 2 adds the "My profile"
 * feature: reading a user's own details, updating them (including an
 * optional room, duty, payment details, and profile picture), deleting the
 * account, and looking members up so they can be shown to other housemates
 * on the dashboard and member detail page. This revision adds a self-service
 * "forgot password" flow.
 */
@Service
public class UserService {

    // Reject profile pictures bigger than this so nobody accidentally
    // fills the database with a huge image. Kept in sync with the
    // spring.servlet.multipart.max-file-size property.
    private static final long MAX_PICTURE_BYTES = 5 * 1024 * 1024; // 5MB

    // How long a password reset link stays valid after it's requested.
    private static final long RESET_TOKEN_VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final HouseService houseService;

    public UserService(UserRepository userRepository, RoomRepository roomRepository,
                        PasswordEncoder passwordEncoder, EmailService emailService,
                        HouseService houseService) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.houseService = houseService;
    }

    /**
     * Creates and saves a new user from the registration form, then either
     * starts a brand new house for them or adds them to an existing one,
     * depending on which action they picked (see RegistrationDto).
     *
     * Returns an error message if something went wrong (email already
     * taken, missing house details, or an invite code that doesn't match
     * any house), or null if registration succeeded.
     */
    public String register(RegistrationDto dto) {
        // Do not allow two accounts with the same email.
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            return "That email is already registered.";
        }

        // Turn the plain-text password into a secure BCrypt hash before saving.
        String hashedPassword = passwordEncoder.encode(dto.getPassword());

        // Every new housemate starts as a normal user. House/room are set
        // by HouseService below, once we know which path they're taking.
        User user = new User(
                dto.getName(),
                dto.getEmail(),
                hashedPassword,
                "ROLE_USER"
        );

        if ("JOIN".equalsIgnoreCase(dto.getAction())) {
            return houseService.joinHouse(dto.getInviteCode(), user);
        }

        // Anything other than "JOIN" is treated as "CREATE" - the default
        // and the only other real option the form offers.
        if (dto.getHouseName() == null || dto.getHouseName().isBlank()) {
            return "Please enter a name for your house.";
        }
        int roomCount = dto.getRoomCount() != null ? dto.getRoomCount() : HouseService.MIN_ROOMS;
        if (roomCount < HouseService.MIN_ROOMS || roomCount > HouseService.MAX_ROOMS) {
            return "Please choose between " + HouseService.MIN_ROOMS
                    + " and " + HouseService.MAX_ROOMS + " rooms.";
        }
        houseService.createHouse(dto.getHouseName(), roomCount, user);
        return null;
    }

    /**
     * Looks up the currently logged-in user by their email (their username).
     * Used by ProfileController to pre-fill the "My profile" form.
     */
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Logged-in user not found with email: " + email));
    }

    /**
     * Looks up any housemate by their id. Returns empty if no such user
     * exists (e.g. their account was deleted, or a bad id was typed into
     * the URL). Used when viewing another housemate's profile.
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Returns every housemate IN THE GIVEN HOUSE, sorted alphabetically by
     * name, for the Housemates grid. Deliberately scoped to one house
     * (rather than the old "every user in the whole app") - with any
     * number of independent houses now sharing Homie, showing every
     * registered user everywhere would leak one house's housemates to
     * every other house.
     */
    public List<User> findAllInHouseSortedByName(House house) {
        return userRepository.findByHouse(house).stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    /**
     * Applies changes from the "My profile" form to the logged-in user,
     * including an optional new profile picture.
     *
     * Returns null if the update succeeded, or an error message to show
     * on the form if something was wrong (e.g. the new email is already
     * taken, the new password is too short, or the picture isn't a valid
     * image).
     */
    public String updateProfile(String currentEmail, ProfileUpdateDto dto, MultipartFile profilePicture) {
        User user = findByEmail(currentEmail);

        // If the email is changing, make sure no other account already uses it.
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(dto.getEmail());
        if (emailChanged && userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            return "That email is already registered to another account.";
        }

        // Only touch the password if the user actually typed a new one.
        String newPassword = dto.getNewPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) {
                return "New password must be at least 6 characters.";
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        // Only touch the picture if the user actually chose a file.
        if (profilePicture != null && !profilePicture.isEmpty()) {
            String contentType = profilePicture.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return "Profile picture must be an image file (JPG or PNG).";
            }
            if (profilePicture.getSize() > MAX_PICTURE_BYTES) {
                return "Profile picture must be smaller than 5MB.";
            }
            try {
                user.setProfilePicture(profilePicture.getBytes());
                user.setProfilePictureType(contentType);
            } catch (IOException e) {
                return "Something went wrong reading that image. Please try again.";
            }
        }

        // The room dropdown submits a room id (or blank for "Not set"). Only
        // accept a room that actually belongs to this housemate's own
        // house - otherwise someone could edit the form to move into a
        // room in a different house entirely.
        if (dto.getRoomId() == null) {
            user.setRoom(null);
        } else {
            Room room = roomRepository.findById(dto.getRoomId()).orElse(null);
            if (room == null || room.getHouse() == null
                    || !room.getHouse().getId().equals(user.getHouse().getId())) {
                return "Please choose a room from your own house.";
            }
            user.setRoom(room);
        }

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setDuty(dto.getDuty());
        user.setPaymentDetails(dto.getPaymentDetails());
        userRepository.save(user);

        return null; // null means "no error" i.e. success
    }

    /**
     * Checks whether this housemate's account can safely be deleted right
     * now. Returns an error message if not, or null if it's fine to go
     * ahead and call deleteAccount().
     *
     * The one case this blocks: a house's owner (its creator) can't be
     * deleted while other housemates still live there, since
     * House.createdBy can never be null - there would be nobody left to
     * "own" the house. If the owner is the LAST person in the house,
     * deleting them is fine (deleteAccount() removes the now-empty house
     * too). Must be checked before any destructive cleanup runs (bills,
     * announcements, the account itself) - see ProfileController, which
     * calls this first and only proceeds if it returns null.
     */
    public String checkAccountDeletable(String email) {
        User user = findByEmail(email);
        if (user.isHouseOwner()) {
            long memberCount = userRepository.countByHouse(user.getHouse());
            if (memberCount > 1) {
                return "You created " + user.getHouse().getName() + " and other housemates are still using it. "
                        + "Everyone else will need to leave (or you'll need to hand off ownership some other way) "
                        + "before your account can be deleted.";
            }
        }
        return null;
    }

    /**
     * Permanently deletes the logged-in user's account. If they were the
     * sole member and owner of their house, the now-empty house (and its
     * rooms) is deleted right along with them - there'd be nobody left to
     * use it anyway. Callers should check checkAccountDeletable() first;
     * this method doesn't re-check, since by the time it's called the
     * caller has usually already deleted the user's bills/announcements,
     * which can't easily be undone if this method then refused to finish.
     *
     * Every write here goes through a plain SQL / native-query repository
     * method rather than Hibernate's normal entity save()/delete(). That's
     * deliberate: House and User point at each other (House.createdBy is a
     * required, non-nullable link back to the very user being deleted
     * here), and asking Hibernate to manage that deletion through its
     * usual entity graph repeatedly trips over its own bookkeeping for
     * that relationship - either trying to null out a required column
     * before a delete, or refusing to flush because it thinks a still-
     * referenced entity looks "unsaved". Plain SQL has none of that
     * bookkeeping, so it just does the deletes in the one order that's
     * actually valid for the foreign keys involved:
     *   1. detach this user from their house/room (so nothing still
     *      points at the house once it's gone)
     *   2. delete the house's rooms, then the house itself (only reached
     *      if this user was its owner and its last remaining member)
     *   3. delete the user
     */
    @Transactional
    public void deleteAccount(String email) {
        User user = findByEmail(email);
        House house = user.getHouse();
        boolean wasOwner = user.isHouseOwner();
        Long userId = user.getId();

        if (wasOwner && house != null) {
            userRepository.detachFromHouseAndRoomNative(userId);
            houseService.deleteHouse(house);
        }

        userRepository.deleteByIdNative(userId);
    }

    /**
     * Starts a password reset for whoever owns this email, if anyone does.
     *
     * Deliberately does NOT reveal whether the email matched an account -
     * the controller shows the same "check your email" message either way.
     * This stops someone from using the forgot-password form to test which
     * email addresses are registered to real accounts.
     */
    public void requestPasswordReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email);
        if (maybeUser.isEmpty()) {
            return; // silently do nothing - see note above
        }

        User user = maybeUser.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    /**
     * Checks whether a password reset link is still valid - it exists and
     * hasn't expired. Used to decide whether to show the "choose a new
     * password" form or an error message.
     */
    public boolean isResetTokenValid(String token) {
        return userRepository.findByResetToken(token)
                .filter(user -> user.getResetTokenExpiry() != null
                        && user.getResetTokenExpiry().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    /**
     * Sets a new password for whoever owns this reset token, then clears
     * the token so the same link can never be used again.
     *
     * Returns an error message if the token is missing/expired or the two
     * password fields didn't match, or null on success.
     */
    public String resetPassword(String token, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            return "Those passwords don't match.";
        }

        Optional<User> maybeUser = userRepository.findByResetToken(token)
                .filter(user -> user.getResetTokenExpiry() != null
                        && user.getResetTokenExpiry().isAfter(LocalDateTime.now()));

        if (maybeUser.isEmpty()) {
            return "That reset link is invalid or has expired. Please request a new one.";
        }

        User user = maybeUser.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        return null;
    }
}
