package com.homie.app.repository;

import com.homie.app.entity.Bill;
import com.homie.app.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Handles all database work for the Bill entity.
 */
public interface BillRepository extends JpaRepository<Bill, Long> {

    // One house's bills, soonest due date first. Used to list bills on the
    // Bills page with the most urgent ones at the top - scoped to a single
    // house, since every house's bills are entirely separate from every
    // other house's.
    List<Bill> findByHouseOrderByDueDateAsc(House house);
}
