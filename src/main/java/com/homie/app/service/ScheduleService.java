package com.homie.app.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Works out two things from the house rules, for any given date:
 *
 *  1) The cleaning rota - one person per week, rotating in a fixed order,
 *     looping back to the start after the last person.
 *  2) The washing/bins room - a fixed map of weekday to room.
 *
 * Keeping this logic here (not in the controller) means it is easy to unit
 * test and reuse from the scheduled reminder job later.
 *
 * NOTE: These names and dates are fixed house data for now. In a later sprint
 * they will come from the database (Member entity with a rotation position),
 * but the calculation itself will not change.
 */
@Service
public class ScheduleService {

    // The cleaning order. Index 0 is the anchor person for the anchor week.
    private static final List<String> CLEANING_ORDER = List.of(
            "Julia", "Edgar", "Momo", "Sheron", "Luis",
            "Lívia", "Ágatha", "Allyne", "Edecilmar"
    );

    // The Saturday that Julia's week starts (17 May 2025). Each new week
    // begins on this weekday. We count whole weeks from here to find who is on.
    private static final LocalDate ANCHOR = LocalDate.of(2025, 5, 17);

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    /**
     * Returns the person responsible for cleaning during the week containing
     * the given date.
     */
    public String cleanerFor(LocalDate date) {
        return CLEANING_ORDER.get(indexFor(date));
    }

    // The position in CLEANING_ORDER that is "on" for the given date's week.
    private int indexFor(LocalDate date) {
        long weeks = ChronoUnit.WEEKS.between(ANCHOR, date);
        // Java's % can be negative for dates before the anchor, so we add the
        // size and take % again to always land on a valid list index.
        return (int) (((weeks % CLEANING_ORDER.size()) + CLEANING_ORDER.size())
                % CLEANING_ORDER.size());
    }

