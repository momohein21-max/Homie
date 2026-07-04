package com.homie.app.repository;

import com.homie.app.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Handles all database work for the Bill entity.
 */
public interface BillRepository extends JpaRepository<Bill, Long> {

    // All bills, soonest due date first. Used to list bills on the
    // Bills page with the most urgent ones at the top.
    List<Bill> findAllByOrderByDueDateAsc();
}
