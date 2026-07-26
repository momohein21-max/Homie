package com.homie.app.service;

import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.HouseRepository;
import com.homie.app.repository.RoomRepository;
import com.homie.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds the business logic for houses themselves: creating a brand new
 * house (with its rooms and a shareable invite code), joining an existing
 * one, and the handful of things only a house's owner can do afterwards
 * (rename the house, add/rename/remove rooms, reorder the cleaning rota).
 *
 * This is what turns Homie from one hardcoded house into an app any number
 * of independent houses can each have their own account on - every other
 * service (bills, announcements, the schedule) works within whatever House
 * it's given by this one.
 */
@Service
public class HouseService {

    // Room count sanity bounds for the "create a house" form.
    public static final int MIN_ROOMS = 1;
    public static final int MAX_ROOMS = 20;

    // Characters used for invite codes. Deliberately excludes 0/O and 1/I,
    // which are easy to mix up when a housemate reads the code aloud or
    // types it from a photo of a sticky note on the fridge.
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final HouseRepository houseRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public HouseService(HouseRepository houseRepository, RoomRepository roomRepository,
                         UserRepository userRepository) {
        this.houseRepository = houseRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a brand new house with the given name and number of rooms
     * (named "Room 1", "Room 2", ... - renameable afterwards), and makes
     * the given not-yet-saved user its first member and owner.
     *
     * Saves the user in two steps: once before the house exists (so the
     * house has a real user id to point its createdBy at) and once after
     * (to attach the now-existing house back onto the user). This dance is
     * only needed for a house's very first member - House.createdBy and
     * User.house point at each other, so *something* has to be created
     * without its counterpart existing yet.
     */
    @Transactional
    public House createHouse(String houseName, int roomCount, User newUser) {
        User savedUser = userRepository.save(newUser);

        House house = new House(houseName.trim(), generateUniqueInviteCode(), LocalDate.now(), savedUser);
        house = houseRepository.save(house);

        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            rooms.add(new Room(house, "Room " + (i + 1), i));
        }
        roomRepository.saveAll(rooms);

        savedUser.setHouse(house);
        savedUser.setCleaningOrder(0); // the founder starts the cleaning rota
        userRepository.save(savedUser);

        return house;
    }

