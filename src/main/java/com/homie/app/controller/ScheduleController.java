package com.homie.app.controller;

import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.service.ScheduleService;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * Shows the washing machine + bins page for the logged-in housemate's own
 * house.
 *
 * Unlike the cleaning rota (which rotates per person each week), this
 * schedule is fixed by room: each room always does washing and bins on the
 * same weekday, based on its position in the house's room order (see
 * ScheduleService). This page shows the full grid and highlights today's
 * room.
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
        User currentUser = userService.findByEmail(authentication.getName());
        var house = currentUser.getHouse();

        // The full fixed weekly schedule, with today marked.
        model.addAttribute("duties", scheduleService.weeklyDuties(house, today));

        // Which room is on today (may be null), for the summary line.
        Room roomToday = scheduleService.roomOnDutyFor(house, today);
        model.addAttribute("roomToday", roomToday);

        // All of this house's rooms with their members, for the reference panel.
        model.addAttribute("rooms", scheduleService.orderedRooms(house));

        model.addAttribute("currentUser", currentUser);

        return "schedule"; // renders templates/schedule.html
    }
}
