package com.homie.app.controller;

import com.homie.app.dto.ProfileUpdateDto;
import com.homie.app.entity.User;
import com.homie.app.service.AnnouncementService;
import com.homie.app.service.BillService;
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

    public ProfileController(UserService userService, BillService billService,
                              AnnouncementService announcementService) {
        this.userService = userService;
        this.billService = billService;
        this.announcementService = announcementService;
    }

    // Show the profile page, pre-filled with the logged-in user's details.
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRoom(user.getRoom());
        dto.setDuty(user.getDuty());
        dto.setPaymentDetails(user.getPaymentDetails());

        model.addAttribute("profile", dto);
        model.addAttribute("hasProfilePicture", user.getProfilePicture() != null);
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
            model.addAttribute("hasProfilePicture",
                    userService.findByEmail(authentication.getName()).getProfilePicture() != null);
            return "profile";
        }

        String currentEmail = authentication.getName();
        String error = userService.updateProfile(currentEmail, dto, profilePicture);

        if (error != null) {
            model.addAttribute("profileError", error);
            model.addAttribute("hasProfilePicture",
                    userService.findByEmail(currentEmail).getProfilePicture() != null);
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

        model.addAttribute("profileSuccess", "Your profile has been updated.");
        model.addAttribute("hasProfilePicture",
                userService.findByEmail(currentEmail).getProfilePicture() != null);
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

    // Serve ANY housemate's profile picture by id. Used for avatars on the
    // dashboard's "House & responsibilities" panel and the member detail
    // page. Safe to expose to any logged-in housemate — this is a shared
    // house app, not a public one (SecurityConfig already requires login
    // for every route except /login and /register).
    @GetMapping("/profile/picture/{id}")
    public ResponseEntity<byte[]> profilePictureById(@PathVariable Long id) {
        Optional<User> user = userService.findById(id);
        if (user.isEmpty()) {
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
    @PostMapping("/profile/delete")
    public String deleteProfile(Authentication authentication, HttpServletRequest request) {
        User user = userService.findByEmail(authentication.getName());

        billService.deleteAllForUser(user.getId());
        announcementService.deleteAllForUser(user.getId());
        userService.deleteAccount(authentication.getName());

        // The account no longer exists, so end the session immediately.
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();

        return "redirect:/login?deleted";
    }
}
