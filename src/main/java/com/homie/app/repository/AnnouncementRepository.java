package com.homie.app.repository;

import com.homie.app.entity.Announcement;
import com.homie.app.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Handles all database work for the Announcement entity.
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // One house's notices, newest first, for that house's Announcements
    // board and dashboard strip.
    List<Announcement> findByHouseOrderByCreatedDateDesc(House house);
}
