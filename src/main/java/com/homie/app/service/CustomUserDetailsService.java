package com.homie.app.service;

import com.homie.app.entity.User;
import com.homie.app.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tells Spring Security how to find a user when someone tries to log in.
 *
 * Spring Security does not know about our database. When a login happens it
 * calls loadUserByUsername(...) and expects us to return the matching user's
 * details (email, hashed password, and role). Spring then checks the typed
 * password against the stored hash for us.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // Spring passes in the repository automatically (constructor injection).
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Look the user up by email, ignoring case - PostgreSQL compares
        // text case-sensitively by default, unlike MySQL, so this avoids
        // login silently failing over a capitalisation mismatch.
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("No user found with email: " + email));

        // Hand Spring Security a built-in user object that holds the email,
        // the hashed password, and the role it should grant.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole())))
                .build();
    }
}
