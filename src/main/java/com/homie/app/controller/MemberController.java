package com.homie.app.controller;

import com.homie.app.entity.User;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Handles read-only viewing of housemates: the "Housemates" grid showing
 * everyone at a glance, and clicking through to one person's own page.
 *
 * This is deliberately separate from ProfileController, which only ever
 * edits the CURRENTLY LOGGED-IN user's own account. Nothing in this
 * controller can change any data.
 *
 * Bins day, room type, and next-cleaning-week for each housemate are all
 * computed straight in the template via the ScheduleService Spring bean
 * ("@scheduleService.xxx(...)"), so this controller only needs to supply
 * the list of housemates and today's date.
 */
@Controller
public class MemberController {

    private final UserService userService;

    public MemberController(UserService userService) {
        this.userService = userService;
    }

    // The "Housemates" grid: everyone in the house, with their room, bins
    // day, and next cleaning week at a glance.
    @GetMapping("/members")
    public String members(Model model, Authentication authentication) {
        model.addAttribute("members", userService.findAllSortedByName());
        model.addAttribute("today", LocalDate.now());
        addCurrentUser(model, authentication);
        return "members"; // renders templates/members.html
    }

    @GetMapping("/members/{id}")
    public String viewMember(@PathVariable Long id, Model model, Authentication authentication) {
        Optional<User> member = userService.findById(id);

        if (member.isEmpty()) {
            // No such housemate (e.g. their account was since deleted).
            return "redirect:/members";
        }

        model.addAttribute("member", member.get());
        model.addAttribute("today", LocalDate.now());
        addCurrentUser(model, authentication);
        return "member"; // renders templates/member.html
    }

    // Shared: the logged-in user, for the sidebar footer (avatar/name/room).
    private void addCurrentUser(Model model, Authentication authentication) {
        if (authentication != null) {
            model.addAttribute("currentUser", userService.findByEmail(authentication.getName()));
        }
    }
}
