package com.homie.app.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single alert shown to one housemate in the notification bell.
 *
 * Right now the only thing that raises these is BillReminderScheduler
 * (upcoming/due-today/overdue bill reminders), but the shape is generic -
 * any message plus an optional link - so other features (e.g. new
 * announcements) could reuse NotificationService.notify() later without
 * needing their own table.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who this notification is for.
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String message;

    // Where clicking the notification should take the user, e.g. "/bills".
    // Optional - null just means it isn't clickable-through to anywhere.
    private String link;

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private LocalDateTime createdDate;

    public Notification() {
    }

    public Notification(User user, String message, String link) {
        this.user = user;
        this.message = message;
        this.link = link;
        this.createdDate = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
}
