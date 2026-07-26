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
 * Shows the cleaning rota page for the logged-in housemate's own house.
 *
 * The whole house is cleaned by one member per week, rotating through that
 * house's own member order (see ScheduleService), looping back to the
 * start once everyone's had a turn.
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
        User currentUser = userService.findByEmail(authentication.getName());
        var house = currentUser.getHouse();

        // Who is cleaning this week.
        User currentCleaner = scheduleService.cleanerFor(house, today);
        model.addAttribute("currentCleaner", currentCleaner);

        // The full cycle through every member of this house, each tagged
        // done/current/upcoming.
        model.addAttribute("rota", scheduleService.fullRotationStatus(house, today));

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isCurrentUserCleaning",
                currentCleaner != null && currentCleaner.getId().equals(currentUser.getId()));

        return "cleaning"; // renders templates/cleaning.html
    }
}
