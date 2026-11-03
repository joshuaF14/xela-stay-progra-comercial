package com.xelastay.reporting.definitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reportes 9 al 15 (Giovanni), con las consultas simuladas. */
class ReportsNineToFifteenTest {

    private static final ReportPeriod OCTOBER = new ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    private static final Instant WHEN = Instant.parse("2026-10-10T15:00:00Z");

    private final NativeQueries queries = mock(NativeQueries.class);

    private List<ReportDefinition> all() {
        return List.of(new CancellationsReport(queries), new StayDurationReport(queries),
                new RatingsDistributionReport(queries), new ChatResponseReport(queries),
                new NotificationsReport(queries), new AiUsageReport(queries), new AuditActivityReport(queries));
    }

    @Test
    void numbersAndIdsAreUniqueAndComplete() {
        assertThat(all()).extracting(ReportDefinition::number).containsExactly(9, 10, 11, 12, 13, 14, 15);
        Set<String> ids = new HashSet<>();
        for (ReportDefinition report : all()) {
            assertThat(ids.add(report.id())).as("id repetido: %s", report.id()).isTrue();
            assertThat(report.id()).matches("[a-z0-9-]+");
            assertThat(report.title()).isNotBlank();
            assertThat(report.description()).isNotBlank();
        }
    }

