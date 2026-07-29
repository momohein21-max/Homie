package com.homie.app.repository;

import com.homie.app.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles all database work for the BillPayment entity (one housemate's
 * share of one bill).
 */
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {
    List<BillPayment> findByPaidFalseAndBill_DueDateIn(List<LocalDate> dates);
}
