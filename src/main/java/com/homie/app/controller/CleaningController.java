package com.homie.app.controller;

import com.homie.app.entity.User;
import com.homie.app.service.ScheduleService;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * Shows the cleaning rota page.
 *
 * The whole house is cleaned by one person per week, rotating through a fixed
 * order. This page shows who is on this week and the full 9-week cycle, each
 * housemate tagged as done, current, or upcoming for this cycle.
 */
@Controller
public class CleaningController {

    private final ScheduleService scheduleService;
    private final UserService userService;

    public CleaningController(ScheduleService scheduleService, UserService userService) {
        this.scheduleService = scheduleService;
        this.userService = userService;
    }

    @GetMapping("/cleaning")
    public String cleaning(Model model, Authentication authentication) {
        LocalDate today = LocalDate.now();

        // Who is cleaning this week.
        String currentCleaner = scheduleService.cleanerFor(today);
        model.addAttribute("currentCleaner", currentCleaner);

        // The full 9-person cycle, each tagged done/current/upcoming.
        model.addAttribute("rota", scheduleService.fullRotationStatus(today));

        if (authentication != null) {
            User currentUser = userService.findByEmail(authentication.getName());
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isCurrentUserCleaning", currentCleaner.equals(currentUser.getName()));
        }

        return "cleaning"; // renders templates/cleaning.html
    }
}
