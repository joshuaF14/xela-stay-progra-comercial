package com.xelastay.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.payment.dto.CreatePaymentRequest;
import com.xelastay.payment.dto.PaymentResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @Test
    void payRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentService);
    }

    @Test
    void payReturns201ForTheTokenUser() throws Exception {
        when(paymentService.pay(eq(userId), eq(bookingId), any(CreatePaymentRequest.class)))
                .thenReturn(response(PaymentStatus.APPROVED));

        mockMvc.perform(post("/api/v1/bookings/{id}/payments", bookingId).with(as("ROLE_GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(700.0));
    }

    @Test
    void unknownMethodIs400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{id}/payments", bookingId).with(as("ROLE_GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/bookings/{id}/payments", bookingId).with(as("ROLE_GUEST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("paymentMethod"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void bookingPaymentsPassTheAdminFlag() throws Exception {
        when(paymentService.listForBooking(userId, true, bookingId)).thenReturn(List.of(response(PaymentStatus.APPROVED)));

        mockMvc.perform(get("/api/v1/bookings/{id}/payments", bookingId).with(as("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paymentId.toString()));
    }

    @Test
    void adminSearchRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/payments").with(as("ROLE_HOST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void adminSearchParsesFilters() throws Exception {
        when(paymentService.search(PaymentStatus.APPROVED, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 0,
                20)).thenReturn(new PageResponse<>(List.of(response(PaymentStatus.APPROVED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/payments").param("status", "APPROVED").param("from", "2026-11-01")
                        .param("to", "2026-11-30").with(as("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminRefunds() throws Exception {
        when(paymentService.refund(paymentId)).thenReturn(response(PaymentStatus.REFUNDED));

        mockMvc.perform(patch("/api/v1/payments/{id}/refund", paymentId).with(as("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void myPaymentsUseTheTokenUser() throws Exception {
        when(paymentService.mine(userId, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response(PaymentStatus.APPROVED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/me/payments").with(as("ROLE_GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionReference").value("SIM-123"));
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(userId.toString())).authorities(new SimpleGrantedAuthority(role));
    }

    private PaymentResponse response(PaymentStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new PaymentResponse(paymentId, bookingId, new BigDecimal("700.00"), "GTQ", PaymentMethod.CARD, status,
                "SIM-123", now, now);
    }
}
