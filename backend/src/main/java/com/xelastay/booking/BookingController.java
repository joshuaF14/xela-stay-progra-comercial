package com.xelastay.booking;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.booking.dto.BookingResponse;
import com.xelastay.booking.dto.BookingResult;
import com.xelastay.booking.dto.CancelBookingRequest;
import com.xelastay.booking.dto.CreateBookingRequest;
import com.xelastay.booking.dto.RejectBookingRequest;
import com.xelastay.common.PageResponse;
import com.xelastay.security.CurrentUser;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reservas. El usuario siempre sale del JWT (CurrentUser), nunca del cuerpo ni de la URL. */
@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    /** 201 con la reserva nueva, o 200 con la existente si se repitió la clave de idempotencia. */
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResult result = bookingService.create(currentUser.requireId(), request);
        if (!result.created()) {
            return ResponseEntity.ok(result.booking());
        }
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + result.booking().id()))
                .body(result.booking());
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse get(@PathVariable UUID id) {
        return bookingService.get(currentUser.requireId(), id);
    }

    @PatchMapping("/bookings/{id}/confirm")
    public BookingResponse confirm(@PathVariable UUID id) {
        return bookingService.confirm(currentUser.requireId(), id);
    }

    @PatchMapping("/bookings/{id}/reject")
    public BookingResponse reject(@PathVariable UUID id,
            @Valid @RequestBody(required = false) RejectBookingRequest request) {
        return bookingService.reject(currentUser.requireId(), id, request != null ? request.reason() : null);
    }

    @PatchMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelBookingRequest request) {
        return bookingService.cancel(currentUser.requireId(), id, request.reason());
    }

    @PatchMapping("/bookings/{id}/complete")
    public BookingResponse complete(@PathVariable UUID id) {
        return bookingService.complete(currentUser.requireId(), id);
    }

    @GetMapping("/me/bookings")
    public PageResponse<BookingResponse> myBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingService.listAsGuest(currentUser.requireId(), status, page, size);
    }

    @GetMapping("/me/hosting/bookings")
    public PageResponse<BookingResponse> hostingBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingService.listAsHost(currentUser.requireId(), status, page, size);
    }
}
