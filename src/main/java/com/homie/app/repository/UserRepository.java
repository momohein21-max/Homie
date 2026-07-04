package com.homie.app.repository;

import com.homie.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Handles all database work for the User entity.
 *
 * By extending JpaRepository we automatically get methods like save(),
 * findById(), findAll(), and delete() without writing any SQL ourselves.
 *
 * We only add the extra finder methods that our features actually need.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data builds the query from the method name.
    // This looks up a single user by their email (used during login).
    // Optional means the result may be empty if no user matches.
    //
    // IgnoreCase matters here: MySQL's default collation compared text
    // case-insensitively, so "Momo@Gmail.com" and "momo@gmail.com" were
    // treated as the same email. PostgreSQL compares text case-sensitively
    // by default, so without IgnoreCase here, a login typed with slightly
    // different capitalisation than how the email was originally stored
    // would silently fail to match after the move to Postgres.
    Optional<User> findByEmailIgnoreCase(String email);

    // Used during registration to check whether an email is already taken.
    // Same IgnoreCase reasoning as above - otherwise "momo@gmail.com" and
    // "Momo@Gmail.com" could both be registered as separate accounts.
    boolean existsByEmailIgnoreCase(String email);

    // Looks a housemate up by their first name (case-insensitive). Used by
    // AvatarService to find a housemate's real profile picture wherever an
    // avatar is shown by name alone (e.g. the cleaning rota), rather than
    // by a full User object.
    Optional<User> findByNameIgnoreCase(String name);

    // Looks a user up by their active password-reset token. Used when
    // someone clicks the reset link emailed to them.
    Optional<User> findByResetToken(String resetToken);
}
