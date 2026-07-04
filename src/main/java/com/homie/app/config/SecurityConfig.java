package com.homie.app.config;

import com.homie.app.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central place where we tell Spring Security how login and access work.
 *
 * This replaces the default Spring login page with our own Thymeleaf page,
 * decides which pages are public, and sets BCrypt as the password hasher.
 */
@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    // BCrypt turns passwords into salted hashes and checks them at login.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Defines the security rules for every incoming web request.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Tell Spring to use our service for loading users.
            .userDetailsService(userDetailsService)

            // Decide which URLs are open to everyone and which need login.
            .authorizeHttpRequests(auth -> auth
                // Anyone can reach the login page, the registration page,
                // the forgot/reset password pages, and static files.
                .requestMatchers(
                        "/login", "/register",
                        "/forgot-password", "/reset-password",
                        "/css/**", "/js/**"
                ).permitAll()
                // Every other page requires the user to be logged in.
                .anyRequest().authenticated()
            )

            // Set up our own login page instead of the built-in one.
            .formLogin(form -> form
                .loginPage("/login")            // our custom page
                .defaultSuccessUrl("/", true)   // where to go after login
                .permitAll()
            )

            // Allow users to log out and send them back to the login page.
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
