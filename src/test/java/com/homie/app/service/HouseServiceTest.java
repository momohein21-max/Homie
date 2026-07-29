package com.homie.app.service;

import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.HouseRepository;
import com.homie.app.repository.RoomRepository;
import com.homie.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HouseService.
 *
 * Every repository is mocked with Mockito rather than hitting a real
 * database - these tests check HouseService's own business logic (invite
 * code generation/retry, owner-only permission checks, cleaning-rota
 * maths, room bookkeeping) in isolation, independently of Hibernate/
 * Postgres and any live Supabase connection.
 */
@ExtendWith(MockitoExtension.class)
class HouseServiceTest {

    @Mock
    private HouseRepository houseRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;

    private HouseService houseService;

    @BeforeEach
    void setUp() {
        houseService = new HouseService(houseRepository, roomRepository, userRepository);
    }

    // --- createHouse -------------------------------------------------

    @Test
    void createHouse_savesFounderAsOwnerWithRoomsAndFirstRotaSlot() {
        User founder = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        when(userRepository.save(any(User.class))).thenReturn(founder);
        when(houseRepository.existsByInviteCodeIgnoreCase(anyString())).thenReturn(false);
        when(houseRepository.save(any(House.class))).thenAnswer(inv -> inv.getArgument(0));

        House house = houseService.createHouse("14 Elm Street", 3, founder);

        assertEquals("14 Elm Street", house.getName());
        assertEquals(6, house.getInviteCode().length());
        assertSame(founder, house.getCreatedBy());
        assertSame(house, founder.getHouse());
        assertEquals(0, founder.getCleaningOrder());

        verify(userRepository, times(2)).save(any(User.class));

        ArgumentCaptor<List<Room>> roomsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roomRepository).saveAll(roomsCaptor.capture());
        List<Room> savedRooms = roomsCaptor.getValue();
        assertEquals(3, savedRooms.size());
        assertEquals("Room 1", savedRooms.get(0).getName());
        assertEquals("Room 3", savedRooms.get(2).getName());
    }

    @Test
    void createHouse_retriesInviteCodeGeneration_onCollision() {
        User founder = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        when(userRepository.save(any(User.class))).thenReturn(founder);
        // First generated code collides with an existing house, second is free.
        when(houseRepository.existsByInviteCodeIgnoreCase(anyString())).thenReturn(true, false);
        when(houseRepository.save(any(House.class))).thenAnswer(inv -> inv.getArgument(0));

        houseService.createHouse("14 Elm Street", 1, founder);

        verify(houseRepository, times(2)).existsByInviteCodeIgnoreCase(anyString());
    }

    // --- joinHouse -----------------------------------------------------

    @Test
    void joinHouse_blankCode_returnsErrorWithoutTouchingRepository() {
        User newUser = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");

        String result = houseService.joinHouse("   ", newUser);

        assertEquals("Please enter your house's invite code.", result);
        verifyNoInteractions(houseRepository);
    }

    @Test
    void joinHouse_unknownCode_returnsError() {
        when(houseRepository.findByInviteCodeIgnoreCase("ABC123")).thenReturn(Optional.empty());
        User newUser = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");

        String result = houseService.joinHouse("ABC123", newUser);

        assertEquals("That invite code doesn't match any house. Double-check it with your housemates.", result);
    }

    @Test
    void joinHouse_validCode_addsMemberAtEndOfRota() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        House house = new House("14 Elm Street", "ABC123", null, owner);
        when(houseRepository.findByInviteCodeIgnoreCase("abc123")).thenReturn(Optional.of(house));
        when(userRepository.countByHouse(house)).thenReturn(3L);

        User newUser = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        String result = houseService.joinHouse("abc123", newUser);

        assertNull(result);
        assertSame(house, newUser.getHouse());
        assertEquals(3, newUser.getCleaningOrder());
        verify(userRepository).save(newUser);
    }

    // --- renameHouse (owner-only) ---------------------------------------

    @Test
    void renameHouse_notOwner_returnsPermissionError() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);

        String result = houseService.renameHouse(house, "New Name", "someone-else@test.com");

        assertNotNull(result);
        assertTrue(result.contains("Only the housemate who created"));
        verify(houseRepository, never()).save(any());
    }

    @Test
    void renameHouse_owner_updatesName() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);

        String result = houseService.renameHouse(house, "  The Green House  ", owner.getEmail());

        assertNull(result);
        assertEquals("The Green House", house.getName());
        verify(houseRepository).save(house);
    }

    @Test
    void renameHouse_blankName_returnsError() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);

        String result = houseService.renameHouse(house, "  ", owner.getEmail());

        assertEquals("Please enter a house name.", result);
    }

    // --- addRoom ---------------------------------------------------------

    @Test
    void addRoom_atMaxCapacity_returnsError() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        List<Room> twentyRooms = new ArrayList<>();
        for (int i = 0; i < HouseService.MAX_ROOMS; i++) {
            twentyRooms.add(new Room(house, "Room " + (i + 1), i));
        }
        when(roomRepository.findByHouseOrderByOrderIndexAsc(house)).thenReturn(twentyRooms);

        String result = houseService.addRoom(house, owner.getEmail());

        assertEquals("A house can have at most 20 rooms.", result);
        verify(roomRepository, never()).save(any());
    }

    // --- removeRoom --------------------------------------------------------

    @Test
    void removeRoom_lastRoomInHouse_returnsError() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        Room onlyRoom = new Room(house, "Room 1", 0);
        when(roomRepository.countByHouse(house)).thenReturn(1L);

        String result = houseService.removeRoom(onlyRoom, owner.getEmail());

        assertEquals("A house needs at least one room.", result);
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void removeRoom_unassignsOccupantsThenDeletesRoom() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        Room room = new Room(house, "Room 2", 1);
        room.setId(2L);
        when(roomRepository.countByHouse(house)).thenReturn(2L);

        User occupant = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        occupant.setRoom(room);
        User elsewhere = new User("Bea", "bea@test.com", "hashed", "ROLE_USER");
        Room otherRoom = new Room(house, "Room 1", 0);
        otherRoom.setId(1L);
        elsewhere.setRoom(otherRoom);
        when(userRepository.findByHouse(house)).thenReturn(List.of(occupant, elsewhere));

        String result = houseService.removeRoom(room, owner.getEmail());

        assertNull(result);
        assertNull(occupant.getRoom());
        assertSame(otherRoom, elsewhere.getRoom());
        verify(userRepository).saveAll(List.of(occupant));
        verify(roomRepository).delete(room);
    }

    // --- moveMember (cleaning rota reorder) ---------------------------------

    @Test
    void moveMember_swapsCleaningOrderWithPreviousMember() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);

        User a = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        a.setId(10L);
        a.setCleaningOrder(0);
        User b = new User("Bea", "bea@test.com", "hashed", "ROLE_USER");
        b.setId(11L);
        b.setCleaningOrder(1);
        when(userRepository.findByHouseOrderByCleaningOrderAsc(house)).thenReturn(List.of(a, b));

        String result = houseService.moveMember(house, 11L, true, owner.getEmail());

        assertNull(result);
        assertEquals(1, a.getCleaningOrder());
        assertEquals(0, b.getCleaningOrder());
        verify(userRepository).save(a);
        verify(userRepository).save(b);
    }

    @Test
    void moveMember_alreadyFirst_isNoOp() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);

        User a = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        a.setId(10L);
        a.setCleaningOrder(0);
        when(userRepository.findByHouseOrderByCleaningOrderAsc(house)).thenReturn(List.of(a));

        String result = houseService.moveMember(house, 10L, true, owner.getEmail());

        assertNull(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void moveMember_unknownMember_returnsError() {
        User owner = ownerWithId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        when(userRepository.findByHouseOrderByCleaningOrderAsc(house)).thenReturn(List.of());

        String result = houseService.moveMember(house, 999L, true, owner.getEmail());

        assertEquals("That housemate isn't in this house.", result);
    }

    // --- helpers -------------------------------------------------------

    private User ownerWithId(Long id) {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(id);
        return owner;
    }
}
