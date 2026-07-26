package com.homie.app.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A single household using Homie. Everything in the app - housemates,
 * rooms, the cleaning rota, the bins schedule, bills, and announcements -
 * belongs to exactly one House. This is what turns Homie from "an app for
 * one specific 9-person house" into "an app any house can create their own
 * account on and use independently of every other house".
 *
 * A House is created by one housemate (the "owner" - see isOwner() on
 * User), who chooses how many rooms the house has and can later rename
 * rooms, add/remove rooms, and reorder the cleaning rota. Everyone else
 * joins that same House using its inviteCode.
 */
@Entity
@Table(name = "houses")
public class House {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The house's display name, e.g. "14 Elm Street" or "The Green House".
    @Column(nullable = false)
    private String name;

    // A short, unique, human-typeable code (e.g. "AB3X9K") that housemates
    // share with each other so new accounts can join the right house.
    // Deliberately NOT the database id - ids are sequential and guessable,
    // which would let anyone join any house just by trying nearby numbers.
    @Column(nullable = false, unique = true, length = 12)
    private String inviteCode;

    // The date this house was created. Used as the anchor date for the
    // cleaning rota and bins schedule calculations (see ScheduleService) -
    // every house's rota starts counting from its own creation date rather
    // than one shared fixed date, so a brand new house starts on week 1
    // instead of wherever a global calendar would place it.
    @Column(nullable = false)
    private LocalDate createdDate;

    // The housemate who created this house. They're the only one allowed
    // to resize/rename rooms and reorder the cleaning rota (see
    // User.isOwnerOf(House) and HouseService).
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "house", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms = new ArrayList<>();

    @OneToMany(mappedBy = "house")
    private List<User> members = new ArrayList<>();

    public House() {
    }

    public House(String name, String inviteCode, LocalDate createdDate, User createdBy) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
    }

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

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }

    public List<User> getMembers() {
        return members;
    }

    public void setMembers(List<User> members) {
        this.members = members;
    }

    // How many people currently live in this house, for stat cards and the
    // "split between N housemates" line on the Bills page.
    @Transient
    public int getMemberCount() {
        return members == null ? 0 : members.size();
    }
}
