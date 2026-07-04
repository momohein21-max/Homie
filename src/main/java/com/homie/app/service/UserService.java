package com.homie.app.service;

import com.homie.app.dto.ProfileUpdateDto;
import com.homie.app.dto.RegistrationDto;
import com.homie.app.entity.User;
import com.homie.app.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Creates and saves a new user from the registration form.
     * Returns false if the email is already in use, true if registration worked.
     */
    public boolean register(RegistrationDto dto) {
        // Do not allow two accounts with the same email.
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            return false;
        }

        // Turn the plain-text password into a secure BCrypt hash before saving.
        String hashedPassword = passwordEncoder.encode(dto.getPassword());

        // Every new housemate starts as a normal user.
        User user = new User(
                dto.getName(),
                dto.getEmail(),
                hashedPassword,
                "ROLE_USER"
        );

        // The room picker on the register page is optional — a housemate
        // can always set or change it later on their profile.
        if (dto.getRoom() != null && !dto.getRoom().isBlank()) {
            user.setRoom(dto.getRoom());
        }

        userRepository.save(user);
        return true;
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
     * Returns every housemate, sorted alphabetically by name, for the
     * dashboard's "House & responsibilities" panel.
     */
    public List<User> findAllSortedByName() {
        return userRepository.findAll().stream()
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

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setRoom(dto.getRoom());
        user.setDuty(dto.getDuty());
        user.setPaymentDetails(dto.getPaymentDetails());
        userRepository.save(user);

        return null; // null means "no error" i.e. success
    }

    /**
     * Permanently deletes the logged-in user's account.
     */
    public void deleteAccount(String email) {
        User user = findByEmail(email);
        userRepository.delete(user);
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
