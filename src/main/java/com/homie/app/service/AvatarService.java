package com.homie.app.service;

import com.homie.app.entity.User;
import com.homie.app.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Gives every housemate a consistent avatar colour and initials, used
 * anywhere a small circular avatar is shown (sidebar, cleaning rota, bins
 * page, housemates grid, bills, announcements, etc.) — and, if that
 * housemate has uploaded a real profile picture, the URL to show that
 * instead of the coloured initials.
 *
 * Registered as a normal Spring bean so Thymeleaf templates can call it
 * directly as "@avatarService.color(name)" / "@avatarService.initials(name)"
 * / "@avatarService.pictureUrl(name)" without every controller having to
 * pass avatar data through the model.
 *
 * The 9 fixed housemates get hand-picked colours matching the house's
 * design reference. Anyone else (e.g. a housemate added later, or a typo
 * in a name) still gets a stable colour, just picked deterministically
 * from the same palette instead of a hand-picked one.
 */
@Service
public class AvatarService {

    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            Map.entry("Julia", "#6B8E5A"),
            Map.entry("Edgar", "#C39A4E"),
            Map.entry("Momo", "#B5654A"),
            Map.entry("Sheron", "#6E8A8E"),
            Map.entry("Luis", "#8A8B4B"),
            Map.entry("Lívia", "#A8755A"),
            Map.entry("Ágatha", "#8A6A7A"),
            Map.entry("Allyne", "#5E7D52"),
            Map.entry("Edecilmar", "#9C7A4E")
    );

    // Same palette, used as a fallback so any other name still gets one of
    // these colours rather than a random one.
    private static final String[] FALLBACK_PALETTE = {
            "#6B8E5A", "#C39A4E", "#B5654A", "#6E8A8E", "#8A8B4B",
            "#A8755A", "#8A6A7A", "#5E7D52", "#9C7A4E"
    };

    private final UserRepository userRepository;

    public AvatarService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String color(String name) {
        if (name == null || name.isBlank()) {
            return FALLBACK_PALETTE[0];
        }
        String trimmed = name.trim();
        String known = NAMED_COLORS.get(trimmed);
        if (known != null) {
            return known;
        }
        int index = Math.floorMod(trimmed.hashCode(), FALLBACK_PALETTE.length);
        return FALLBACK_PALETTE[index];
    }

    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String trimmed = name.trim();
        return trimmed.length() >= 2 ? trimmed.substring(0, 2) : trimmed.substring(0, 1);
    }

    /**
     * If the housemate with this name has uploaded a real profile
     * picture, returns the URL to show it (e.g. "/profile/picture/7").
     * Returns null if they haven't set one, or no housemate matches the
     * name — in which case the template should fall back to the coloured
     * initials circle instead.
     *
     * Name-based (not id-based) so this works everywhere an avatar is
     * already shown by name alone, like the cleaning rota, without having
     * to thread a full User object through every page.
     */
    public String pictureUrl(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<User> user = userRepository.findByNameIgnoreCase(name.trim());
        if (user.isEmpty() || user.get().getProfilePicture() == null) {
            return null;
        }
        return "/profile/picture/" + user.get().getId();
    }
}
