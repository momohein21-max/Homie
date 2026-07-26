package com.homie.app.repository;

import com.homie.app.entity.House;
import com.homie.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    // Looks a user up by their active password-reset token. Used when
    // someone clicks the reset link emailed to them.
    Optional<User> findByResetToken(String resetToken);

    // Every housemate in one house, in cleaning-rota order. This is the
    // multi-house replacement for the old "just get everyone" queries -
    // every page that used to show "the house" now shows "this house".
    List<User> findByHouseOrderByCleaningOrderAsc(House house);

    List<User> findByHouse(House house);

    long countByHouse(House house);

    // Plain bulk SQL, used only when deleting a house's last remaining
    // member (its owner) - see UserService.deleteAccount. Breaks this
    // user's link to their house/room BEFORE the house is deleted, so the
    // database never sees a users row pointing at a house that no longer
    // exists. clearAutomatically wipes any stale User/House objects still
    // sitting in Hibernate's persistence context afterwards, so nothing
    // later in the same transaction can accidentally write their old
    // in-memory values back over this update.
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE users SET house_id = NULL, room_id = NULL WHERE id = :userId", nativeQuery = true)
    void detachFromHouseAndRoomNative(@Param("userId") Long userId);

    // Plain bulk SQL delete for the user row itself. Deliberately used
    // instead of the normal entity delete() for every account deletion
    // (not just house owners) - going through Hibernate's usual
    // entity-by-entity deletion here trips over its association
    // bookkeeping for the bidirectional House<->User relationship (it
    // tries to validate or null out required references on entities still
    // sitting in the persistence context), which the database then
    // rejects since House.createdBy is required. A plain SQL DELETE has
    // none of that bookkeeping to trip over.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM users WHERE id = :id", nativeQuery = true)
    void deleteByIdNative(@Param("id") Long id);
}
