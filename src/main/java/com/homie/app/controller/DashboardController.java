package com.homie.app.controller;

import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.UserRepository;
import com.homie.app.service.AnnouncementService;
import com.homie.app.service.BillService;
import com.homie.app.service.ScheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Builds the dashboard (home) page after login - always scoped to the
 * logged-in housemate's own house.
 */
@Controller
public class DashboardController {

    private final ScheduleService scheduleService;
    private final UserRepository userRepository;
    private final BillService billService;
    private final AnnouncementService announcementService;

    public DashboardController(ScheduleService scheduleService,
                               UserRepository userRepository,
                               BillService billService,
                               AnnouncementService announcementService) {
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
        this.billService = billService;
        this.announcementService = announcementService;
    }

    @GetMapping("/")
    public String dashboard(Model model, Authentication authentication) {
        LocalDate today = LocalDate.now();

        // A time-of-day greeting, e.g. "Good morning, Momo."
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        model.addAttribute("greeting", greeting);
        model.addAttribute("today",
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));

        // Look up the logged-in user by their email (the login username).
        User currentUser = null;
        if (authentication != null) {
            currentUser = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        }

        if (currentUser != null) {
            House house = currentUser.getHouse();

            model.addAttribute("memberName", currentUser.getName());
            model.addAttribute("currentUser", currentUser);

            User cleanerThisWeek = scheduleService.cleanerFor(house, today);
            model.addAttribute("cleanerThisWeek", cleanerThisWeek);
            model.addAttribute("nextCleaner", scheduleService.upNextCleaners(house, today, 1).stream()
                    .findFirst().orElse(null));

            Room roomToday = scheduleService.roomOnDutyFor(house, today);
            model.addAttribute("roomToday", roomToday);

            model.addAttribute("outstandingTotal", billService.outstandingTotal(house));
            model.addAttribute("yourShareDue", billService.yourShareDue(house, currentUser.getId()));

            long unpaidBillCount = billService.allBills(house).stream()
                    .filter(bill -> !bill.isFullyPaid())
                    .count();
            model.addAttribute("unpaidBillCount", unpaidBillCount);

            // The current week plus the next few, for the "Cleaning rota" mini panel.
            model.addAttribute("rotaMini", scheduleService.upcomingRota(house, today, 5));

            // The 3 most recent house notices, for the "Recent announcements" panel.
            model.addAttribute("announcements", announcementService.recentAnnouncements(house, 3));
        } else {
            model.addAttribute("outstandingTotal", 0.0);
            model.addAttribute("yourShareDue", 0.0);
            model.addAttribute("unpaidBillCount", 0);
        }

        return "dashboard";
    }
}
