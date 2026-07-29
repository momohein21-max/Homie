package com.homie.app.service;

import com.homie.app.entity.Notification;
import com.homie.app.entity.User;
import com.homie.app.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 *
 * The one rule worth pinning down here is the ownership check in
 * markRead() - a housemate should only ever be able to mark their OWN
 * notifications read, never someone else's just by guessing an id in the
 * request (see the notification bell's /api/notifications/{id}/read
 * endpoint).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void notify_savesANewNotificationForTheGivenUser() {
        User user = userWithId(1L);

        notificationService.notify(user, "Wifi bill is due tomorrow.", "/bills");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals("Wifi bill is due tomorrow.", saved.getMessage());
        assertEquals("/bills", saved.getLink());
        assertFalse(saved.isRead());
    }

    @Test
    void unreadCountFor_delegatesToRepositoryCount() {
        User user = userWithId(1L);
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(5L);

        assertEquals(5L, notificationService.unreadCountFor(user));
    }

    @Test
    void markRead_ownNotification_marksReadAndSaves() {
        User owner = userWithId(1L);
        Notification notification = new Notification(owner, "Due today", "/bills");
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markRead(10L, owner);

        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_someoneElsesNotification_isIgnored() {
        User owner = userWithId(1L);
        User requester = userWithId(2L);
        Notification notification = new Notification(owner, "Due today", "/bills");
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markRead(10L, requester);

        assertFalse(notification.isRead());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_alreadyReadNotification_doesNotResave() {
        User owner = userWithId(1L);
        Notification notification = new Notification(owner, "Due today", "/bills");
        notification.setRead(true);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markRead(10L, owner);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllRead_delegatesToBulkRepositoryUpdate() {
        User user = userWithId(1L);

        notificationService.markAllRead(user);

        verify(notificationRepository).markAllAsReadForUser(user);
    }

    private User userWithId(Long id) {
        User user = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        user.setId(id);
        return user;
    }
}
