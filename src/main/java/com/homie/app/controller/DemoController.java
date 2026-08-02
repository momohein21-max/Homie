package com.homie.app.controller;

import com.homie.app.service.BillReminderScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A single demo-only button: "send bill reminders now". Not a real feature
 * housemates would use day-to-day - BillReminderScheduler already handles
 * that for real, automatically, once a day at 8am. That's exactly the
 * problem for a live presentation or a quick sanity check, though: a cron
 * job that only fires once a day, and only for bills due within a day of
 * today, gives you no way to actually SEE a reminder go out on demand.
 *
 * This exists purely so that during a demo (or while testing locally) you
 * can create a bill due today, click one button, and immediately see both
 * the in-app notification and the reminder email arrive - without waiting
 * for 8am or fiddling with the bill's due date. It calls the exact same
 * method the real scheduled job calls, so what you see here is genuinely
 * what housemates would eventually get - just on demand instead of at 8am.
 *
 * Deliberately left open to any logged-in housemate (no admin/owner check)
 * since Homie has no "admin" role and this can only ever notify people
 * about bill shares that are already genuinely due/overdue - it can't
 * create fake debt or notify anyone about something that isn't real.
 */
@Controller
public class DemoController {

    private final BillReminderScheduler billReminderScheduler;

    public DemoController(BillReminderScheduler billReminderScheduler) {
        this.billReminderScheduler = billReminderScheduler;
    }

    @PostMapping("/demo/send-bill-reminders-now")
    public String sendBillRemindersNow(RedirectAttributes redirectAttributes) {
        billReminderScheduler.sendBillDueReminders();
        redirectAttributes.addFlashAttribute("billsSuccess",
                "Reminders sent for any bill share due yesterday, today, or tomorrow.");
        return "redirect:/bills";
    }
}
