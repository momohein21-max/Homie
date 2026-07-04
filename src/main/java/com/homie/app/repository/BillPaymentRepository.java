package com.homie.app.repository;

import com.homie.app.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Handles all database work for the BillPayment entity (one housemate's
 * share of one bill).
 */
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {
}
