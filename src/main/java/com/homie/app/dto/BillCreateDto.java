package com.homie.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Carries the data typed into the "Add a bill" form.
 *
 * A separate DTO (rather than binding straight to the Bill entity) keeps
 * the form away from JPA-managed fields like the payments list and the
 * createdBy user, which are set by BillService instead.
 */
public class BillCreateDto {

    @NotBlank(message = "Please enter a short description")
    private String description;

    @NotBlank(message = "Please choose a category")
    private String category;

    @NotNull(message = "Please enter an amount")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private Double totalAmount;

    @NotNull(message = "Please choose a due date")
    private LocalDate dueDate;

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

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
