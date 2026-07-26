package com.homie.app.service;

import com.homie.app.entity.User;
import org.springframework.stereotype.Service;

/**
 * Gives every housemate a consistent avatar colour and initials, used
 * anywhere a small circular avatar is shown (sidebar, cleaning rota, bins
 * page, housemates grid, bills, announcements, etc.) — and, if that
 * housemate has uploaded a real profile picture, the URL to show that
 * instead of the coloured initials.
 *
 * Registered as a normal Spring bean so Thymeleaf templates can call it
 * directly as "@avatarService.color(user)" / "@avatarService.initials(user)"
 * / "@avatarService.pictureUrl(user)" without every controller having to
 * pass avatar data through the model separately.
 *
 * IMPORTANT: this used to look housemates up BY NAME, which worked when
 * Homie only served one fixed 9-person house where every name was unique.
 * Now that any number of independent houses share the same app, two
 * different houses can easily both have a "John" - a name lookup would
 * have shown one house's John a different house's John's profile picture.
 * Every method here now takes the actual User object instead, which also
 * means no database lookup is needed at all: the colour is derived from
 * the user's own id, and the picture comes straight off the object.
 */
@Service
public class AvatarService {

    // A small warm palette matching the house's design reference. Every
    // housemate gets a colour from here, picked deterministically from
    // their user id so the same person always gets the same colour.
    private static final String[] PALETTE = {
            "#6B8E5A", "#C39A4E", "#B5654A", "#6E8A8E", "#8A8B4B",
            "#A8755A", "#8A6A7A", "#5E7D52", "#9C7A4E"
    };

    public String color(User user) {
        if (user == null || user.getId() == null) {
            return PALETTE[0];
        }
        int index = Math.floorMod(user.getId().hashCode(), PALETTE.length);
        return PALETTE[index];
    }

    public String initials(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return "?";
        }
        String trimmed = user.getName().trim();
        return trimmed.length() >= 2 ? trimmed.substring(0, 2) : trimmed.substring(0, 1);
    }

    /**
     * If this housemate has uploaded a real profile picture, returns the
     * URL to show it (e.g. "/profile/picture/7"). Returns null if they
     * haven't set one, in which case the template should fall back to the
     * coloured initials circle instead.
     */
    public String pictureUrl(User user) {
        if (user == null || user.getProfilePicture() == null) {
            return null;
        }
        return "/profile/picture/" + user.getId();
    }
}
