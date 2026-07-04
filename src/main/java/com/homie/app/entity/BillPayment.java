package com.homie.app.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Represents one housemate's share of one Bill.
 *
 * When a Bill is created, BillService creates one of these for every
 * current housemate, so the bill page can show, side by side, who still
 * owes their share and who has already paid it back to whoever raised
 * the bill.
 */
@Entity
@Table(name = "bill_payments")
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The bill this share belongs to.
    @ManyToOne(optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    // The housemate who owes this share.
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Whether this housemate has paid their share back yet.
    @Column(nullable = false)
    private boolean paid = false;

    // The date they were marked as paid. Null until then.
    private LocalDate paidDate;

    public BillPayment() {
    }

    public BillPayment(Bill bill, User user) {
        this.bill = bill;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Bill getBill() {
        return bill;
    }

    public void setBill(Bill bill) {
        this.bill = bill;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }
}
