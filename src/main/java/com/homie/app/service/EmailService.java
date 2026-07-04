package com.homie.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the small handful of emails Homie needs to send - right now,
 * just the "reset your password" link.
 *
 * Kept as its own class (rather than folding this into UserService) so
 * the "how do we send an email" concern stays separate from the "what is
 * a valid password reset" business logic.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // The address Homie's emails appear to come "from". Set in
    // application.properties (spring.mail.username) - most SMTP
    // providers, including Gmail, require this to match the account
    // that's actually authenticating to send the email.
    @Value("${spring.mail.username}")
    private String fromAddress;

    // The base URL to build links with, e.g. "http://localhost:8080" when
    // running locally, or your real onrender.com address once deployed.
    // Configurable via application.properties (app.base-url) so the same
    // code works in both places.
    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a housemate the link they need to choose a new password.
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = baseUrl + "/reset-password?token=" + resetToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your Homie password");
        message.setText(
                "Hi,\n\n" +
                "Someone (hopefully you!) asked to reset the password for your Homie account.\n\n" +
                "Click this link to choose a new password. It expires in 30 minutes:\n" +
                resetLink + "\n\n" +
                "If you didn't ask for this, you can safely ignore this email - your password " +
                "won't change unless you click the link above and set a new one.\n\n" +
                "- Homie"
        );

        mailSender.send(message);
    }
}
