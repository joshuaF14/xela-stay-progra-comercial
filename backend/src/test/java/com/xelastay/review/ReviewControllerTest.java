package com.xelastay.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.review.dto.ReviewRequest;
import com.xelastay.review.dto.ReviewResponse;

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
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    private final UUID userId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();

    @Test
    void propertyReviewsArePublic() throws Exception {
        when(reviewService.listForProperty(propertyId, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response(5)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/properties/{id}/reviews", propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].reviewerName").value("Ana López"));
    }

    @Test
    void writingAReviewRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    void createReturns201() throws Exception {
        when(reviewService.create(eq(userId), eq(bookingId), any(ReviewRequest.class))).thenReturn(response(5));

        mockMvc.perform(post("/api/v1/bookings/{id}/review", bookingId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Excelente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reviewId.toString()));
    }

    @Test
    void ratingOutsideOneToFiveIs400() throws Exception {
        mockMvc.perform(put("/api/v1/reviews/{id}", reviewId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("rating"));
        verifyNoInteractions(reviewService);
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).with(user()))
                .andExpect(status().isNoContent());
        verify(reviewService).delete(userId, reviewId);
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private ReviewResponse response(int rating) {
        Instant now = Instant.now();
        return new ReviewResponse(reviewId, bookingId, propertyId, userId, "Ana López", rating, "Excelente", now, now);
    }
}
