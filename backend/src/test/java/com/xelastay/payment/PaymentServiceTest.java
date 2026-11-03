package com.xelastay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.booking.Booking;
import com.xelastay.booking.BookingRepository;
import com.xelastay.booking.BookingStatus;
import com.xelastay.booking.PropertyInfo;
import com.xelastay.booking.PropertyReader;
import com.xelastay.payment.dto.CreatePaymentRequest;
import com.xelastay.payment.dto.PaymentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository payments;

    @Mock
    private BookingRepository bookings;

    @Mock
    private PropertyReader properties;

    private final UUID guestId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private PaymentService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, bookings, properties);
        booking = Booking.pending(propertyId, guestId, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12), 2,
                new BigDecimal("350.00"), new BigDecimal("700.00"), BigDecimal.ZERO, "GTQ", null);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
    }

    @Test
    void guestPaysTheBookingTotalAndTheBookingDoesNotChange() {
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.pay(guestId, booking.getId(),
                new CreatePaymentRequest(PaymentMethod.CARD, null));

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.amount()).isEqualByComparingTo("700.00");
        assertThat(response.currency()).isEqualTo("GTQ");
        assertThat(response.transactionReference()).startsWith("SIM-");
        assertThat(response.paidAt()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookings, never()).save(any());
    }

    @Test
    void simulatedFailureIsRejectedWithoutPaidAt() {
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.pay(guestId, booking.getId(),
                new CreatePaymentRequest(PaymentMethod.TRANSFER, true));

        assertThat(response.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(response.paidAt()).isNull();
    }

    @Test
    void onlyTheGuestCanPay() {
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertStatus(() -> service.pay(hostId, booking.getId(), new CreatePaymentRequest(PaymentMethod.CASH, null)),
                HttpStatus.FORBIDDEN);
        verify(payments, never()).save(any());
    }

    @Test
    void cancelledBookingCannotBePaid() {
        ReflectionTestUtils.setField(booking, "status", BookingStatus.CANCELLED);
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertStatus(() -> service.pay(guestId, booking.getId(), new CreatePaymentRequest(PaymentMethod.CARD, null)),
                HttpStatus.CONFLICT);
    }

    @Test
    void secondApprovedPaymentIs409() {
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(payments.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.APPROVED)).thenReturn(true);

        assertStatus(() -> service.pay(guestId, booking.getId(), new CreatePaymentRequest(PaymentMethod.CARD, null)),
                HttpStatus.CONFLICT);
        verify(payments, never()).save(any());
    }

    @Test
    void hostCanListButStrangersCannot() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(properties.findById(propertyId)).thenReturn(Optional.of(
                new PropertyInfo(propertyId, hostId, "Casa", new BigDecimal("350.00"), "GTQ", 4, "PUBLISHED")));
        when(payments.findByBookingIdOrderByCreatedAtDesc(booking.getId())).thenReturn(List.of(
                Payment.simulated(booking.getId(), new BigDecimal("700.00"), "GTQ", PaymentMethod.CARD, true)));

        assertThat(service.listForBooking(hostId, false, booking.getId())).hasSize(1);
        assertStatus(() -> service.listForBooking(UUID.randomUUID(), false, booking.getId()), HttpStatus.FORBIDDEN);
        assertThat(service.listForBooking(UUID.randomUUID(), true, booking.getId())).hasSize(1);
    }

    @Test
    void onlyApprovedPaymentsAreRefunded() {
        Payment rejected = Payment.simulated(booking.getId(), new BigDecimal("700.00"), "GTQ", PaymentMethod.CARD,
                false);
        Payment approved = Payment.simulated(booking.getId(), new BigDecimal("700.00"), "GTQ", PaymentMethod.CARD,
                true);
        UUID rejectedId = UUID.randomUUID();
        UUID approvedId = UUID.randomUUID();
        when(payments.findById(rejectedId)).thenReturn(Optional.of(rejected));
        when(payments.findById(approvedId)).thenReturn(Optional.of(approved));
        when(payments.save(approved)).thenReturn(approved);

        assertStatus(() -> service.refund(rejectedId), HttpStatus.CONFLICT);
        assertThat(service.refund(approvedId).status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void searchRejectsInvertedDates() {
        assertStatus(() -> service.search(null, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1), 0, 20),
                HttpStatus.BAD_REQUEST);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }
}
