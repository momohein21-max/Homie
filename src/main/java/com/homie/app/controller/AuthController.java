package com.homie.app.controller;

import com.homie.app.dto.RegistrationDto;
import com.homie.app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Connects web addresses (URLs) to pages for login, registration, and home.
 *
 * A @Controller returns the name of a Thymeleaf template to display.
 * GET methods show a page; POST methods handle a submitted form.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Show the login page.
    @GetMapping("/login")
    public String login() {
        return "login"; // renders templates/login.html
    }

    // Show the empty registration form.
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        // An empty DTO is added so the form fields have something to bind to.
        model.addAttribute("user", new RegistrationDto());
        return "register"; // renders templates/register.html
    }

    // Handle the submitted registration form.
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") RegistrationDto dto,
                           BindingResult result,
                           Model model) {

        // If any validation rule failed, show the form again with the errors.
        if (result.hasErrors()) {
            return "register";
        }

        // Try to register. If the email is taken, show a friendly message.
        boolean success = userService.register(dto);
        if (!success) {
            model.addAttribute("emailError", "That email is already registered.");
            return "register";
        }

        // Registration worked: send them to the login page with a success flag.
        return "redirect:/login?registered";
    }
}
