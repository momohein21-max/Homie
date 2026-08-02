package com.homie.app.service;

import com.homie.app.dto.BillCreateDto;
import com.homie.app.entity.Bill;
import com.homie.app.entity.BillPayment;
import com.homie.app.entity.House;
import com.homie.app.entity.User;
import com.homie.app.repository.BillPaymentRepository;
import com.homie.app.repository.BillRepository;
import com.homie.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillService.
 *
 * Repositories are mocked so these tests focus purely on BillService's own
 * rules: splitting a new bill equally across every current housemate,
 * only letting the bill's creator edit/toggle/delete it, and the
 * outstanding/your-share-due roll-up maths shown on the Bills page.
 */
@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private UserRepository userRepository;

    private BillService billService;

    @BeforeEach
    void setUp() {
        billService = new BillService(billRepository, billPaymentRepository, userRepository);
    }

    // --- createBill --------------------------------------------------------

    @Test
    void createBill_createsOneBillPaymentPerHousemate() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        House house = new House("14 Elm Street", "ABC123", null, creator);
        creator.setHouse(house);
        User mate1 = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        User mate2 = new User("Bea", "bea@test.com", "hashed", "ROLE_USER");
        when(userRepository.findByHouse(house)).thenReturn(List.of(creator, mate1, mate2));

        BillCreateDto dto = new BillCreateDto();
        dto.setDescription("June electricity");
        dto.setCategory("Electricity");
        dto.setTotalAmount(90.0);
        dto.setDueDate(LocalDate.now().plusDays(14));

        billService.createBill(dto, creator);

        ArgumentCaptor<Bill> billCaptor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(billCaptor.capture());
        Bill savedBill = billCaptor.getValue();
        assertEquals("June electricity", savedBill.getDescription());
        assertEquals(90.0, savedBill.getTotalAmount());
        assertSame(house, savedBill.getHouse());
        assertSame(creator, savedBill.getCreatedBy());

        verify(billPaymentRepository, times(3)).save(any(BillPayment.class));
    }

    // --- outstandingTotal / yourShareDue -----------------------------------

    @Test
    void outstandingTotal_sumsUnpaidSharesAcrossAllBillsInHouse() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        House house = new House("14 Elm Street", "ABC123", null, creator);

        Bill bill1 = new Bill("Electricity", "Electricity", 100.0, LocalDate.now(), LocalDate.now(), creator, house);
        addPayment(bill1, creator, true);   // paid, share = 50
        addPayment(bill1, creator, false);  // unpaid, share = 50 -> 50 outstanding

        Bill bill2 = new Bill("Wifi", "Wifi", 60.0, LocalDate.now(), LocalDate.now(), creator, house);
        addPayment(bill2, creator, false);  // unpaid, share = 60 -> 60 outstanding

        when(billRepository.findByHouseOrderByDueDateAsc(house)).thenReturn(List.of(bill1, bill2));

        assertEquals(110.0, billService.outstandingTotal(house), 0.001);
    }

    @Test
    void yourShareDue_onlyCountsRequestingUsersOwnUnpaidShares() {
        User you = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        you.setId(1L);
        User housemate = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        housemate.setId(2L);
        House house = new House("14 Elm Street", "ABC123", null, you);

        Bill bill = new Bill("Electricity", "Electricity", 100.0, LocalDate.now(), LocalDate.now(), you, house);
        addPayment(bill, you, false);        // you still owe 50
        addPayment(bill, housemate, true);   // housemate already paid, doesn't count towards your total

        when(billRepository.findByHouseOrderByDueDateAsc(house)).thenReturn(List.of(bill));

        assertEquals(50.0, billService.yourShareDue(house, 1L), 0.001);
        assertEquals(0.0, billService.yourShareDue(house, 2L), 0.001);
    }

    // --- updateBill ----------------------------------------------------

    @Test
    void updateBill_requestedByNonCreator_returnsPermissionError() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        String result = billService.updateBill(1L, "New desc", "Wifi", 40.0, LocalDate.now(), "someone-else@test.com");

        assertNotNull(result);
        assertTrue(result.contains("can update this bill"));
        verify(billRepository, never()).save(any());
    }

    @Test
    void updateBill_nonPositiveAmount_returnsError() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        String result = billService.updateBill(1L, "New desc", "Wifi", 0.0, LocalDate.now(), "momo@test.com");

        assertEquals("Amount must be greater than 0.", result);
    }

    @Test
    void updateBill_validChanges_updatesAndSaves() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
        LocalDate newDueDate = LocalDate.now().plusDays(7);

        String result = billService.updateBill(1L, "Updated desc", "Gas", 75.5, newDueDate, "momo@test.com");

        assertNull(result);
        assertEquals("Updated desc", bill.getDescription());
        assertEquals("Gas", bill.getCategory());
        assertEquals(75.5, bill.getTotalAmount());
        assertEquals(newDueDate, bill.getDueDate());
        verify(billRepository).save(bill);
    }

    // --- togglePaid ------------------------------------------------------

    @Test
    void togglePaid_requestedByNonCreator_returnsPermissionError() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        BillPayment payment = new BillPayment(bill, creator);
        when(billPaymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        String result = billService.togglePaid(5L, "someone-else@test.com");

        assertNotNull(result);
        verify(billPaymentRepository, never()).save(any());
    }

    @Test
    void togglePaid_marksUnpaidShareAsPaidWithToday() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        BillPayment payment = new BillPayment(bill, creator);
        when(billPaymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        String result = billService.togglePaid(5L, "momo@test.com");

        assertNull(result);
        assertTrue(payment.isPaid());
        assertEquals(LocalDate.now(), payment.getPaidDate());
        verify(billPaymentRepository).save(payment);
    }

    @Test
    void togglePaid_marksPaidShareBackToUnpaidAndClearsDate() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        BillPayment payment = new BillPayment(bill, creator);
        payment.setPaid(true);
        payment.setPaidDate(LocalDate.now().minusDays(2));
        when(billPaymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        billService.togglePaid(5L, "momo@test.com");

        assertFalse(payment.isPaid());
        assertNull(payment.getPaidDate());
    }

    // --- deleteBill ------------------------------------------------------

    @Test
    void deleteBill_requestedByNonCreator_returnsPermissionError() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        String result = billService.deleteBill(1L, "someone-else@test.com");

        assertNotNull(result);
        verify(billRepository, never()).delete(any());
    }

    @Test
    void deleteBill_requestedByCreator_deletesBill() {
        User creator = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        Bill bill = existingBill(creator);
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        String result = billService.deleteBill(1L, "momo@test.com");

        assertNull(result);
        verify(billRepository).delete(bill);
    }

    // --- deleteAllForUser (account deletion cleanup) ------------------------

    @Test
    void deleteAllForUser_removesOwnShareFromOthersBillsAndDeletesOwnBills() {
        User leaving = new User("Momo", "momo@test.com", "hashed", "ROLE_USER");
        leaving.setId(7L);
        User housemate = new User("Ann", "ann@test.com", "hashed", "ROLE_USER");
        housemate.setId(8L);
        House house = new House("14 Elm Street", "ABC123", null, housemate);

        // A bill someone else created that the leaving user owes a share on.
        Bill othersBill = new Bill("Wifi", "Wifi", 60.0, LocalDate.now(), LocalDate.now(), housemate, house);
        addPayment(othersBill, housemate, false);
        addPayment(othersBill, leaving, false);

        // A bill the leaving user created themselves.
        Bill ownBill = new Bill("Electricity", "Electricity", 100.0, LocalDate.now(), LocalDate.now(), leaving, house);
        addPayment(ownBill, leaving, false);
        addPayment(ownBill, housemate, false);

        when(billRepository.findByHouse(house)).thenReturn(List.of(othersBill, ownBill));

        billService.deleteAllForUser(7L, house);

        assertEquals(1, othersBill.getPayments().size());
        assertSame(housemate, othersBill.getPayments().get(0).getUser());

        verify(billRepository).saveAll(List.of(othersBill, ownBill));
        verify(billRepository).deleteAll(List.of(ownBill));
    }

    // --- helpers -------------------------------------------------------

    private Bill existingBill(User creator) {
        House house = new House("14 Elm Street", "ABC123", null, creator);
        Bill bill = new Bill("June electricity", "Electricity", 90.0, LocalDate.now().plusDays(10),
                LocalDate.now(), creator, house);
        bill.setId(1L);
        return bill;
    }

    private void addPayment(Bill bill, User user, boolean paid) {
        BillPayment payment = new BillPayment(bill, user);
        payment.setPaid(paid);
        bill.getPayments().add(payment);
    }
}
