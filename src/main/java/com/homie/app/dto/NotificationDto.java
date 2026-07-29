package com.homie.app.dto;

import com.homie.app.entity.Notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * What the notification bell's JavaScript actually receives from
 * GET /api/notifications - deliberately flat and simple (no nested
 * User/Bill objects, no raw LocalDateTime) so it serializes cleanly with
 * this project's minimal Jackson setup (see pom.xml's notes on that) and
 * needs no date parsing on the client - the display string is already
 * formatted server-side.
 */
public class NotificationDto {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    private Long id;
    private String message;
    private String link;
    private boolean read;
    private String createdDisplay;

    public static NotificationDto from(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.id = notification.getId();
        dto.message = notification.getMessage();
        dto.link = notification.getLink();
        dto.read = notification.isRead();
        dto.createdDisplay = notification.getCreatedDate() != null
                ? notification.getCreatedDate().format(DISPLAY_FORMAT)
                : "";
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return read;
    }

    public String getCreatedDisplay() {
        return createdDisplay;
    }
}
