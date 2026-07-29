package com.homie.app.config;

import com.homie.app.entity.User;
import com.homie.app.service.NotificationService;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds "unreadNotificationCount" to the model of every request
 * automatically, so the notification bell fragment (included in every
 * logged-in page's topbar - see fragments/notification-bell.html) can
 * show the right badge count without every single controller having to
 * remember to add it themselves.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final UserService userService;
    private final NotificationService notificationService;

    public GlobalModelAttributes(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return 0;
        }
        try {
            User user = userService.findByEmail(authentication.getName());
            return notificationService.unreadCountFor(user);
        } catch (IllegalStateException e) {
            // The session outlived the account (e.g. it was just
            // deleted) - fail quietly rather than break every page's
            // rendering over a badge count.
            return 0;
        }
    }
}
