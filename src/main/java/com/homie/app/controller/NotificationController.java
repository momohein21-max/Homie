package com.homie.app.controller;

import com.homie.app.dto.NotificationDto;
import com.homie.app.entity.User;
import com.homie.app.service.NotificationService;
import com.homie.app.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The small JSON API the notification bell's JavaScript talks to - list
 * recent notifications, and mark one (or all) as read. See
 * fragments/notification-bell.html for the client side.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public List<NotificationDto> list(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return notificationService.recentFor(user).stream().map(NotificationDto::from).toList();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        notificationService.markRead(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        notificationService.markAllRead(user);
        return ResponseEntity.ok().build();
    }
}
