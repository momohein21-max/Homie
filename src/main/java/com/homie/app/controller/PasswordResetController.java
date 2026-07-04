package com.homie.app.controller;

import com.homie.app.dto.ForgotPasswordDto;
import com.homie.app.dto.ResetPasswordDto;
import com.homie.app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Handles the "forgot password" self-service flow:
 *   1. GET/POST /forgot-password - housemate types their email
 *   2. An email goes out with a one-time link (see EmailService)
 *   3. GET/POST /reset-password  - clicking that link lets them choose a
 *      new password
 *
 * Deliberately gives the same response on the forgot-password form
 * whether or not the email matched a real account - see
 * UserService.requestPasswordReset() for why.
 */
@Controller
public class PasswordResetController {

    private final UserService userService;

    public PasswordResetController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordDto());
        return "forgot-password"; // renders templates/forgot-password.html
    }

    @PostMapping("/forgot-password")
    public String submitForgotPasswordForm(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordDto dto,
                                            BindingResult result,
                                            Model model) {

        if (result.hasErrors()) {
            return "forgot-password";
        }

        userService.requestPasswordReset(dto.getEmail());

        // Same message every time, regardless of whether that email
        // actually belongs to an account - see the note on
        // UserService.requestPasswordReset().
        model.addAttribute("submitted", true);
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        if (!userService.isResetTokenValid(token)) {
            model.addAttribute("tokenInvalid", true);
            return "reset-password"; // renders templates/reset-password.html
        }

        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setToken(token);
        model.addAttribute("resetPasswordForm", dto);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String submitResetPasswordForm(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordDto dto,
                                           BindingResult result,
                                           Model model) {

        if (result.hasErrors()) {
            return "reset-password";
        }

        String error = userService.resetPassword(dto.getToken(), dto.getNewPassword(), dto.getConfirmPassword());
        if (error != null) {
            model.addAttribute("resetError", error);
            return "reset-password";
        }

        return "redirect:/login?resetSuccess";
    }
}
