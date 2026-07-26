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
 * everyone in the logged-in user's own house at a glance, and clicking
 * through to one person's own page.
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

    // The "Housemates" grid: everyone in the logged-in user's house, with
    // their room, bins day, and next cleaning week at a glance.
    @GetMapping("/members")
    public String members(Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        model.addAttribute("members", userService.findAllInHouseSortedByName(currentUser.getHouse()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("currentUser", currentUser);
        return "members"; // renders templates/members.html
    }

    @GetMapping("/members/{id}")
    public String viewMember(@PathVariable Long id, Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        Optional<User> member = userService.findById(id);

        // No such housemate, or they belong to a different house entirely
        // (don't let someone view another house's member by guessing an id).
        if (member.isEmpty() || member.get().getHouse() == null || currentUser.getHouse() == null
                || !member.get().getHouse().getId().equals(currentUser.getHouse().getId())) {
            return "redirect:/members";
        }

        model.addAttribute("member", member.get());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("currentUser", currentUser);
        return "member"; // renders templates/member.html
    }
}
