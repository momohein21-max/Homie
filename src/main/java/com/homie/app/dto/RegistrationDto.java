package com.homie.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries the data typed into the registration form.
 *
 * We use a separate DTO (Data Transfer Object) instead of the User entity
 * directly so the web form never touches the database object straight away.
 * This keeps the password-plain-text value away from the entity and lets us
 * validate the input cleanly before creating a real User.
 *
 * Since Homie now supports any number of independent houses (not just one
 * fixed 9-person house), registration also asks which of two paths the
 * person is taking: starting a brand new house, or joining an existing one
 * with its invite code. houseName/roomCount only matter when action is
 * "CREATE"; inviteCode only matters when action is "JOIN". These aren't
 * marked @NotBlank here because which ones are required depends on which
 * action was chosen - UserService.register() checks that conditionally
 * instead, the same way BillService/AnnouncementService validate things
 * that plain field annotations can't express.
 */
public class RegistrationDto {

    @NotBlank(message = "Please enter your name")
    private String name;

    @NotBlank(message = "Please enter your email")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Please enter a password")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    // "CREATE" (start a new house) or "JOIN" (join one with a code).
    // Defaults to CREATE so the form's create panel is the one shown first.
    private String action = "CREATE";

    // Only used when action = CREATE.
    private String houseName;
    private Integer roomCount;

    // Only used when action = JOIN.
    private String inviteCode;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getHouseName() {
        return houseName;
    }

    public void setHouseName(String houseName) {
        this.houseName = houseName;
    }

    public Integer getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(Integer roomCount) {
        this.roomCount = roomCount;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
