package com.homie.app.service;

import com.homie.app.dto.BillCreateDto;
import com.homie.app.entity.Bill;
import com.homie.app.entity.BillPayment;
import com.homie.app.entity.House;
import com.homie.app.entity.User;
import com.homie.app.repository.BillPaymentRepository;
import com.homie.app.repository.BillRepository;
import com.homie.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Holds the business logic for shared bills.
 *
 * A bill is always split equally between every current housemate. When a
 * bill is added, one BillPayment row is created per housemate so each
 * person's share can be tracked and ticked off individually. Only the
 * housemate who added the bill is allowed to mark shares as paid, since
 * they are the one who actually paid the company/landlord and is
 * collecting the money back from everyone else.
 */
@Service
public class BillService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final UserRepository userRepository;

    public BillService(BillRepository billRepository,
                        BillPaymentRepository billPaymentRepository,
                        UserRepository userRepository) {
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns one house's bills, soonest due date first, for that house's
     * Bills page. Every house's bills are completely separate from every
     * other house's.
     */
    public List<Bill> allBills(House house) {
        return billRepository.findByHouseOrderByDueDateAsc(house);
    }

    /**
     * Creates a new bill from the "Add a bill" form and splits it equally
     * across every housemate currently in the creator's own house.
     */
    public void createBill(BillCreateDto dto, User creator) {
        House house = creator.getHouse();
        Bill bill = new Bill(
                dto.getDescription(),
                dto.getCategory(),
                dto.getTotalAmount(),
                dto.getDueDate(),
                LocalDate.now(),
                creator,
                house
        );
        billRepository.save(bill);

        // Give every housemate in this house a share of this bill to pay
        // back, including the person who created it (they paid the
        // company, but still owe themselves a share of the total
        // conceptually, matching a simple "split evenly" rule).
        List<User> housemates = userRepository.findByHouse(house);
        for (User housemate : housemates) {
            BillPayment payment = new BillPayment(bill, housemate);
            billPaymentRepository.save(payment);
        }
    }

    /**
     * The total still owed across every bill in this house — the sum of
     * every housemate's unpaid shares. Shown as "Outstanding" on the
     * Bills page.
     */
    public double outstandingTotal(House house) {
        return allBills(house).stream()
                .mapToDouble(Bill::getUnpaidShareTotal)
                .sum();
    }

    /**
     * How much the given housemate personally still owes, across every
     * bill in their house they have an unpaid share in. Shown as
     * "Your share due".
     */
    public double yourShareDue(House house, Long userId) {
        return allBills(house).stream()
                .map(bill -> bill.paymentFor(userId))
                .filter(payment -> payment != null && !payment.isPaid())
                .mapToDouble(payment -> payment.getBill().getShareAmount())
                .sum();
    }

    /**
     * How many housemates this house's bills are currently split between.
     * Shown as "Split between" on the Bills page.
     */
    public long housemateCount(House house) {
        return userRepository.countByHouse(house);
    }

    /**
     * Updates an existing bill's details (description, category, amount,
     * due date). Only the housemate who originally added the bill is
     * allowed to do this, e.g. if an estimated bill turns out to be a
     * different amount, or the due date changes.
     *
     * Returns an error message if the request is not allowed or the input
     * is invalid, or null if it succeeded.
     */
    public String updateBill(Long billId, String description, String category,
                              Double totalAmount, LocalDate dueDate, String requestingUserEmail) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (!bill.getCreatedBy().getEmail().equalsIgnoreCase(requestingUserEmail)) {
            return "Only " + bill.getCreatedBy().getName() + " can update this bill.";
        }
        if (description == null || description.isBlank()) {
            return "Please enter a description.";
        }
        if (category == null || category.isBlank()) {
            return "Please choose a category.";
        }
        if (totalAmount == null || totalAmount <= 0) {
            return "Amount must be greater than 0.";
        }
        if (dueDate == null) {
            return "Please choose a due date.";
        }

        bill.setDescription(description);
        bill.setCategory(category);
        bill.setTotalAmount(totalAmount);
        bill.setDueDate(dueDate);
        billRepository.save(bill);
        return null;
    }

    /**
     * Flips one housemate's paid/unpaid status for a bill.
     *
     * Only the housemate who created the bill is allowed to do this.
     * Returns an error message if the request is not allowed, or null if
     * it succeeded.
     */
    public String togglePaid(Long paymentId, String requestingUserEmail) {
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Bill payment not found: " + paymentId));

        User creator = payment.getBill().getCreatedBy();
        if (!creator.getEmail().equalsIgnoreCase(requestingUserEmail)) {
            return "Only " + creator.getName() + " can update payments for this bill.";
        }

        boolean nowPaid = !payment.isPaid();
        payment.setPaid(nowPaid);
        payment.setPaidDate(nowPaid ? LocalDate.now() : null);
        billPaymentRepository.save(payment);
        return null;
    }

    /**
     * Deletes a bill entirely (and its BillPayment rows, via cascade).
     * Only the housemate who created the bill is allowed to do this.
     */
    public String deleteBill(Long billId, String requestingUserEmail) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (!bill.getCreatedBy().getEmail().equalsIgnoreCase(requestingUserEmail)) {
            return "Only " + bill.getCreatedBy().getName() + " can delete this bill.";
        }

        billRepository.delete(bill);
        return null;
    }

    /**
     * Removes everything bill-related tied to a housemate whose account is
     * about to be deleted: their own share of every OTHER housemate's
     * bill, plus every bill they created themselves (which cascades to
     * remove that bill's payment rows too).
     *
     * Scoped to the departing housemate's own house — with any number of
     * independent houses now sharing Homie, there is no reason for this to
     * touch (or even load) another house's bills at all.
     *
     * This must run before the User row itself is deleted — otherwise the
     * database rejects the deletion outright, since bills and bill
     * payments are required to point at a real housemate. Called from
     * ProfileController just before UserService.deleteAccount().
     */
    @Transactional
    public void deleteAllForUser(Long userId, House house) {
        List<Bill> houseBills = billRepository.findByHouse(house);

        // Remove their own share from every bill they didn't create themselves.
        for (Bill bill : houseBills) {
            boolean isOwnBill = bill.getCreatedBy() != null && bill.getCreatedBy().getId().equals(userId);
            if (!isOwnBill) {
                bill.getPayments().removeIf(payment ->
                        payment.getUser() != null && payment.getUser().getId().equals(userId));
            }
        }
        billRepository.saveAll(houseBills);

        // Delete every bill they created themselves — cascades to remove
        // that bill's payment rows too (Bill.payments has cascade=ALL).
        List<Bill> ownBills = houseBills.stream()
                .filter(bill -> bill.getCreatedBy() != null && bill.getCreatedBy().getId().equals(userId))
                .toList();
        billRepository.deleteAll(ownBills);
    }
}