    @Test
    void everyQueryUsesOnlyThePeriodParameters() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of());
        when(queries.single(anyString(), anyMap())).thenReturn(List.of());

        for (ReportDefinition report : all()) {
            ReportTable table = report.generate(OCTOBER);
            assertThat(table.rows()).as(report.id()).isEmpty();
            assertThat(table.columns()).as(report.id()).isNotEmpty();
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(queries, atLeastOnce()).rows(sql.capture(), argThat(ReportsNineToFifteenTest::isOctober));
        verify(queries, atLeastOnce()).single(sql.capture(), argThat(ReportsNineToFifteenTest::isOctober));
        for (String query : sql.getAllValues()) {
            assertThat(query).contains(":to");
            // Hibernate trata ":" como inicio de parámetro: los casts van con CAST(... AS ...), no con "::".
            assertThat(query).doesNotContain("::");
            assertThat(query.replace(":from", "").replace(":to", "")).doesNotContain(":");
        }
    }

    @Test
    void cancellationsCountsByStateAndMissingReason() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row(WHEN, "Casa A", "Ana", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), "Cancelada",
                        "Huésped", "Cambio de planes", new BigDecimal("500.00")),
                row(WHEN, "Casa B", "Luis", LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), "Rechazada",
                        "Anfitrión", "Sin motivo", new BigDecimal("250.00")),
                row(WHEN, "Casa A", "Eva", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2), "Cancelada",
                        "Sin registro", "Sin motivo", null)));

        ReportTable table = new CancellationsReport(queries).generate(OCTOBER);

        assertThat(table.columns()).hasSize(9);
        assertThat(table.rows()).hasSize(3);
        assertThat(summary(table, "Cancelaciones")).isEqualTo(2L);
        assertThat(summary(table, "Rechazos")).isEqualTo(1L);
        assertThat(summary(table, "Sin motivo registrado")).isEqualTo(2L);
        assertThat(summary(table, "Monto de las reservas afectadas")).isEqualTo(new BigDecimal("750.00"));
    }

    @Test
    void stayDurationUsesTheTotalsQueryForTheSummary() {
        when(queries.rows(anyString(), anyMap()))
                .thenReturn(List.of(row("Casa A", "Quetzaltenango", 4L, new BigDecimal("2.5"), 1L, 4L,
                        new BigDecimal("12.0"))));
        when(queries.single(anyString(), anyMap())).thenReturn(row(4L, new BigDecimal("2.5"), new BigDecimal("12.0")));

        ReportTable table = new StayDurationReport(queries).generate(OCTOBER);

        assertThat(table.columns()).hasSize(7);
        assertThat(summary(table, "Reservas analizadas")).isEqualTo(4L);
        assertThat(summary(table, "Anticipación promedio en días (todas)")).isEqualTo(new BigDecimal("12.0"));
    }

    @Test
    void emptyTotalsGiveEmptySummaryValues() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of());
        when(queries.single(anyString(), anyMap())).thenReturn(List.of());

        ReportTable table = new RatingsDistributionReport(queries).generate(OCTOBER);

        assertThat(table.columns()).hasSize(8);
        assertThat(table.summary()).extracting(ReportSummary::value).containsOnlyNulls();
    }

    @Test
    void chatResponseHasOneValuePerColumn() {
        when(queries.rows(anyString(), anyMap()))
                .thenReturn(List.of(row("Casa A", "Marta", 2L, 10L, 6L, 4L, new BigDecimal("35.5"))));
        when(queries.single(anyString(), anyMap())).thenReturn(row(2L, 10L, new BigDecimal("35.5")));

        ReportTable table = new ChatResponseReport(queries).generate(OCTOBER);

        assertThat(table.rows().get(0)).hasSize(table.columns().size());
        assertThat(summary(table, "Respuesta promedio de anfitriones (min)")).isEqualTo(new BigDecimal("35.5"));
    }

    @Test
    void notificationTypesGetSpanishLabels() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row("BOOKING_CREATED", 3L, 2L, 1L, new BigDecimal("66.7"), WHEN, WHEN),
                row("OTRO_TIPO", 1L, 0L, 1L, new BigDecimal("0.0"), WHEN, WHEN)));
        when(queries.single(anyString(), anyMap())).thenReturn(row(4L, 2L, 3L));

        ReportTable table = new NotificationsReport(queries).generate(OCTOBER);

        assertThat(table.rows()).extracting(r -> r.get(0)).containsExactly("Solicitud de reserva", "OTRO_TIPO");
        assertThat(summary(table, "Usuarios notificados")).isEqualTo(3L);
    }

    @Test
    void aiUsageCountsCitedDocuments() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row("Reglamento de huéspedes", "POLICY", 5L, 3L, WHEN),
                row("Guía de Xela", "GUIDE", 2L, 2L, WHEN)));
        when(queries.single(anyString(), anyMap())).thenReturn(row(8L, 4L, 1L, new BigDecimal("1.6")));

        ReportTable table = new AiUsageReport(queries).generate(OCTOBER);

        assertThat(table.columns()).hasSize(5);
        assertThat(summary(table, "Preguntas al agente")).isEqualTo(8L);
        assertThat(summary(table, "Documentos citados")).isEqualTo(2L);
    }

    @Test
    void auditGroupsBySourceWithLabels() {
        List<List<Object>> detail = List.of(
                row("BOOKING_CREATED", "booking", "USER", "Ana", 5L, WHEN),
                row("OUTBOX_PUBLISHED", "outbox_event", "SYSTEM", "Sistema", 7L, WHEN));
        List<List<Object>> bySource = List.of(row("SYSTEM", 7L), row("USER", 5L));
        when(queries.rows(anyString(), anyMap())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("GROUP BY a.source\n") ? bySource : detail;
        });

        ReportTable table = new AuditActivityReport(queries).generate(OCTOBER);

        assertThat(table.rows()).extracting(r -> r.get(2)).containsExactly("Usuario", "Sistema");
        assertThat(table.summary()).containsExactly(
                new ReportSummary("Total de operaciones", 12L),
                new ReportSummary("Origen: Sistema", 7L),
                new ReportSummary("Origen: Usuario", 5L));
    }

    private static boolean isOctober(Map<String, Object> parameters) {
        return parameters.equals(Map.of("from", OCTOBER.from(), "to", OCTOBER.toExclusive()));
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static Object summary(ReportTable table, String label) {
        return table.summary().stream()
                .filter(item -> item.label().equals(label))
                .map(ReportSummary::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No está en el resumen: " + label + " " + table.summary()));
    }
}
