package com.xelastay.notification;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.notification.dto.NotificationResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Test
    void notificationsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void listsUnreadNotificationsOfTheCurrentUser() throws Exception {
        when(notificationService.listMine(userId, true, 0, 20))
                .thenReturn(new PageResponse<>(List.of(notification(null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/me/notifications").param("unreadOnly", "true").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_CONFIRMED"));
    }

    @Test
    void markReadReturnsTheNotification() throws Exception {
        when(notificationService.markRead(userId, notificationId)).thenReturn(notification(Instant.now()));

        mockMvc.perform(patch("/api/v1/me/notifications/{id}/read", notificationId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    void markingSomeoneElsesNotificationIs404() throws Exception {
        when(notificationService.markRead(userId, notificationId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));

        mockMvc.perform(patch("/api/v1/me/notifications/{id}/read", notificationId).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notificación no encontrada"));
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private NotificationResponse notification(Instant readAt) {
        return new NotificationResponse(notificationId, "BOOKING_CONFIRMED", "Reserva confirmada",
                "El anfitrión confirmó tu reserva.", readAt, Instant.now());
    }
}
