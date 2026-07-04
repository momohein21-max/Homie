package com.homie.app.controller;

import com.homie.app.service.ScheduleService;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * Shows the washing machine + bins page.
 *
 * Unlike the cleaning rota (which rotates per person each week), this schedule
 * is FIXED by room: each room always washes clothes and takes out the bins on
 * the same weekday. This page shows the full Monday-to-Friday grid and
 * highlights today's room.
 */
@Controller
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserService userService;

    public ScheduleController(ScheduleService scheduleService, UserService userService) {
        this.scheduleService = scheduleService;
        this.userService = userService;
    }

    @GetMapping("/schedule")
    public String schedule(Model model, Authentication authentication) {
        LocalDate today = LocalDate.now();

        // The full fixed weekly schedule (Mon-Fri), with today marked.
        model.addAttribute("duties", scheduleService.weeklyDuties(today));

        // Which room is on today (null at weekends), for the summary line.
        String roomToday = scheduleService.roomOnDutyFor(today);
        model.addAttribute("roomToday", roomToday);
        model.addAttribute("membersToday", scheduleService.membersOf(roomToday));

        // All rooms with their members, for the reference panel.
        model.addAttribute("rooms", scheduleService.allRooms());

        if (authentication != null) {
            model.addAttribute("currentUser", userService.findByEmail(authentication.getName()));
        }

        return "schedule"; // renders templates/schedule.html
    }
}