    /**
     * Adds the given not-yet-saved user to whichever house owns this
     * invite code, at the end of the cleaning rota. Returns an error
     * message if the code doesn't match any house, or null on success.
     */
    @Transactional
    public String joinHouse(String inviteCode, User newUser) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return "Please enter your house's invite code.";
        }
        Optional<House> maybeHouse = houseRepository.findByInviteCodeIgnoreCase(inviteCode.trim());
        if (maybeHouse.isEmpty()) {
            return "That invite code doesn't match any house. Double-check it with your housemates.";
        }

        House house = maybeHouse.get();
        long existingMembers = userRepository.countByHouse(house);

        newUser.setHouse(house);
        newUser.setCleaningOrder((int) existingMembers); // joins at the end of the rota
        userRepository.save(newUser);
        return null;
    }

    // The rooms belonging to a house, in bins-rota order.
    public List<Room> roomsFor(House house) {
        return roomRepository.findByHouseOrderByOrderIndexAsc(house);
    }

    // Every current member of a house, in cleaning-rota order.
    public List<User> membersFor(House house) {
        return userRepository.findByHouseOrderByCleaningOrderAsc(house);
    }

    // Permanently removes a house and its rooms. Only called from
    // UserService.deleteAccount(), and only once it has already
    // confirmed the house's owner is its last remaining member - an
    // owner can't be deleted while other housemates still depend on the
    // house existing, so by the time this runs there's genuinely nobody
    // left for it to belong to. Package-private since no controller
    // should ever be able to trigger this directly.
    //
    // Deliberately uses the native bulk-delete repository methods rather
    // than the normal entity delete() - going through Hibernate's usual
    // entity-by-entity deletion here trips over a quirk where it tries to
    // null out House.createdBy (required, non-nullable) before removing
    // the row, because the User it points at was just deleted in the
    // same transaction. A plain SQL DELETE has no such association
    // bookkeeping to trip over.
    @Transactional
    void deleteHouse(House house) {
        roomRepository.deleteAllByHouseIdNative(house.getId());
        houseRepository.deleteByIdNative(house.getId());
    }

    /**
     * Renames a house. Only the owner may do this.
     */
    public String renameHouse(House house, String newName, String requestingEmail) {
        String permissionError = requireOwner(house, requestingEmail);
        if (permissionError != null) return permissionError;

        if (newName == null || newName.isBlank()) {
            return "Please enter a house name.";
        }
        house.setName(newName.trim());
        houseRepository.save(house);
        return null;
    }

    /**
     * Adds one more room to the end of the house's room list. Only the
     * owner may do this.
     */
    public String addRoom(House house, String requestingEmail) {
        String permissionError = requireOwner(house, requestingEmail);
        if (permissionError != null) return permissionError;

        List<Room> existing = roomsFor(house);
        if (existing.size() >= MAX_ROOMS) {
            return "A house can have at most " + MAX_ROOMS + " rooms.";
        }
        Room room = new Room(house, "Room " + (existing.size() + 1), existing.size());
        roomRepository.save(room);
        return null;
    }

    /**
     * Renames one room. Only the owner of that room's house may do this.
     */
    public String renameRoom(Room room, String newName, String requestingEmail) {
        String permissionError = requireOwner(room.getHouse(), requestingEmail);
        if (permissionError != null) return permissionError;

        if (newName == null || newName.isBlank()) {
            return "Please enter a room name.";
        }
        room.setName(newName.trim());
        roomRepository.save(room);
        return null;
    }

    /**
     * Removes a room. Anyone currently living in it is unassigned (their
     * room becomes "Not set") rather than being blocked from anything -
     * losing a room shouldn't lock a housemate out of their account. Only
     * the owner of the room's house may do this. Refuses to remove the
     * house's very last room, since every house needs at least one.
     */
    @Transactional
    public String removeRoom(Room room, String requestingEmail) {
        House house = room.getHouse();
        String permissionError = requireOwner(house, requestingEmail);
        if (permissionError != null) return permissionError;

        if (roomRepository.countByHouse(house) <= MIN_ROOMS) {
            return "A house needs at least one room.";
        }

        List<User> occupants = userRepository.findByHouse(house).stream()
                .filter(u -> u.getRoom() != null && u.getRoom().getId().equals(room.getId()))
                .toList();
        for (User occupant : occupants) {
            occupant.setRoom(null);
        }
        userRepository.saveAll(occupants);

        roomRepository.delete(room);
        return null;
    }

    /**
     * Moves one member one place up or down the cleaning rota, swapping
     * places with whoever's currently next to them. Only the owner may do
     * this. The simplest possible reorder control - no drag-and-drop
     * library required, just an up/down arrow per row.
     */
    public String moveMember(House house, Long userId, boolean moveUp, String requestingEmail) {
        String permissionError = requireOwner(house, requestingEmail);
        if (permissionError != null) return permissionError;

        List<User> members = membersFor(house);
        int index = -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId().equals(userId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return "That housemate isn't in this house.";
        }

        int swapWith = moveUp ? index - 1 : index + 1;
        if (swapWith < 0 || swapWith >= members.size()) {
            return null; // already first/last - nothing to do, not an error
        }

        User a = members.get(index);
        User b = members.get(swapWith);
        Integer aOrder = a.getCleaningOrder();
        a.setCleaningOrder(b.getCleaningOrder());
        b.setCleaningOrder(aOrder);
        userRepository.save(a);
        userRepository.save(b);
        return null;
    }

    /**
     * Sets a new cleaning-rota order for a house, given the member ids in
     * their new order (index 0 goes first). Only the owner may do this.
     * Ignores any id that doesn't actually belong to this house, so a
     * stale form submission can't reorder someone else's housemates.
     * (An alternative to moveMember() above, kept for a possible future
     * drag-and-drop reorder UI.)
     */
    public String reorderCleaning(House house, List<Long> orderedUserIds, String requestingEmail) {
        String permissionError = requireOwner(house, requestingEmail);
        if (permissionError != null) return permissionError;

        List<User> members = membersFor(house);
        List<User> updated = new ArrayList<>();
        int position = 0;
        for (Long userId : orderedUserIds) {
            for (User member : members) {
                if (member.getId().equals(userId)) {
                    member.setCleaningOrder(position++);
                    updated.add(member);
                    break;
                }
            }
        }
        userRepository.saveAll(updated);
        return null;
    }

    // Shared permission check: is this email the owner (creator) of this house?
    private String requireOwner(House house, String requestingEmail) {
        if (house.getCreatedBy() == null || !house.getCreatedBy().getEmail().equalsIgnoreCase(requestingEmail)) {
            return "Only the housemate who created " + house.getName() + " can manage its rooms and rota.";
        }
        return null;
    }

    // Keeps generating random 6-character codes until it finds one that
    // isn't already in use by another house.
    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (houseRepository.existsByInviteCodeIgnoreCase(code));
        return code;
    }
}
