package com.xelastay.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.xelastay.booking.dto.AvailabilityBlockRequest;
import com.xelastay.booking.dto.AvailabilityBlockResponse;
import com.xelastay.booking.dto.AvailabilityResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    private final UUID userId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @Test
    void availabilityIsPublic() throws Exception {
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);
        when(availabilityService.occupiedDates(propertyId, from, to))
                .thenReturn(new AvailabilityResponse(propertyId, from, to, List.of(LocalDate.of(2030, 1, 5))));

        mockMvc.perform(get("/api/v1/properties/{id}/availability", propertyId)
                        .param("from", "2030-01-01").param("to", "2030-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedDates[0]").value("2030-01-05"));
    }

    @Test
    void invalidDateFormatIs400() throws Exception {
        mockMvc.perform(get("/api/v1/properties/{id}/availability", propertyId).param("from", "05/01/2030"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(availabilityService);
    }

    @Test
    void onlyGetOnAvailabilityIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/properties/{id}/availability", propertyId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blocksRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/properties/{id}/availability-blocks", propertyId))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(availabilityService);
    }

    @Test
    void createBlockReturns201ForTheCurrentUser() throws Exception {
        UUID blockId = UUID.randomUUID();
        when(availabilityService.createBlock(eq(userId), eq(propertyId), any(AvailabilityBlockRequest.class)))
                .thenReturn(new AvailabilityBlockResponse(blockId, propertyId, LocalDate.of(2030, 2, 1),
                        LocalDate.of(2030, 2, 5), "Mantenimiento", userId, Instant.now()));

        mockMvc.perform(post("/api/v1/properties/{id}/availability-blocks", propertyId).with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2030-02-01\",\"endDate\":\"2030-02-05\",\"reason\":\"Mantenimiento\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(blockId.toString()))
                .andExpect(jsonPath("$.createdBy").value(userId.toString()));
    }

    @Test
    void deleteBlockReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/properties/{id}/availability-blocks/{blockId}", propertyId, UUID.randomUUID())
                        .with(user()))
                .andExpect(status().isNoContent());
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }
}
