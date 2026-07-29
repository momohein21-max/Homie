package com.homie.app.service;

import com.homie.app.dto.ProfileUpdateDto;
import com.homie.app.dto.RegistrationDto;
import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.RoomRepository;
import com.homie.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 *
 * All collaborators (UserRepository, RoomRepository, PasswordEncoder,
 * EmailService, HouseService) are mocked, so these tests exercise
 * UserService's own decisions - duplicate-email rejection, the
 * owner-can't-be-deleted-while-housemates-remain rule, password reset
 * token handling, profile validation - without needing a real database.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private HouseService houseService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roomRepository, passwordEncoder, emailService, houseService);
    }

    // --- register ------------------------------------------------------

    @Test
    void register_duplicateEmail_returnsErrorAndSkipsEverythingElse() {
        RegistrationDto dto = new RegistrationDto();
        dto.setEmail("momo@test.com");
        when(userRepository.existsByEmailIgnoreCase("momo@test.com")).thenReturn(true);

        String result = userService.register(dto);

        assertEquals("That email is already registered.", result);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(houseService);
    }

    @Test
    void register_joinAction_delegatesToHouseServiceJoinHouse() {
        RegistrationDto dto = new RegistrationDto();
        dto.setEmail("ann@test.com");
        dto.setPassword("secret1");
        dto.setAction("JOIN");
        dto.setInviteCode("ABC123");
        when(userRepository.existsByEmailIgnoreCase("ann@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(houseService.joinHouse(eq("ABC123"), any(User.class))).thenReturn(null);

        String result = userService.register(dto);

        assertNull(result);
        verify(houseService).joinHouse(eq("ABC123"), any(User.class));
        verify(houseService, never()).createHouse(any(), anyInt(), any());
    }

    @Test
    void register_createAction_blankHouseName_returnsError() {
        RegistrationDto dto = new RegistrationDto();
        dto.setEmail("momo@test.com");
        dto.setPassword("secret1");
        dto.setAction("CREATE");
        when(userRepository.existsByEmailIgnoreCase("momo@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");

        String result = userService.register(dto);

        assertEquals("Please enter a name for your house.", result);
    }

    @Test
    void register_createAction_roomCountOutOfRange_returnsError() {
        RegistrationDto dto = new RegistrationDto();
        dto.setEmail("momo@test.com");
        dto.setPassword("secret1");
        dto.setAction("CREATE");
        dto.setHouseName("14 Elm Street");
        dto.setRoomCount(25);
        when(userRepository.existsByEmailIgnoreCase("momo@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");

        String result = userService.register(dto);

        assertEquals("Please choose between 1 and 20 rooms.", result);
    }

    @Test
    void register_createAction_success_callsHouseServiceCreateHouse() {
        RegistrationDto dto = new RegistrationDto();
        dto.setEmail("momo@test.com");
        dto.setPassword("secret1");
        dto.setAction("CREATE");
        dto.setHouseName("14 Elm Street");
        dto.setRoomCount(3);
        when(userRepository.existsByEmailIgnoreCase("momo@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");

        String result = userService.register(dto);

        assertNull(result);
        verify(houseService).createHouse(eq("14 Elm Street"), eq(3), any(User.class));
    }

    // --- findByEmail -----------------------------------------------------

    @Test
    void findByEmail_notFound_throwsIllegalStateException() {
        when(userRepository.findByEmailIgnoreCase("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> userService.findByEmail("ghost@test.com"));
    }

    // --- updateProfile ---------------------------------------------------

    @Test
    void updateProfile_emailChangedToOneAlreadyTaken_returnsError() {
        User user = existingUser("old@test.com");
        when(userRepository.findByEmailIgnoreCase("old@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@test.com")).thenReturn(true);

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName("Momo");
        dto.setEmail("new@test.com");

        String result = userService.updateProfile("old@test.com", dto, null);

        assertEquals("That email is already registered to another account.", result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_newPasswordTooShort_returnsError() {
        User user = existingUser("momo@test.com");
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(user));

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName("Momo");
        dto.setEmail("momo@test.com");
        dto.setNewPassword("abc");

        String result = userService.updateProfile("momo@test.com", dto, null);

        assertEquals("New password must be at least 6 characters.", result);
    }

    @Test
    void updateProfile_pictureNotAnImage_returnsError() {
        User user = existingUser("momo@test.com");
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(user));

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName("Momo");
        dto.setEmail("momo@test.com");

        String result = userService.updateProfile("momo@test.com", dto, file);

        assertEquals("Profile picture must be an image file (JPG or PNG).", result);
    }

    @Test
    void updateProfile_pictureTooLarge_returnsError() {
        User user = existingUser("momo@test.com");
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(user));

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(6L * 1024 * 1024);

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName("Momo");
        dto.setEmail("momo@test.com");

        String result = userService.updateProfile("momo@test.com", dto, file);

        assertEquals("Profile picture must be smaller than 5MB.", result);
    }

    @Test
    void updateProfile_roomFromAnotherHouse_returnsError() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(1L);
        House myHouse = new House("14 Elm Street", "ABC123", null, owner);
        owner.setHouse(myHouse);
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(owner));

        House otherHouse = new House("Other House", "XYZ999", null, owner);
        otherHouse.setId(2L);
        myHouse.setId(1L);
        Room roomInOtherHouse = new Room(otherHouse, "Room 1", 0);
        when(roomRepository.findById(50L)).thenReturn(Optional.of(roomInOtherHouse));

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName("Momo");
        dto.setEmail("momo@test.com");
        dto.setRoomId(50L);

        String result = userService.updateProfile("momo@test.com", dto, null);

        assertEquals("Please choose a room from your own house.", result);
    }

    @Test
    void updateProfile_validChanges_savesUpdatedUser() {
        User user = existingUser("momo@test.com");
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(user));

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName("Momo Updated");
        dto.setEmail("momo@test.com");
        dto.setDuty("Bin day reminders");
        dto.setPaymentDetails("revolut.me/momo");

        String result = userService.updateProfile("momo@test.com", dto, null);

        assertNull(result);
        assertEquals("Momo Updated", user.getName());
        assertEquals("Bin day reminders", user.getDuty());
        verify(userRepository).save(user);
    }

    // --- checkAccountDeletable --------------------------------------------

    @Test
    void checkAccountDeletable_ownerWithOtherHousemates_returnsError() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        owner.setHouse(house);
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(owner));
        when(userRepository.countByHouse(house)).thenReturn(3L);

        String result = userService.checkAccountDeletable("momo@test.com");

        assertNotNull(result);
        assertTrue(result.contains("other housemates are still using it"));
    }

    @Test
    void checkAccountDeletable_ownerAloneInHouse_returnsNull() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        owner.setHouse(house);
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(owner));
        when(userRepository.countByHouse(house)).thenReturn(1L);

        assertNull(userService.checkAccountDeletable("momo@test.com"));
    }

    @Test
    void checkAccountDeletable_nonOwner_returnsNullWithoutCountingHousemates() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        User housemate = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        housemate.setId(2L);
        housemate.setHouse(house);
        when(userRepository.findByEmailIgnoreCase("ann@test.com")).thenReturn(Optional.of(housemate));

        assertNull(userService.checkAccountDeletable("ann@test.com"));
        verify(userRepository, never()).countByHouse(any());
    }

    // --- deleteAccount ---------------------------------------------------

    @Test
    void deleteAccount_soleOwner_detachesAndDeletesHouseThenUser() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        owner.setHouse(house);
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(owner));

        userService.deleteAccount("momo@test.com");

        verify(userRepository).detachFromHouseAndRoomNative(1L);
        verify(houseService).deleteHouse(house);
        verify(userRepository).deleteByIdNative(1L);
    }

    @Test
    void deleteAccount_nonOwnerHousemate_doesNotTouchHouse() {
        User owner = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        owner.setId(1L);
        House house = new House("14 Elm Street", "ABC123", null, owner);
        User housemate = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        housemate.setId(2L);
        housemate.setHouse(house);
        when(userRepository.findByEmailIgnoreCase("ann@test.com")).thenReturn(Optional.of(housemate));

        userService.deleteAccount("ann@test.com");

        verify(userRepository, never()).detachFromHouseAndRoomNative(anyLong());
        verify(houseService, never()).deleteHouse(any());
        verify(userRepository).deleteByIdNative(2L);
    }

    // --- password reset flow -----------------------------------------------

    @Test
    void requestPasswordReset_unknownEmail_doesNothingSilently() {
        when(userRepository.findByEmailIgnoreCase("ghost@test.com")).thenReturn(Optional.empty());

        userService.requestPasswordReset("ghost@test.com");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void requestPasswordReset_knownEmail_setsTokenAndSendsEmail() {
        User user = existingUser("momo@test.com");
        when(userRepository.findByEmailIgnoreCase("momo@test.com")).thenReturn(Optional.of(user));

        userService.requestPasswordReset("momo@test.com");

        assertNotNull(user.getResetToken());
        assertNotNull(user.getResetTokenExpiry());
        verify(userRepository).save(user);
        verify(emailService).sendPasswordResetEmail(eq("momo@test.com"), anyString());
    }

    @Test
    void isResetTokenValid_expiredToken_returnsFalse() {
        User user = existingUser("momo@test.com");
        user.setResetToken("tok");
        user.setResetTokenExpiry(LocalDateTime.now().minusMinutes(5));
        when(userRepository.findByResetToken("tok")).thenReturn(Optional.of(user));

        assertFalse(userService.isResetTokenValid("tok"));
    }

    @Test
    void isResetTokenValid_freshToken_returnsTrue() {
        User user = existingUser("momo@test.com");
        user.setResetToken("tok");
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByResetToken("tok")).thenReturn(Optional.of(user));

        assertTrue(userService.isResetTokenValid("tok"));
    }

    @Test
    void resetPassword_mismatchedConfirmation_returnsErrorWithoutTouchingRepository() {
        String result = userService.resetPassword("tok", "newpass1", "newpass2");

        assertEquals("Those passwords don't match.", result);
        verifyNoInteractions(userRepository);
    }

    @Test
    void resetPassword_expiredOrMissingToken_returnsError() {
        when(userRepository.findByResetToken("tok")).thenReturn(Optional.empty());

        String result = userService.resetPassword("tok", "newpass1", "newpass1");

        assertEquals("That reset link is invalid or has expired. Please request a new one.", result);
    }

    @Test
    void resetPassword_validToken_setsNewPasswordAndClearsToken() {
        User user = existingUser("momo@test.com");
        user.setResetToken("tok");
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByResetToken("tok")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass1")).thenReturn("hashed-new");

        String result = userService.resetPassword("tok", "newpass1", "newpass1");

        assertNull(result);
        assertEquals("hashed-new", user.getPassword());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiry());
        verify(userRepository).save(user);
    }

    // --- helpers -------------------------------------------------------

    private User existingUser(String email) {
        User user = new User("Momo", email, "hashed", "ROLE_USER");
        user.setId(1L);
        return user;
    }
}
