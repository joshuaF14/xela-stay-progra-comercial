package com.xelastay.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.common.PageResponse;
import com.xelastay.payment.dto.CreatePaymentRequest;
import com.xelastay.payment.dto.PaymentResponse;
import com.xelastay.profile.SystemRoles;
import com.xelastay.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pagos simulados de reservas. El pago no cambia el estado de la reserva. */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    /** Solo el huésped de la reserva. Devuelve 201 también si la simulación lo rechaza (status REJECTED). */
    @PostMapping("/bookings/{bookingId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@PathVariable UUID bookingId, @Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.pay(currentUser.requireId(), bookingId, request);
    }

    @GetMapping("/bookings/{bookingId}/payments")
    public List<PaymentResponse> listForBooking(@PathVariable UUID bookingId) {
        return paymentService.listForBooking(currentUser.requireId(), currentUser.hasRole(SystemRoles.ADMIN),
                bookingId);
    }

    @GetMapping("/me/payments")
    public PageResponse<PaymentResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.mine(currentUser.requireId(), page, size);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<PaymentResponse> search(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.search(status, from, to, page, size);
    }

    @PatchMapping("/payments/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse refund(@PathVariable UUID id) {
        return paymentService.refund(id);
    }
}
