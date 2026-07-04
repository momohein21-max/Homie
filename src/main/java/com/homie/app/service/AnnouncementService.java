package com.homie.app.service;

import com.homie.app.dto.AnnouncementCreateDto;
import com.homie.app.entity.Announcement;
import com.homie.app.entity.User;
import com.homie.app.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Holds the business logic for the Announcements board. Any housemate can
 * post a notice; only the housemate who posted it can delete it.
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    // All notices, newest first, for the full Announcements board.
    public List<Announcement> allAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedDateDesc();
    }

    // The most recent few notices, for the dashboard's "Recent
    // announcements" panel.
    public List<Announcement> recentAnnouncements(int howMany) {
        List<Announcement> all = allAnnouncements();
        return all.subList(0, Math.min(howMany, all.size()));
    }

    public Announcement findById(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));
    }

    public void createAnnouncement(AnnouncementCreateDto dto, User author) {
        Announcement announcement = new Announcement(
                dto.getTitle(),
                dto.getCategory(),
                dto.getBody(),
                LocalDate.now(),
                author
        );
        announcementRepository.save(announcement);
    }

    /**
     * Deletes a notice. Only the housemate who posted it is allowed to.
     * Returns an error message if the request is not allowed, or null if
     * it succeeded.
     */
    public String deleteAnnouncement(Long id, String requestingUserEmail) {
        Announcement announcement = findById(id);

        if (!announcement.getCreatedBy().getEmail().equalsIgnoreCase(requestingUserEmail)) {
            return "Only " + announcement.getCreatedBy().getName() + " can delete this notice.";
        }

        announcementRepository.delete(announcement);
        return null;
    }

    /**
     * Deletes every notice posted by a housemate whose account is about to
     * be deleted. Must run before the User row itself is deleted —
     * otherwise the database rejects the deletion, since every
     * announcement is required to point at a real housemate. Called from
     * ProfileController just before UserService.deleteAccount().
     */
    @Transactional
    public void deleteAllForUser(Long userId) {
        List<Announcement> ownAnnouncements = announcementRepository.findAll().stream()
                .filter(a -> a.getCreatedBy() != null && a.getCreatedBy().getId().equals(userId))
                .toList();
        announcementRepository.deleteAll(ownAnnouncements);
    }
}
