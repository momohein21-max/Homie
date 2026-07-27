package com.homie.app.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Represents a person who can log into Homie (one of the housemates).
 *
 * Sprint 1 only held what registration and login needed: id, name, email,
 * password, role. Sprint 2 adds the "My profile" fields: an optional duty
 * (a responsibility the housemate has taken on, separate from the cleaning
 * rota), a room, payment details so others know how to pay them back for
 * bills, and a profile picture. This later revision adds a "forgot
 * password" reset token/expiry pair, and drops the MySQL-only LONGBLOB
 * type now that the app runs on PostgreSQL - @Lob alone lets Hibernate
 * pick the correct binary column type for whichever database is in use.
 */
@Entity
@Table(name = "users") // "user" is a reserved word in MySQL, so we use "users"
public class User {

    // Primary key. The database generates the value automatically.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The housemate's display name, e.g. "Momo". Cannot be empty.
    @Column(nullable = false)
    private String name;

    // Email is used as the login username, so it must be unique.
    @Column(nullable = false, unique = true)
    private String email;

    // The password is never stored in plain text. We store a BCrypt hash.
    @Column(nullable = false)
    private String password;

    // A simple role string such as "ROLE_USER" or "ROLE_ADMIN".
    // Spring Security uses this to decide what a user is allowed to do.
    @Column(nullable = false)
    private String role;

    // Which House this housemate belongs to. Every real account has one -
    // registration always either starts a new house or joins one with an
    // invite code (see RegistrationDto/HouseService) - so there's no such
    // thing as a housemate without a house in practice. The column itself
    // is nullable only because of a brief chicken-and-egg moment when a
    // brand new house is created: House.createdBy needs a real user id to
    // point at, so that first user is saved once with house = null and
    // then immediately updated once the house exists (see
    // HouseService.createHouse). Everything else in the app (rooms, bills,
    // announcements, the cleaning rota) is scoped through this
    // relationship, which is what lets any number of independent houses
    // use Homie at once without seeing each other's data.
    @ManyToOne
    @JoinColumn(name = "house_id")
    private House house;

    // Which room within their house this housemate lives in. Optional and
    // self-selected on the profile page (some rooms are shared by more
    // than one housemate, so this is not a unique constraint). A real
    // relationship to a Room row now, rather than a free-text "Room 4"
    // string, since houses have different, changeable sets of rooms.
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    // This housemate's position in their house's cleaning rota (0 = first).
    // Assigned automatically when they join (added to the end), and
    // reorderable afterwards by the house owner - see HouseService. Kept
    // as a plain integer per house rather than a single global order, since
    // every house has its own independent rota.
    private Integer cleaningOrder;

    // An optional house responsibility the housemate has taken on, e.g.
    // "Wifi router admin" or "Bin day reminders". This is separate from
    // the weekly cleaning rota, which is calculated by ScheduleService.
    private String duty;

    // Optional free-text payment info (e.g. an IBAN, a Revolut @handle, or
    // a PayPal.me link) so housemates know how to pay this person back for
    // bills. Deliberately kept as free text rather than structured card
    // fields, so the app never needs to hold a full card number.
    @Column(length = 500)
    private String paymentDetails;

    // The profile picture itself, stored directly in the database as a
    // BLOB. This is fine at this house's scale (9 users); a bigger app
    // would use external file/cloud storage instead. No columnDefinition
    // is specified so Hibernate picks whichever binary type matches the
    // database actually in use (e.g. bytea on PostgreSQL) instead of
    // hardcoding a MySQL-only type name.

    @JdbcTypeCode(SqlTypes.VARBINARY)
    private byte[] profilePicture;

    // The picture's MIME type (e.g. "image/png" or "image/jpeg"), so we
    // know what Content-Type header to send back when serving it.
    private String profilePictureType;

    // A one-time code emailed to a housemate who requests a password
    // reset. Null when nobody has an active reset in progress. Cleared
    // again as soon as the password is actually changed, so it can never
    // be reused.
    private String resetToken;

    // When the reset token above stops being valid. Kept short (see
    // UserService) so an old, unused reset link can't be used much later.
    private LocalDateTime resetTokenExpiry;

    // JPA requires a no-argument constructor.
    public User() {
    }

    public User(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    // Getters and setters let other classes read and update the fields.
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public House getHouse() {
        return house;
    }

    public void setHouse(House house) {
        this.house = house;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Integer getCleaningOrder() {
        return cleaningOrder;
    }

    public void setCleaningOrder(Integer cleaningOrder) {
        this.cleaningOrder = cleaningOrder;
    }

    // True if this housemate is the one who created their house, meaning
    // they're allowed to resize/rename rooms and reorder the cleaning rota
    // (see HouseController and HouseService). Every house has exactly one
    // owner - whoever created it.
    @Transient
    public boolean isHouseOwner() {
        return house != null && house.getCreatedBy() != null
                && house.getCreatedBy().getId() != null
                && house.getCreatedBy().getId().equals(this.id);
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

    public byte[] getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(byte[] profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getProfilePictureType() {
        return profilePictureType;
    }

    public void setProfilePictureType(String profilePictureType) {
        this.profilePictureType = profilePictureType;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }
}
