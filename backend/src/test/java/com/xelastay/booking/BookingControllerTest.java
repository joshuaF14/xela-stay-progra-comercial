package com.xelastay.booking;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.xelastay.booking.dto.BookingResponse;
import com.xelastay.booking.dto.BookingResult;
import com.xelastay.booking.dto.CreateBookingRequest;
import com.xelastay.common.PageResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerTest {

    private static final String VALID_BODY = """
            {"propertyId":"%s","checkIn":"2030-01-10","checkOut":"2030-01-12","guestsCount":2,"idempotencyKey":"k-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private final UUID userId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.formatted(propertyId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(bookingService);
    }

    @Test
    void createReturns201WithLocationForTheUserInTheToken() throws Exception {
        when(bookingService.create(eq(userId), any(CreateBookingRequest.class)))
                .thenReturn(new BookingResult(response(BookingStatus.PENDING), true));

        mockMvc.perform(post("/api/v1/bookings").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/bookings/" + bookingId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(700.0))
                .andExpect(jsonPath("$.checkIn").value("2030-01-10"));
    }

    @Test
    void repeatedIdempotencyKeyReturns200() throws Exception {
        when(bookingService.create(eq(userId), any(CreateBookingRequest.class)))
                .thenReturn(new BookingResult(response(BookingStatus.PENDING), false));

        mockMvc.perform(post("/api/v1/bookings").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.formatted(propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));
    }

    @Test
    void invalidBodyReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/v1/bookings").with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field",
                        hasItems("propertyId", "checkIn", "checkOut", "guestsCount")));
        verifyNoInteractions(bookingService);
    }

    @Test
    void serviceErrorsUseTheApiErrorFormat() throws Exception {
        when(bookingService.confirm(userId, bookingId)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el anfitrión puede confirmar la reserva"));

        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", bookingId).with(user()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Solo el anfitrión puede confirmar la reserva"))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings/" + bookingId + "/confirm"));
    }

    @Test
    void cancelRequiresAReason() throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("reason"));
        verifyNoInteractions(bookingService);
    }

    @Test
    void rejectWorksWithoutBody() throws Exception {
        when(bookingService.reject(userId, bookingId, null)).thenReturn(response(BookingStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/bookings/{id}/reject", bookingId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void myBookingsAreFilteredForTheCurrentUser() throws Exception {
        when(bookingService.listAsGuest(userId, BookingStatus.CONFIRMED, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response(BookingStatus.CONFIRMED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/me/bookings").param("status", "CONFIRMED").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void unknownStatusFilterIs400() throws Exception {
        mockMvc.perform(get("/api/v1/me/hosting/bookings").param("status", "ARCHIVADA").with(user()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bookingService);
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private BookingResponse response(BookingStatus status) {
        Instant now = Instant.now();
        return new BookingResponse(bookingId, propertyId, "Casa en Xela", UUID.randomUUID(), userId,
                LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12), 2, 2, new BigDecimal("350.00"),
                new BigDecimal("700.00"), new BigDecimal("0.00"), new BigDecimal("700.00"), "GTQ", status, null,
                now, now);
    }
}
