package com.homie.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries the data typed into the "My profile" edit form.
 *
 * The password is optional here (unlike registration): leaving it blank
 * means "keep my current password". Room, duty, and payment details are
 * also optional — not every housemate has set a room or a duty yet.
 *
 * The profile picture itself is NOT part of this DTO. File uploads
 * (MultipartFile) are handled separately in ProfileController, since
 * binding them through th:field in Thymeleaf is unreliable.
 */
public class ProfileUpdateDto {

    @NotBlank(message = "Please enter your name")
    private String name;

    @NotBlank(message = "Please enter your email")
    @Email(message = "Please enter a valid email address")
    private String email;

    // Optional. Left blank = don't change the password.
    private String newPassword;

    // Optional. One of "Room 1".."Room 5" (selected from a dropdown).
    private String room;

    // Optional. E.g. "Wifi router admin" or "Bin day reminders".
    @Size(max = 255, message = "Keep your duty under 255 characters")
    private String duty;

    // Optional. E.g. an IBAN, a Revolut @handle, or a PayPal.me link.
    @Size(max = 500, message = "Keep your payment details under 500 characters")
    private String paymentDetails;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getDuty() {
        return duty;
    }

    public void setDuty(String duty) {
        this.duty = duty;
    }

    public String getPaymentDetails() {
        return paymentDetails;
    }

    public void setPaymentDetails(String paymentDetails) {
        this.paymentDetails = paymentDetails;
    }
}
