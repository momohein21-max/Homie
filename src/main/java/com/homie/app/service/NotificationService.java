package com.homie.app.service;

import com.homie.app.entity.Notification;
import com.homie.app.entity.User;
import com.homie.app.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Holds the business logic for in-app notifications - the bell icon shown
 * in every logged-in page's topbar (see fragments/notification-bell.html).
 *
 * Right now the only thing that raises these is BillReminderScheduler
 * (upcoming/due-today/overdue bill reminders), but notify() is kept
 * generic so other features could reuse it later.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // Raises a new notification for one housemate. link may be null if
    // there's nowhere sensible to send them when they click it.
    public void notify(User user, String message, String link) {
        notificationRepository.save(new Notification(user, message, link));
    }

    // The most recent notifications for the bell dropdown.
    public List<Notification> recentFor(User user) {
        return notificationRepository.findTop20ByUserOrderByCreatedDateDesc(user);
    }

    // How many unread notifications this housemate has, for the badge.
    public long unreadCountFor(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    // Marks one notification read, but only if it actually belongs to the
    // person asking - stops someone from marking another housemate's
    // notifications read just by guessing ids in the request.
    @Transactional
    public void markRead(Long notificationId, User requester) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (notification.getUser().getId().equals(requester.getId()) && !notification.isRead()) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        });
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllAsReadForUser(user);
    }
}
