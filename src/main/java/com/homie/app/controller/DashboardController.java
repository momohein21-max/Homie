package com.homie.app.controller;

import com.homie.app.entity.User;
import com.homie.app.repository.UserRepository;
import com.homie.app.service.AnnouncementService;
import com.homie.app.service.BillService;
import com.homie.app.service.ScheduleService;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Builds the dashboard (home) page after login.
 */
@Controller
public class DashboardController {

    private final ScheduleService scheduleService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final BillService billService;
    private final AnnouncementService announcementService;

    public DashboardController(ScheduleService scheduleService,
                               UserRepository userRepository,
                               UserService userService,
                               BillService billService,
                               AnnouncementService announcementService) {
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.billService = billService;
        this.announcementService = announcementService;
    }

    @GetMapping("/")
    public String dashboard(Model model, Authentication authentication) {
        LocalDate today = LocalDate.now();

        model.addAttribute("cleanerThisWeek", scheduleService.cleanerFor(today));
        model.addAttribute("nextCleaner", scheduleService.upNextCleaners(today, 1).get(0));
        model.addAttribute("roomToday", scheduleService.roomOnDutyFor(today));
        model.addAttribute("membersToday", scheduleService.membersOf(scheduleService.roomOnDutyFor(today)));
        model.addAttribute("today",
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));

        // A time-of-day greeting, e.g. "Good morning, Momo."
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        model.addAttribute("greeting", greeting);

        // Look up the logged-in user by their email (the login username) and
        // show their NAME, not their email.
        User currentUser = null;
        if (authentication != null) {
            String email = authentication.getName();
            currentUser = userRepository.findByEmailIgnoreCase(email).orElse(null);
        }
        if (currentUser != null) {
            model.addAttribute("memberName", currentUser.getName());
            model.addAttribute("currentUser", currentUser);

            // Real outstanding-bills figures, replacing the old placeholders.
            model.addAttribute("outstandingTotal", billService.outstandingTotal());
            model.addAttribute("yourShareDue", billService.yourShareDue(currentUser.getId()));
        } else {
            model.addAttribute("outstandingTotal", 0.0);
            model.addAttribute("yourShareDue", 0.0);
        }

        // A short list of unpaid bills, for the "unpaid" note under the stat card.
        long unpaidBillCount = billService.allBills().stream()
                .filter(bill -> !bill.isFullyPaid())
                .count();
        model.addAttribute("unpaidBillCount", unpaidBillCount);

        // The current week plus the next few, for the "Cleaning rota" mini panel.
        model.addAttribute("rotaMini", scheduleService.upcomingRota(today, 5));

        // The 3 most recent house notices, for the "Recent announcements" panel.
        model.addAttribute("announcements", announcementService.recentAnnouncements(3));

        return "dashboard";
    }
}
