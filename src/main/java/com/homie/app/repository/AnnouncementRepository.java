package com.homie.app.repository;

import com.homie.app.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Handles all database work for the Announcement entity.
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // Newest first, for the Announcements board and the dashboard strip.
    List<Announcement> findAllByOrderByCreatedDateDesc();
}