    /**
     * Returns the next few people due to clean after the current week,
     * in order, for showing an "up next" list.
     */
    public List<String> upNextCleaners(LocalDate date, int howMany) {
        int idx = indexFor(date);
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= howMany; i++) {
            int index = Math.floorMod(idx + i, CLEANING_ORDER.size());
            result.add(CLEANING_ORDER.get(index));
        }
        return result;
    }

    /**
     * Returns which room is on washing + bins for the given date's weekday,
     * or null if it is a weekend (no room assigned).
     *
     * Fixed mapping:
     *   Monday    -> Room 3
     *   Tuesday   -> Room 1
     *   Wednesday -> Room 4
     *   Thursday  -> Room 5
     *   Friday    -> Room 2
     */
    public String roomOnDutyFor(LocalDate date) {
        return roomForWeekday(date.getDayOfWeek());
    }

    // Exposed so other code (and tests) can read the order if needed.
    public List<String> getCleaningOrder() {
        return CLEANING_ORDER;
    }

    // Which members live in each room (fixed house data for now).
    // Used to show names next to each room on the washing/bins page.
    private static final java.util.Map<String, String> ROOM_MEMBERS = java.util.Map.of(
            "Room 1", "Edgar",
            "Room 2", "Allyne & Lívia",
            "Room 3", "Ágatha & Julia",
            "Room 4", "Sheron & Momo",
            "Room 5", "Edecilmar & Luis"
    );

    // The names of whoever lives in the given room.
    public String membersOf(String room) {
        if (room == null) return "";
        return ROOM_MEMBERS.getOrDefault(room, "");
    }

    /**
     * A small holder describing one room and who lives in it, for the
     * "Rooms in the house" panel.
     */
    public static class Room {
        private final String name;
        private final String members;
        private final String type;
        public Room(String name, String members, String type) {
            this.name = name; this.members = members; this.type = type;
        }
        public String getName() { return name; }
        public String getMembers() { return members; }
        public String getType() { return type; }
    }

    // All five rooms in order, with members and single/shared type.
    public List<Room> allRooms() {
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("Room 1", ROOM_MEMBERS.get("Room 1"), "Single (1 person)"));
        rooms.add(new Room("Room 2", ROOM_MEMBERS.get("Room 2"), "Shared (2 people)"));
        rooms.add(new Room("Room 3", ROOM_MEMBERS.get("Room 3"), "Shared (2 people)"));
        rooms.add(new Room("Room 4", ROOM_MEMBERS.get("Room 4"), "Shared (2 people)"));
        rooms.add(new Room("Room 5", ROOM_MEMBERS.get("Room 5"), "Shared (2 people)"));
        return rooms;
    }

    // "single" or "shared", for compact display (e.g. "Room 4 · shared" on
    // the housemates grid). Returns "" if the room isn't recognised.
    public String roomTypeShort(String room) {
        if (room == null) return "";
        for (Room r : allRooms()) {
            if (r.getName().equals(room)) {
                return r.getType().startsWith("Single") ? "single" : "shared";
            }
        }
        return "";
    }

    /**
     * A small holder describing one day's washing/bins duty: the weekday name,
     * the room on duty, and whether it is today (so the page can highlight it).
     */
    public static class DutyDay {
        private final String dayName;
        private final String room;
        private final String members;
        private final boolean today;

        public DutyDay(String dayName, String room, String members, boolean today) {
            this.dayName = dayName;
            this.room = room;
            this.members = members;
            this.today = today;
        }
        public String getDayName() { return dayName; }
        public String getRoom() { return room; }
        public String getMembers() { return members; }
        public boolean isToday() { return today; }
    }

    /**
     * Returns the full fixed washing/bins schedule for the working week
     * (Monday to Friday), each day with its room, marking which one is today.
     * The page uses this to show the whole grid.
     */
    public List<DutyDay> weeklyDuties(LocalDate today) {
        DayOfWeek todayDow = today.getDayOfWeek();
        // Build Monday..Friday in order.
        DayOfWeek[] week = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        };
        List<DutyDay> duties = new ArrayList<>();
        for (DayOfWeek d : week) {
            String room = roomForWeekday(d);
            String members = membersOf(room);
            String name = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            duties.add(new DutyDay(name, room, members, d == todayDow));
        }
        return duties;
    }

    // Helper: the room assigned to a given weekday (same fixed map as above).
    private String roomForWeekday(DayOfWeek day) {
        switch (day) {
            case MONDAY:    return "Room 3";
            case TUESDAY:   return "Room 1";
            case WEDNESDAY: return "Room 4";
            case THURSDAY:  return "Room 5";
            case FRIDAY:    return "Room 2";
            default:        return null;
        }
    }

    // The reverse of roomForWeekday: which weekday (e.g. "Wednesday") a
    // given room is on for washing/bins. Used on the housemates grid.
    // Returns "Not set" if the member has no room, or "Unassigned" if
    // somehow the room doesn't match one of the fixed five (shouldn't
    // happen with the current fixed 5-room house).
    public String weekdayForRoom(String room) {
        if (room == null || room.isBlank()) return "Not set";
        DayOfWeek[] week = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        };
        for (DayOfWeek d : week) {
            if (room.equals(roomForWeekday(d))) {
                return d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            }
        }
        return "Unassigned";
    }

    /**
     * A small holder describing one week in the rota: who cleans, the Saturday
     * their week starts, whether it is the current week, and a status label
     * ("done" / "current" / "upcoming") for badge colouring.
     */
    public static class RotaWeek {
        private final String name;
        private final LocalDate weekStart;
        private final boolean current;
        private final String status;

        public RotaWeek(String name, LocalDate weekStart, boolean current) {
            this(name, weekStart, current, current ? "current" : "upcoming");
        }

        public RotaWeek(String name, LocalDate weekStart, boolean current, String status) {
            this.name = name;
            this.weekStart = weekStart;
            this.current = current;
            this.status = status;
        }
        public String getName() { return name; }
        public LocalDate getWeekStart() { return weekStart; }
        public boolean isCurrent() { return current; }
        public String getStatus() { return status; }
    }

    /**
     * Returns the rota for the current week plus the next several weeks, in
     * order, each with the date that week begins. The first item is the current
     * week (marked current = true).
     *
     * We work out the Saturday on which the current week started, then step
     * forward one week at a time, naming whoever is on for each.
     */
    public List<RotaWeek> upcomingRota(LocalDate today, int howManyWeeks) {
        long weeksSinceAnchor = ChronoUnit.WEEKS.between(ANCHOR, today);
        // The Saturday the current rota week began.
        LocalDate currentWeekStart = ANCHOR.plusWeeks(weeksSinceAnchor);

        List<RotaWeek> rota = new ArrayList<>();
        for (int i = 0; i < howManyWeeks; i++) {
            LocalDate weekStart = currentWeekStart.plusWeeks(i);
            String name = cleanerFor(weekStart);
            rota.add(new RotaWeek(name, weekStart, i == 0));
        }
        return rota;
    }

    /**
     * Returns all 9 housemates in their fixed cleaning order, each tagged
     * "done" (their week already passed this cycle), "current" (this week),
     * or "upcoming" (their week is still to come this cycle), with the date
     * their week starts. Used for the full cleaning rota page.
     */
    public List<RotaWeek> fullRotationStatus(LocalDate today) {
        long weeksSinceAnchor = ChronoUnit.WEEKS.between(ANCHOR, today);
        LocalDate currentWeekStart = ANCHOR.plusWeeks(weeksSinceAnchor);
        int currentIndex = indexFor(today);

        List<RotaWeek> result = new ArrayList<>();
        for (int i = 0; i < CLEANING_ORDER.size(); i++) {
            LocalDate weekStart = currentWeekStart.plusWeeks(i - currentIndex);
            String status = i < currentIndex ? "done" : i == currentIndex ? "current" : "upcoming";
            result.add(new RotaWeek(CLEANING_ORDER.get(i), weekStart, i == currentIndex, status));
        }
        return result;
    }

    /**
     * A short label for when the given housemate next cleans (or is
     * cleaning right now), for the housemates grid: "This week",
     * "Week of Jul 6", etc. If their week for this cycle has already
     * passed, this looks ahead to their next turn (9 weeks later).
     */
    public String cleaningLabelFor(String memberName, LocalDate today) {
        for (RotaWeek week : fullRotationStatus(today)) {
            if (!week.getName().equals(memberName)) {
                continue;
            }
            if (week.isCurrent()) {
                return "This week";
            }
            LocalDate weekStart = week.getWeekStart();
            if ("done".equals(week.getStatus())) {
                weekStart = weekStart.plusWeeks(CLEANING_ORDER.size());
            }
            return "Week of " + weekStart.format(DAY_MONTH);
        }
        return "—";
    }
}
