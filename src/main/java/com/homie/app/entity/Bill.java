package com.homie.app.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one shared household bill (e.g. electricity, wifi, gas).
 *
 * A bill always has one total amount and is split equally among every
 * housemate. The per-person breakdown (who owes their share and who has
 * paid it back) is tracked separately in BillPayment rows, one per
 * housemate, created automatically when the bill is added.
 *
 * The person who adds the bill (createdBy) is the one who is treated as
 * having paid the company/landlord up front, and is collecting each
 * housemate's share back from them. That is why only they are allowed to
 * tick off who has paid — see BillService.
 */
@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Short description of the bill, e.g. "June electricity".
    @Column(nullable = false)
    private String description;

    // Category, e.g. "Electricity", "Wifi", "Gas", "Groceries", "Other".
    // Kept as a free String (not an enum) to match how Room/Duty are stored
    // on User, and to keep the category list easy to change later.
    @Column(nullable = false)
    private String category;

    // The total amount of the bill, before it is split.
    @Column(nullable = false)
    private double totalAmount;

    // When the bill is due to be paid.
    @Column(nullable = false)
    private LocalDate dueDate;

    // The date the bill was added to Homie.
    @Column(nullable = false)
    private LocalDate createdDate;

    // The housemate who added the bill and is collecting the shares back.
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    // One BillPayment per housemate, tracking their share and paid status.
    // orphanRemoval + cascade ALL means deleting a Bill cleans up its
    // BillPayment rows automatically.
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillPayment> payments = new ArrayList<>();

    public Bill() {
    }

    public Bill(String description, String category, double totalAmount,
                LocalDate dueDate, LocalDate createdDate, User createdBy) {
        this.description = description;
        this.category = category;
        this.totalAmount = totalAmount;
        this.dueDate = dueDate;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public List<BillPayment> getPayments() {
        return payments;
    }

    public void setPayments(List<BillPayment> payments) {
        this.payments = payments;
    }

    // The amount each housemate owes: the total split evenly across
    // however many BillPayment rows exist for this bill (one per
    // housemate at the time the bill was created).
    @Transient
    public double getShareAmount() {
        if (payments == null || payments.isEmpty()) {
            return totalAmount;
        }
        return totalAmount / payments.size();
    }

    // How many housemates have paid their share so far, e.g. for a
    // "6 of 9 paid" summary on the bill card.
    @Transient
    public long getPaidCount() {
        return payments.stream().filter(BillPayment::isPaid).count();
    }

    @Transient
    public boolean isFullyPaid() {
        return !payments.isEmpty() && getPaidCount() == payments.size();
    }

    // The total still owed on this bill (unpaid shares only), used to
    // roll up the house-wide "Outstanding" figure on the Bills page.
    @Transient
    public double getUnpaidShareTotal() {
        long unpaidCount = payments.size() - getPaidCount();
        return unpaidCount * getShareAmount();
    }

    // "Paid" once everyone has paid their share, "Overdue" if the due
    // date has passed and it's still not fully paid, otherwise "Pending".
    @Transient
    public String getStatus() {
        if (isFullyPaid()) {
            return "Paid";
        }
        if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            return "Overdue";
        }
        return "Pending";
    }

    // One letter shown on the bill's icon, taken from the category
    // (e.g. "E" for Electricity, "W" for Wifi).
    @Transient
    public String getInitial() {
        if (category == null || category.isEmpty()) {
            return "?";
        }
        return String.valueOf(Character.toUpperCase(category.charAt(0)));
    }

    // Cycles bills through a small set of icon colours (see bill-icon-0/1/2
    // in bills.html) based on the category name, purely for visual variety.
    @Transient
    public String getIconClass() {
        int index = Math.floorMod(category == null ? 0 : category.hashCode(), 3);
        return "bill-icon-" + index;
    }

    // Finds the given housemate's own share/payment row for this bill, or
    // null if they somehow don't have one (e.g. they joined after the bill
    // was created). Used to work out "your share due" on the Bills page.
    public BillPayment paymentFor(Long userId) {
        if (payments == null || userId == null) {
            return null;
        }
        return payments.stream()
                .filter(p -> p.getUser() != null && userId.equals(p.getUser().getId()))
                .findFirst()
                .orElse(null);
    }
}
