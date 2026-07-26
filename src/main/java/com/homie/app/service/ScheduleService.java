package com.homie.app.service;

import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.RoomRepository;
import com.homie.app.repository.UserRepository;
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
 * Works out two things from a house's own data, for any given date:
 *
 *  1) The cleaning rota - one member per week, rotating through that
 *     house's members in their chosen order, looping back to the start.
 *  2) The bins/washing room - a fixed weekday per room (Room ranked 1st
 *     gets Monday, 2nd gets Tuesday, and so on), so each room always does
 *     bins on the same day every week, same as before.
 *
 * Everything here is now calculated PER HOUSE rather than from one fixed,
 * hardcoded set of names and rooms - this is what lets any number of
 * different-sized houses each have their own independent rota. Both
 * rotations are anchored to the house's own creation date, so a brand new
 * house always starts its cycle at week 1 / its first member, rather than
 * wherever a single shared calendar date would happen to land.
 *
 * A house with more than 7 rooms is a known edge case: only the first 7
 * (by room order) get a fixed bins weekday, since a week only has 7 days.
 * Extra rooms beyond that don't currently get a bins day assigned - fine
 * for the shared houses Homie is aimed at, but worth knowing about.
 */
@Service
public class ScheduleService {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public ScheduleService(UserRepository userRepository, RoomRepository roomRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    // A house's members, in cleaning-rota order.
    public List<User> orderedMembers(House house) {
        return userRepository.findByHouseOrderByCleaningOrderAsc(house);
    }

    // A house's rooms, in bins-rota order.
    public List<Room> orderedRooms(House house) {
        return roomRepository.findByHouseOrderByOrderIndexAsc(house);
    }

    /**
     * Returns the member responsible for cleaning during the week
     * containing the given date, or null if the house somehow has no
     * members yet (shouldn't happen - every house has at least its owner).
     */
    public User cleanerFor(House house, LocalDate date) {
        List<User> members = orderedMembers(house);
        if (members.isEmpty()) {
            return null;
        }
        return members.get(indexFor(house, date, members.size()));
    }

    // The position in the ordered member list that is "on" for the given
    // date's week, counting whole weeks from the house's creation date.
    private int indexFor(House house, LocalDate date, int memberCount) {
        long weeks = ChronoUnit.WEEKS.between(house.getCreatedDate(), date);
        return (int) Math.floorMod(weeks, memberCount);
    }

    /**
     * True if the given date is the first day of a new cleaning rota week
     * (i.e. it falls on the same weekday as the house's creation date).
     * Useful for a daily reminder job so it only notifies the incoming
     * cleaner once, on the day their week begins.
     */
    public boolean isRotaWeekStart(House house, LocalDate date) {
        return date.getDayOfWeek() == house.getCreatedDate().getDayOfWeek();
    }

    /**
     * Returns the next few members due to clean after the current week,
     * in order, for showing an "up next" list.
     */
    public List<User> upNextCleaners(House house, LocalDate date, int howMany) {
        List<User> members = orderedMembers(house);
        List<User> result = new ArrayList<>();
        if (members.isEmpty()) {
            return result;
        }
        int idx = indexFor(house, date, members.size());
        for (int i = 1; i <= howMany; i++) {
            result.add(members.get(Math.floorMod(idx + i, members.size())));
        }
        return result;
    }

    /**
     * Returns which room is on bins/washing duty for the given date, or
     * null if the house has no rooms, or the date's weekday doesn't have a
     * room assigned (a house with fewer than 7 rooms leaves some weekdays
     * unassigned - the same way the original fixed 5-room house left
     * weekends unassigned).
     */
    public Room roomOnDutyFor(House house, LocalDate date) {
        List<Room> rooms = orderedRooms(house);
        int weekdayIndex = date.getDayOfWeek().getValue() - 1; // Monday = 0
        if (weekdayIndex >= rooms.size()) {
            return null;
        }
        return rooms.get(weekdayIndex);
    }

    // Which weekday (e.g. "Wednesday") a given room does bins on, or
    // "Not set" if the room doesn't currently have a fixed day (only the
    // first 7 rooms, by order, get one).
    public String weekdayForRoom(Room room) {
        if (room == null) {
            return "Not set";
        }
        List<Room> rooms = orderedRooms(room.getHouse());
        int rank = rooms.indexOf(room);
        if (rank < 0 || rank >= 7) {
            return "Not set";
        }
        return DayOfWeek.of(rank + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    // "single" or "shared", for compact display (e.g. "Room 4 · shared" on
    // the housemates grid). Empty string if the room is null.
    public String roomTypeShort(Room room) {
        if (room == null) {
            return "";
        }
        int count = room.getOccupants() == null ? 0 : room.getOccupants().size();
        return count <= 1 ? "single" : "shared";
    }

    /**
     * A single day's bins/washing duty: the weekday name, the room on
     * duty, and whether it's today (so the page can highlight it).
     */
    public static class DutyDay {
        private final String dayName;
        private final Room room;
        private final boolean today;

        public DutyDay(String dayName, Room room, boolean today) {
            this.dayName = dayName;
            this.room = room;
            this.today = today;
        }

        public String getDayName() { return dayName; }
        public Room getRoom() { return room; }
        public boolean isToday() { return today; }
    }

    /**
     * Returns the house's full bins/washing schedule, one entry per room
     * that has a fixed weekday (up to 7), marking which one is today.
     */
    public List<DutyDay> weeklyDuties(House house, LocalDate today) {
        List<Room> rooms = orderedRooms(house);
        DayOfWeek todayDow = today.getDayOfWeek();
        List<DutyDay> duties = new ArrayList<>();
        int days = Math.min(rooms.size(), 7);
        for (int i = 0; i < days; i++) {
            DayOfWeek d = DayOfWeek.of(i + 1);
            String name = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            duties.add(new DutyDay(name, rooms.get(i), d == todayDow));
        }
        return duties;
    }

    /**
     * One week in the cleaning rota: who's on, the date their week starts,
     * whether it's the current week, and a status label ("done" /
     * "current" / "upcoming") for badge colouring.
     */
    public static class RotaEntry {
        private final User user;
        private final LocalDate weekStart;
        private final boolean current;
        private final String status;

        public RotaEntry(User user, LocalDate weekStart, boolean current) {
            this(user, weekStart, current, current ? "current" : "upcoming");
        }

        public RotaEntry(User user, LocalDate weekStart, boolean current, String status) {
            this.user = user;
            this.weekStart = weekStart;
            this.current = current;
            this.status = status;
        }

        public User getUser() { return user; }
        public LocalDate getWeekStart() { return weekStart; }
        public boolean isCurrent() { return current; }
        public String getStatus() { return status; }
    }

    /**
     * Returns the rota for the current week plus the next several weeks,
     * in order, each with the date that week begins. The first item is the
     * current week (marked current = true).
     */
    public List<RotaEntry> upcomingRota(House house, LocalDate today, int howManyWeeks) {
        List<User> members = orderedMembers(house);
        List<RotaEntry> rota = new ArrayList<>();
        if (members.isEmpty()) {
            return rota;
        }
        long weeksSinceAnchor = ChronoUnit.WEEKS.between(house.getCreatedDate(), today);
        LocalDate currentWeekStart = house.getCreatedDate().plusWeeks(weeksSinceAnchor);

        for (int i = 0; i < howManyWeeks; i++) {
            LocalDate weekStart = currentWeekStart.plusWeeks(i);
            User user = members.get(indexFor(house, weekStart, members.size()));
            rota.add(new RotaEntry(user, weekStart, i == 0));
        }
        return rota;
    }

    /**
     * Returns every current member of the house in their fixed cleaning
     * order, each tagged "done" (their week already passed this cycle),
     * "current" (this week), or "upcoming" (still to come), with the date
     * their week starts. Used for the full cleaning rota page.
     */
    public List<RotaEntry> fullRotationStatus(House house, LocalDate today) {
        List<User> members = orderedMembers(house);
        List<RotaEntry> result = new ArrayList<>();
        if (members.isEmpty()) {
            return result;
        }
        long weeksSinceAnchor = ChronoUnit.WEEKS.between(house.getCreatedDate(), today);
        LocalDate currentWeekStart = house.getCreatedDate().plusWeeks(weeksSinceAnchor);
        int currentIndex = indexFor(house, today, members.size());

        for (int i = 0; i < members.size(); i++) {
            LocalDate weekStart = currentWeekStart.plusWeeks(i - currentIndex);
            String status = i < currentIndex ? "done" : i == currentIndex ? "current" : "upcoming";
            result.add(new RotaEntry(members.get(i), weekStart, i == currentIndex, status));
        }
        return result;
    }

    /**
     * A short label for when the given housemate next cleans (or is
     * cleaning right now): "This week", "Week of Jul 6", etc. If their
     * week for this cycle has already passed, this looks ahead to their
     * next turn.
     */
    public String cleaningLabelFor(User member, LocalDate today) {
        if (member.getHouse() == null) {
            return "—";
        }
        List<RotaEntry> rota = fullRotationStatus(member.getHouse(), today);
        for (RotaEntry entry : rota) {
            if (!entry.getUser().getId().equals(member.getId())) {
                continue;
            }
            if (entry.isCurrent()) {
                return "This week";
            }
            LocalDate weekStart = entry.getWeekStart();
            if ("done".equals(entry.getStatus())) {
                weekStart = weekStart.plusWeeks(rota.size());
            }
            return "Week of " + weekStart.format(DAY_MONTH);
        }
        return "—";
    }
}
