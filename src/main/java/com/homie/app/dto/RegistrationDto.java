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
 * The annotations below describe the rules. Spring checks them automatically
 * and sends helpful messages back to the form if something is wrong.
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

    // Which room they're moving into. Optional at registration (can also
    // be set later on the profile page) so this has no @NotBlank rule.
    private String room;

    // Getters and setters so Spring can fill this object from the form fields.
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

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}
