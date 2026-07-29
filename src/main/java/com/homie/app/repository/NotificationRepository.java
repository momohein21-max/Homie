package com.homie.app.repository;

import com.homie.app.entity.Notification;
import com.homie.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Handles all database work for the Notification entity (the bell icon's
 * data).
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Most recent notifications for the bell dropdown - capped at 20 so
    // the panel never has to render an ever-growing list.
    List<Notification> findTop20ByUserOrderByCreatedDateDesc(User user);

    // Powers the badge count shown on the bell icon itself.
    long countByUserAndReadFalse(User user);

    // Bulk "mark all read" for one housemate, used by the panel's "Mark
    // all read" button - a single UPDATE rather than loading every unread
    // row into memory just to flip one field on each.
    @Modifying
    @Query("update Notification n set n.read = true where n.user = :user and n.read = false")
    void markAllAsReadForUser(@Param("user") User user);
}
