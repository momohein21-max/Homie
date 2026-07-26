package com.homie.app.controller;

import com.homie.app.dto.AnnouncementCreateDto;
import com.homie.app.entity.Announcement;
import com.homie.app.entity.User;
import com.homie.app.service.AnnouncementService;
import com.homie.app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Handles the Announcements board: viewing all notices posted in the
 * logged-in housemate's own house, reading one in full, posting a new one,
 * and deleting one you posted.
 */
@Controller
public class AnnouncementController {

    // Fixed list of categories offered in the "New notice" form.
    private static final List<String> CATEGORIES = List.of(
            "House rules", "Cleaning", "Bins", "Maintenance", "Social", "Other"
    );

    private final AnnouncementService announcementService;
    private final UserService userService;

    public AnnouncementController(AnnouncementService announcementService, UserService userService) {
        this.announcementService = announcementService;
        this.userService = userService;
    }

    @GetMapping("/announcements")
    public String announcements(Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());

        model.addAttribute("announcements", announcementService.allAnnouncements(currentUser.getHouse()));
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentUserId", currentUser.getId());

        if (!model.containsAttribute("announcementForm")) {
            model.addAttribute("announcementForm", new AnnouncementCreateDto());
        }

        return "announcements"; // renders templates/announcements.html
    }

    @GetMapping("/announcements/{id}")
    public String viewAnnouncement(@PathVariable Long id, Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        Announcement announcement = announcementService.findById(id);

        // Don't let a housemate view another house's notice just by
        // guessing its id in the URL.
        if (announcement.getHouse() == null || currentUser.getHouse() == null
                || !announcement.getHouse().getId().equals(currentUser.getHouse().getId())) {
            return "redirect:/announcements";
        }

        model.addAttribute("announcement", announcement);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentUserId", currentUser.getId());

        return "announcement"; // renders templates/announcement.html
    }

    @PostMapping("/announcements/create")
    public String createAnnouncement(@Valid @ModelAttribute("announcementForm") AnnouncementCreateDto dto,
                                      BindingResult result,
                                      Model model,
                                      Authentication authentication) {

        User currentUser = userService.findByEmail(authentication.getName());

        if (result.hasErrors()) {
            model.addAttribute("announcements", announcementService.allAnnouncements(currentUser.getHouse()));
            model.addAttribute("categories", CATEGORIES);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("currentUserId", currentUser.getId());
            return "announcements";
        }

        announcementService.createAnnouncement(dto, currentUser);
        return "redirect:/announcements";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {

        String error = announcementService.deleteAnnouncement(id, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("announcementsError", error);
            return "redirect:/announcements/" + id;
        }
        return "redirect:/announcements";
    }
}
