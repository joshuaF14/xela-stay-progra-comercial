package com.xelastay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.ai.AiLogWriter;
import com.xelastay.booking.AvailabilityBlockRepository;
import com.xelastay.booking.Booking;
import com.xelastay.booking.BookingRepository;
import com.xelastay.booking.BookingService;
import com.xelastay.booking.PropertyInfo;
import com.xelastay.booking.PropertyReader;
import com.xelastay.events.OutboxService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Puntos donde los módulos de reservas e IA escriben en audit_logs a través de AuditService. */
@ExtendWith(MockitoExtension.class)
class AuditIntegrationPointsTest {

    @Mock
    private AuditService audit;

    @Mock
    private BookingRepository bookings;

    @Mock
    private AvailabilityBlockRepository blocks;

    @Mock
    private PropertyReader properties;

    @Mock
    private OutboxService outbox;

    @Test
    void bookingTransitionIsAudited() {
        UUID hostId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        Booking booking = Booking.pending(propertyId, UUID.randomUUID(), LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12), 2, new BigDecimal("350.00"), new BigDecimal("700.00"), BigDecimal.ZERO,
                "GTQ", null);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(properties.findById(propertyId)).thenReturn(Optional.of(
                new PropertyInfo(propertyId, hostId, "Casa", new BigDecimal("350.00"), "GTQ", 4, "PUBLISHED")));
        BookingService service = new BookingService(bookings, blocks, properties, outbox, audit);

        service.confirm(hostId, booking.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> state = ArgumentCaptor.forClass(Map.class);
        verify(audit).recordUserAction(eq("BOOKING_CONFIRMED"), eq("booking"), eq(booking.getId()), isNull(),
                state.capture());
        assertThat(state.getValue()).containsEntry("status", "CONFIRMED");
    }

    @Test
    void aiLogWriterDelegatesToAuditServiceKeepingItsDefaults() {
        AiLogWriter writer = new AiLogWriter(new ObjectMapper(), audit);
        UUID adminId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        writer.audit(adminId, "AI_DOCUMENT_INGESTED", "knowledge_document", documentId, Map.of("chunks", 3),
                "ADMIN", null, " ");

        verify(audit).record(eq(adminId), eq("AI_DOCUMENT_INGESTED"), eq("knowledge_document"), eq(documentId),
                isNull(), eq(Map.of("chunks", 3)), eq(AuditSource.ADMIN), eq("0.0.0.0"), eq("desconocido"));
    }

    @Test
    void aiLogWriterPassesTheRequestIpAndUserAgent() {
        AiLogWriter writer = new AiLogWriter(new ObjectMapper(), audit);

        writer.audit(null, "AI_CHAT", "ai_interaction", UUID.randomUUID(), Map.of(), "AI", "190.1.2.3", "curl/8");

        verify(audit).record(isNull(), eq("AI_CHAT"), eq("ai_interaction"), any(), isNull(), any(),
                eq(AuditSource.AI), eq("190.1.2.3"), eq("curl/8"));
    }
}
