package com.xelastay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.security.CurrentUser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository logs;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ProfileNameReader profileNames;

    @Captor
    private ArgumentCaptor<AuditLog> saved;

    private final UUID actorId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();
    private AuditService service;

    record Sample(String name, BigDecimal price) {
    }

    @BeforeEach
    void setUp() {
        service = new AuditService(logs, currentUser, new ObjectMapper().registerModule(new JavaTimeModule()),
                profileNames);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordTakesIpAndUserAgentFromTheCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "200.1.2.3, 10.0.0.5");
        request.addHeader("User-Agent", "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.record(actorId, "PROPERTY_UPDATED", "property", entityId, new Sample("Casa", new BigDecimal("350.00")),
                Map.of("status", "PUBLISHED"), AuditSource.USER);

        verify(logs).save(saved.capture());
        AuditLog log = saved.getValue();
        assertThat(log.getIpAddress()).isEqualTo("200.1.2.3");
        assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(log.getOldValues()).containsEntry("name", "Casa");
        assertThat(log.getNewValues()).containsEntry("status", "PUBLISHED");
        assertThat(log.getSource()).isEqualTo(AuditSource.USER);
        assertThat(log.getActorId()).isEqualTo(actorId);
    }

    @Test
    void withoutRequestIpAndUserAgentAreNull() {
        service.record(null, "SYSTEM_TASK", "booking", entityId, null, null, AuditSource.SYSTEM);

        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getIpAddress()).isNull();
        assertThat(saved.getValue().getUserAgent()).isNull();
        assertThat(saved.getValue().getOldValues()).isNull();
    }

    @Test
    void invalidIpIsDiscardedToAvoidAnInetCastError() {
        assertThat(AuditService.validIp("unknown")).isNull();
        assertThat(AuditService.validIp("0:0:0:0:0:0:0:1")).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(AuditService.validIp(" 190.56.1.2 ")).isEqualTo("190.56.1.2");
    }

    @Test
    void adminActingOnSomethingElseIsSourceAdmin() {
        when(currentUser.id()).thenReturn(Optional.of(actorId));
        when(currentUser.hasRole("ADMIN")).thenReturn(true);

        service.recordAction("PROPERTY_STATUS_CHANGED", "property", entityId, null, null, UUID.randomUUID());

        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(AuditSource.ADMIN);
    }

    @Test
    void adminActingOnOwnResourceIsSourceUser() {
        when(currentUser.id()).thenReturn(Optional.of(actorId));
        when(currentUser.hasRole("ADMIN")).thenReturn(true);

        service.recordAction("PROFILE_UPDATED", "profile", actorId, null, null, actorId);

        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(AuditSource.USER);
    }

    @Test
    void nonAdminIsAlwaysSourceUser() {
        when(currentUser.id()).thenReturn(Optional.of(actorId));
        when(currentUser.hasRole("ADMIN")).thenReturn(false);

        service.recordAction("LOCATION_CREATED", "location", entityId, null, null, null);

        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(AuditSource.USER);
        assertThat(saved.getValue().getActorId()).isEqualTo(actorId);
    }

    @Test
    void searchRejectsInvertedDates() {
        assertThatThrownBy(() -> service.search(null, null, null, null, LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 1), 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(logs);
    }
}
