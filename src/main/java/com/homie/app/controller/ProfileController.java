package com.homie.app.controller;

import com.homie.app.dto.ProfileUpdateDto;
import com.homie.app.entity.User;
import com.homie.app.service.AnnouncementService;
import com.homie.app.service.BillService;
import com.homie.app.service.ScheduleService;
import com.homie.app.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Handles the "My profile" page: viewing your own details, editing them
 * (including room, duty, payment details, and profile picture), and
 * deleting your account. Also serves profile picture image bytes — both
 * your own (/profile/picture) and, so avatars can be shown around the app,
 * any housemate's by id (/profile/picture/{id}).
 *
 * Viewing OTHER housemates' full profile pages lives in MemberController,
 * since that is read-only and a different concern from editing your own.
 */
@Controller
public class ProfileController {

    private final UserService userService;
    private final BillService billService;
    private final AnnouncementService announcementService;
    private final ScheduleService scheduleService;

    public ProfileController(UserService userService, BillService billService,
                              AnnouncementService announcementService, ScheduleService scheduleService) {
        this.userService = userService;
        this.billService = billService;
        this.announcementService = announcementService;
        this.scheduleService = scheduleService;
    }

    // Show the profile page, pre-filled with the logged-in user's details.
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRoomId(user.getRoom() != null ? user.getRoom().getId() : null);
        dto.setDuty(user.getDuty());
        dto.setPaymentDetails(user.getPaymentDetails());

        model.addAttribute("profile", dto);
        model.addAttribute("hasProfilePicture", user.getProfilePicture() != null);
        // This house's actual rooms, for the room dropdown - replaces the
        // old hardcoded "Room 1".."Room 5" list.
        model.addAttribute("houseRooms", scheduleService.orderedRooms(user.getHouse()));
        model.addAttribute("currentUser", user);
        return "profile"; // renders templates/profile.html
    }

    // Handle the "Save changes" form. The picture arrives as a separate
    // multipart parameter rather than through the DTO — Thymeleaf's
    // th:field doesn't bind file inputs reliably.
    @PostMapping("/profile/update")
    public String updateProfile(@Valid @ModelAttribute("profile") ProfileUpdateDto dto,
                                 BindingResult result,
                                 @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
                                 Model model,
                                 Authentication authentication,
                                 HttpServletRequest request) {

        // If any validation rule failed (e.g. blank name), show the form again.
        if (result.hasErrors()) {
            User currentUser = userService.findByEmail(authentication.getName());
            model.addAttribute("hasProfilePicture", currentUser.getProfilePicture() != null);
            model.addAttribute("houseRooms", scheduleService.orderedRooms(currentUser.getHouse()));
            model.addAttribute("currentUser", currentUser);
            return "profile";
        }

        String currentEmail = authentication.getName();
        String error = userService.updateProfile(currentEmail, dto, profilePicture);

        if (error != null) {
            User currentUser = userService.findByEmail(currentEmail);
            model.addAttribute("profileError", error);
            model.addAttribute("hasProfilePicture", currentUser.getProfilePicture() != null);
            model.addAttribute("houseRooms", scheduleService.orderedRooms(currentUser.getHouse()));
            model.addAttribute("currentUser", currentUser);
            return "profile";
        }

        // Spring Security's login session is tied to the email used to log in.
        // If the email just changed, that session is now stale, so the safest
        // thing is to log the user out and have them log back in with the
        // new email rather than risk the app looking up the old one.
        boolean emailChanged = !currentEmail.equalsIgnoreCase(dto.getEmail());
        if (emailChanged) {
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            return "redirect:/login?emailChanged";
        }

        User updatedUser = userService.findByEmail(currentEmail);
        model.addAttribute("profileSuccess", "Your profile has been updated.");
        model.addAttribute("hasProfilePicture", updatedUser.getProfilePicture() != null);
        model.addAttribute("houseRooms", scheduleService.orderedRooms(updatedUser.getHouse()));
        model.addAttribute("currentUser", updatedUser);
        dto.setNewPassword(""); // don't echo the password back into the field
        return "profile";
    }

    // If the uploaded file is bigger than Spring's multipart limit, this
    // fires BEFORE our own 5MB check in UserService ever runs — Spring
    // rejects it while reading the request. Without this handler the user
    // would see a raw "413 Content Too Large" whitelabel error page instead
    // of a normal form with a friendly message.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("profileError",
                "That picture is too large. Please choose an image under 5MB.");
        return "redirect:/profile";
    }

    // Serve the logged-in user's own profile picture as raw image bytes.
    @GetMapping("/profile/picture")
    public ResponseEntity<byte[]> profilePicture(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return pictureResponseFor(user);
    }

    // Serve a housemate's profile picture by id. Used for avatars on the
    // dashboard, cleaning rota, bins page, housemates grid, bills, and
    // announcements. Only shown to a housemate in the SAME HOUSE as the
    // picture's owner - with any number of independent houses now sharing
    // Homie, this stops one house's members from being able to view
    // another house's profile pictures just by guessing user ids in the URL.
    @GetMapping("/profile/picture/{id}")
    public ResponseEntity<byte[]> profilePictureById(@PathVariable Long id, Authentication authentication) {
        User requester = userService.findByEmail(authentication.getName());
        Optional<User> user = userService.findById(id);
        if (user.isEmpty() || user.get().getHouse() == null || requester.getHouse() == null
                || !user.get().getHouse().getId().equals(requester.getHouse().getId())) {
            return ResponseEntity.notFound().build();
        }
        return pictureResponseFor(user.get());
    }

    // Shared helper: turns a User's stored picture bytes into an image response.
    private ResponseEntity<byte[]> pictureResponseFor(User user) {
        if (user.getProfilePicture() == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType contentType = user.getProfilePictureType() != null
                ? MediaType.parseMediaType(user.getProfilePictureType())
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(user.getProfilePicture());
    }

    // Handle account deletion: clean up everything that points at this
    // account (bills they created, their own share of every other bill,
    // and every notice they posted) before removing the user itself, then
    // log them out. Skipping the cleanup step would make the database
    // reject the deletion outright, since bills, bill payments, and
    // announcements are all required to point at a real housemate.
    //
    // Checked BEFORE any of that cleanup runs: is this account even safe
    // to delete? A house's owner can't be removed while other housemates
    // still live there (see UserService.checkAccountDeletable) - catching
    // that first avoids deleting someone's bills and announcements only
    // to then fail on the account itself, which would leave things in a
    // half-deleted state with no way back.
    @PostMapping("/profile/delete")
    public String deleteProfile(Authentication authentication, HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        String blockedReason = userService.checkAccountDeletable(authentication.getName());
        if (blockedReason != null) {
            redirectAttributes.addFlashAttribute("profileError", blockedReason);
            return "redirect:/profile";
        }

        User user = userService.findByEmail(authentication.getName());

        billService.deleteAllForUser(user.getId(), user.getHouse());
        announcementService.deleteAllForUser(user.getId());
        userService.deleteAccount(authentication.getName());

        // The account no longer exists, so end the session immediately.
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();

        return "redirect:/login?deleted";
    }
}
