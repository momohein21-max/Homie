package com.homie.app.service;

import com.homie.app.entity.Bill;
import com.homie.app.entity.BillPayment;
import com.homie.app.entity.User;
import com.homie.app.repository.BillPaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Once a day, checks every unpaid bill share across every house and
 * reminds whoever still owes money - one day before it's due, on the due
 * date itself, and again the day it first becomes overdue. Each housemate
 * gets both an in-app notification (the bell) and an email, reusing
 * NotificationService and EmailService respectively.
 *
 * Deliberately only fires on those three specific days rather than every
 * day a bill stays unpaid/overdue - since this job runs once daily, that
 * naturally means at most one reminder per housemate per bill per
 * milestone, with no separate "already notified" bookkeeping needed.
 */
@Component
public class BillReminderScheduler {

    private final BillPaymentRepository billPaymentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public BillReminderScheduler(BillPaymentRepository billPaymentRepository,
                                  NotificationService notificationService,
                                  EmailService emailService) {
        this.billPaymentRepository = billPaymentRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    // Runs once a day at 8am server time. Worth knowing: Render's free
    // tier spins the app down after inactivity, so this only actually
    // fires if the app happens to be awake at 8am, or gets woken by a
    // visit shortly after - fine for a student project, but not a
    // guarantee the way it would be on an always-on plan.
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendBillDueReminders() {
        LocalDate today = LocalDate.now();
        List<LocalDate> milestoneDates = List.of(today.minusDays(1), today, today.plusDays(1));

        List<BillPayment> duePayments = billPaymentRepository.findByPaidFalseAndBill_DueDateIn(milestoneDates);

        for (BillPayment payment : duePayments) {
            Bill bill = payment.getBill();
            User user = payment.getUser();
            String share = String.format(Locale.ENGLISH, "%.2f", bill.getShareAmount());

            String message;
            String subject;
            if (bill.getDueDate().isBefore(today)) {
                message = "\"" + bill.getDescription() + "\" is overdue - your share is €" + share + ".";
                subject = "Overdue: " + bill.getDescription();
            } else if (bill.getDueDate().isEqual(today)) {
                message = "\"" + bill.getDescription() + "\" is due today - your share is €" + share + ".";
                subject = "Due today: " + bill.getDescription();
            } else {
                message = "\"" + bill.getDescription() + "\" is due tomorrow - your share is €" + share + ".";
                subject = "Due tomorrow: " + bill.getDescription();
            }

            notificationService.notify(user, message, "/bills");
            emailService.sendBillReminderEmail(user.getEmail(), subject,
                    "Hi " + user.getName() + ",\n\n" + message +
                            "\n\nYou can mark it as paid, or see the full breakdown, on the Bills page in Homie.\n\n- Homie");
        }
    }
}
