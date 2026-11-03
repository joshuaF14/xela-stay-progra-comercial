package com.xelastay.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.booking.dto.AvailabilityBlockRequest;
import com.xelastay.booking.dto.AvailabilityBlockResponse;
import com.xelastay.booking.dto.AvailabilityResponse;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final LocalDate JAN_10 = LocalDate.of(2030, 1, 10);
    private static final LocalDate JAN_14 = LocalDate.of(2030, 1, 14);

    @Mock
    private BookingRepository bookings;

    @Mock
    private AvailabilityBlockRepository blocks;

    @Mock
    private PropertyReader properties;

    private final UUID hostId = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private PropertyInfo property;
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        property = new PropertyInfo(propertyId, hostId, "Casa en Xela", new BigDecimal("350.00"), "GTQ", 4,
                "PUBLISHED");
        service = new AvailabilityService(bookings, blocks, properties);
    }

    @Test
    void occupiedDatesCombineBookingsAndBlocksWithinTheRange() {
        when(properties.findPublished(propertyId)).thenReturn(Optional.of(property));
        // Noches 8, 9, 10 y 11; dentro del rango solo cuentan 10 y 11.
        Booking early = booking(LocalDate.of(2030, 1, 8), LocalDate.of(2030, 1, 12));
        // Noches 14 a 19; dentro del rango solo cuenta 14.
        Booking late = booking(JAN_14, LocalDate.of(2030, 1, 20));
        // Noches 11 y 12 (la 11 se repite con la reserva).
        AvailabilityBlock block = AvailabilityBlock.create(propertyId, LocalDate.of(2030, 1, 11),
                LocalDate.of(2030, 1, 13), null, hostId);
        LocalDate endExclusive = JAN_14.plusDays(1);
        when(bookings.findOverlapping(propertyId, BookingStatus.ACTIVE, JAN_10, endExclusive))
                .thenReturn(List.of(early, late));
        when(blocks.findOverlapping(propertyId, JAN_10, endExclusive)).thenReturn(List.of(block));

        AvailabilityResponse response = service.occupiedDates(propertyId, JAN_10, JAN_14);

        assertThat(response.occupiedDates()).containsExactly(
                LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 11), LocalDate.of(2030, 1, 12), JAN_14);
    }

    @Test
    void toBeforeFromIsBadRequest() {
        assertStatus(() -> service.occupiedDates(propertyId, JAN_14, JAN_10), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(properties);
    }

    @Test
    void rangeLongerThanAYearIsBadRequest() {
        assertStatus(() -> service.occupiedDates(propertyId, JAN_10, JAN_10.plusDays(400)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void unpublishedPropertyIsNotFound() {
        when(properties.findPublished(propertyId)).thenReturn(Optional.empty());

        assertStatus(() -> service.occupiedDates(propertyId, JAN_10, JAN_14), HttpStatus.NOT_FOUND);
    }

    @Test
    void hostCreatesBlock() {
        when(properties.lockActive(propertyId)).thenReturn(Optional.of(property));
        when(blocks.save(any(AvailabilityBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AvailabilityBlockResponse response = service.createBlock(hostId, propertyId,
                new AvailabilityBlockRequest(JAN_10, JAN_14, " Mantenimiento "));

        assertThat(response.startDate()).isEqualTo(JAN_10);
        assertThat(response.endDate()).isEqualTo(JAN_14);
        assertThat(response.reason()).isEqualTo("Mantenimiento");
        assertThat(response.createdBy()).isEqualTo(hostId);
    }

    @Test
    void onlyTheOwnerCanCreateBlocks() {
        when(properties.lockActive(propertyId)).thenReturn(Optional.of(property));

        assertStatus(() -> service.createBlock(guestId, propertyId, new AvailabilityBlockRequest(JAN_10, JAN_14, null)),
                HttpStatus.FORBIDDEN);
        verify(blocks, never()).save(any());
    }

    @Test
    void blockCannotCoverActiveBookings() {
        when(properties.lockActive(propertyId)).thenReturn(Optional.of(property));
        when(bookings.countOverlapping(propertyId, BookingStatus.ACTIVE, JAN_10, JAN_14)).thenReturn(1L);

        assertStatus(() -> service.createBlock(hostId, propertyId, new AvailabilityBlockRequest(JAN_10, JAN_14, null)),
                HttpStatus.CONFLICT);
        verify(blocks, never()).save(any());
    }

    @Test
    void blockEndMustBeAfterStart() {
        when(properties.lockActive(propertyId)).thenReturn(Optional.of(property));

        assertStatus(() -> service.createBlock(hostId, propertyId, new AvailabilityBlockRequest(JAN_14, JAN_10, null)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatingABlockIgnoresItsOwnDates() {
        AvailabilityBlock existing = AvailabilityBlock.create(propertyId, JAN_10, JAN_14, null, hostId);
        UUID blockId = UUID.randomUUID();
        ReflectionTestUtils.setField(existing, "id", blockId);
        when(properties.lockActive(propertyId)).thenReturn(Optional.of(property));
        when(blocks.findByIdAndPropertyId(blockId, propertyId)).thenReturn(Optional.of(existing));
        when(blocks.findOverlapping(propertyId, JAN_10, JAN_14.plusDays(2))).thenReturn(List.of(existing));

        AvailabilityBlockResponse response = service.updateBlock(hostId, propertyId, blockId,
                new AvailabilityBlockRequest(JAN_10, JAN_14.plusDays(2), "Remodelación"));

        assertThat(response.endDate()).isEqualTo(JAN_14.plusDays(2));
        assertThat(response.reason()).isEqualTo("Remodelación");
    }

    @Test
    void deletingABlockOfAnotherPropertyIsNotFound() {
        UUID blockId = UUID.randomUUID();
        when(properties.lockActive(propertyId)).thenReturn(Optional.of(property));
        when(blocks.findByIdAndPropertyId(blockId, propertyId)).thenReturn(Optional.empty());

        assertStatus(() -> service.deleteBlock(hostId, propertyId, blockId), HttpStatus.NOT_FOUND);
        verify(blocks, never()).delete(any());
    }

    private Booking booking(LocalDate checkIn, LocalDate checkOut) {
        return Booking.pending(propertyId, guestId, checkIn, checkOut, 2, new BigDecimal("350.00"),
                new BigDecimal("700.00"), new BigDecimal("0.00"), "GTQ", null);
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(expected));
    }
}
