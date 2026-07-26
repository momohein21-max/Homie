package com.homie.app.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One room within a House, e.g. "Room 1" or a renamed "The Attic".
 *
 * Replaces the old fixed 5-room setup (Room 1..Room 5, hardcoded for one
 * specific house) with real rows so any house can have however many rooms
 * it actually has. Created automatically when a house is set up (one row
 * per room the creator asked for) and can be renamed, added to, or removed
 * afterwards by the house owner - see HouseService.
 *
 * orderIndex controls the room's position in the bins/washing rota (see
 * ScheduleService.roomOnDutyFor), which cycles through a house's rooms one
 * per day regardless of how many rooms it has.
 */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "house_id", nullable = false)
    private House house;

    @Column(nullable = false)
    private String name;

    // Where this room sits in the bins/washing rotation, 0-based. Kept as
    // an explicit field (rather than relying on database row order, which
    // JPA doesn't guarantee) so the house owner can reorder rooms later
    // without their ids needing to change.
    @Column(nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "room")
    private List<User> occupants = new ArrayList<>();

    public Room() {
    }

    public Room(House house, String name, int orderIndex) {
        this.house = house;
        this.name = name;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public House getHouse() {
        return house;
    }

    public void setHouse(House house) {
        this.house = house;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public List<User> getOccupants() {
        return occupants;
    }

    public void setOccupants(List<User> occupants) {
        this.occupants = occupants;
    }

    // "Single (1 person)" / "Shared (2 people)" / "Empty" style label,
    // matching the wording the old fixed-room version used.
    @Transient
    public String getTypeLabel() {
        int count = occupants == null ? 0 : occupants.size();
        if (count == 0) return "Empty";
        if (count == 1) return "Single (1 person)";
        return "Shared (" + count + " people)";
    }

    // Comma-and-ampersand joined names of whoever lives here, e.g.
    // "Ágatha & Julia", for the schedule/members pages. Empty string if
    // nobody's moved in yet.
    @Transient
    public String getOccupantNames() {
        if (occupants == null || occupants.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < occupants.size(); i++) {
            if (i > 0) {
                sb.append(i == occupants.size() - 1 ? " & " : ", ");
            }
            sb.append(occupants.get(i).getName());
        }
        return sb.toString();
    }
}
