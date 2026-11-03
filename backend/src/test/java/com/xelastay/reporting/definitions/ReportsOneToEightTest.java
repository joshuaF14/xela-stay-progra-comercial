package com.xelastay.reporting.definitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import com.xelastay.reporting.NativeQueries;
import com.xelastay.reporting.ReportDefinition;
import com.xelastay.reporting.ReportPeriod;
import com.xelastay.reporting.ReportService;
import com.xelastay.reporting.ReportSummary;
import com.xelastay.reporting.ReportTable;
import com.xelastay.reporting.dto.ReportInfo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reportes 1 al 8 (Joshua), que leen las vistas del esquema bi, con las consultas simuladas. */
class ReportsOneToEightTest {

    /** Del 15 de octubre al 13 de noviembre: 30 días; los meses que se cruzan son octubre y noviembre. */
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 13));
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

    /** FROM o JOIN seguido del nombre de la tabla o vista. */
    private static final Pattern SOURCE = Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([a-z_][a-z0-9_.]*)");

    private final NativeQueries queries = mock(NativeQueries.class);

    private List<ReportDefinition> mine() {
        return List.of(new IncomeByMonthReport(queries), new BookingsByStatusReport(queries),
                new OccupancyReport(queries), new PropertiesByCategoryReport(queries), new TopRatedReport(queries),
                new IncomeByHostReport(queries), new UsersByMonthAndRoleReport(queries),
                new PaymentsByMethodReport(queries));
    }

    private List<ReportDefinition> giovanni() {
        return List.of(new CancellationsReport(queries), new StayDurationReport(queries),
                new RatingsDistributionReport(queries), new ChatResponseReport(queries),
                new NotificationsReport(queries), new AiUsageReport(queries), new AuditActivityReport(queries));
    }

    @Test
    void numbersAndIdsAreUniqueAndComplete() {
        assertThat(mine()).extracting(ReportDefinition::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        Set<String> ids = new HashSet<>();
        for (ReportDefinition report : mine()) {
            assertThat(ids.add(report.id())).as("id repetido: %s", report.id()).isTrue();
            assertThat(report.id()).matches("[a-z0-9-]+");
            assertThat(report.title()).isNotBlank();
            assertThat(report.description()).isNotBlank();
        }
    }

    @Test
    void theFifteenReportsLiveTogetherInOrder() {
        List<ReportDefinition> all = new ArrayList<>(giovanni());
        all.addAll(mine());

        ReportService service = new ReportService(all);

        assertThat(service.list()).extracting(ReportInfo::number)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).boxed().toList());
    }

    @Test
    void everyQueryReadsOnlyBiViewsWithThePeriodParameters() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of());
        when(queries.single(anyString(), anyMap())).thenReturn(List.of());

        for (ReportDefinition report : mine()) {
            ReportTable table = report.generate(PERIOD);
            assertThat(table.rows()).as(report.id()).isEmpty();
            assertThat(table.columns()).as(report.id()).isNotEmpty();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(queries, atLeastOnce()).rows(sql.capture(), parameters.capture());
        verify(queries, atLeastOnce()).single(sql.capture(), parameters.capture());

        for (String query : sql.getAllValues()) {
            Matcher source = SOURCE.matcher(query);
            int sources = 0;
            while (source.find()) {
                sources++;
                assertThat(source.group(1)).as("solo vistas de bi: %s", query).startsWith("bi.");
            }
            assertThat(sources).as(query).isPositive();
            // Hibernate trata ":" como inicio de parámetro: los casts van con CAST(... AS ...), no con "::".
            assertThat(query).doesNotContain("::");
            assertThat(query.replace(":fromMonth", "").replace(":start", "").replace(":end", "")).doesNotContain(":");
        }
        Set<Map<String, Object>> allowed = Set.of(
                Map.of(),
                Map.of("fromMonth", OCTOBER, "end", PERIOD.end()),
                Map.of("start", PERIOD.start(), "end", PERIOD.end()));
        assertThat(parameters.getAllValues()).allSatisfy(map -> assertThat(allowed).contains(map));
    }

    @Test
    void incomeByMonthLabelsTheMonthAndAddsTheTotals() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row(OCTOBER, "GTQ", 2L, new BigDecimal("900.00"), new BigDecimal("250.00"), new BigDecimal("650.00")),
                row(NOVEMBER, "GTQ", 1L, new BigDecimal("380.00"), BigDecimal.ZERO, new BigDecimal("380.00"))));

        ReportTable table = new IncomeByMonthReport(queries).generate(PERIOD);

        assertThat(table.rows()).extracting(r -> r.get(0)).containsExactly("2026-10", "2026-11");
        assertThat(summary(table, "Pagos aprobados")).isEqualTo(3L);
        assertThat(summary(table, "Monto aprobado (GTQ)")).isEqualTo(new BigDecimal("1280.00"));
        assertThat(summary(table, "Ingreso neto (GTQ)")).isEqualTo(new BigDecimal("1030.00"));
    }

    @Test
    void bookingsByStatusCountsConfirmedOrCompleted() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row(NOVEMBER, "Cancelada", "GTQ", 1L, 1L, new BigDecimal("250.00")),
                row(NOVEMBER, "Completada", "GTQ", 1L, 1L, new BigDecimal("250.00")),
                row(NOVEMBER, "Confirmada", "GTQ", 2L, 4L, new BigDecimal("1700.00"))));

        ReportTable table = new BookingsByStatusReport(queries).generate(PERIOD);

        assertThat(summary(table, "Reservas")).isEqualTo(4L);
        assertThat(summary(table, "Confirmadas o completadas")).isEqualTo(3L);
        assertThat(summary(table, "% confirmadas o completadas")).isEqualTo(new BigDecimal("75.0"));
        assertThat(summary(table, "Noches")).isEqualTo(6L);
    }

    @Test
    void occupancyDividesNightsByTheDaysOfThePeriod() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row("Casa en Zunil", "Zunil", 3L),
                row("Hostal Los Altos", "Quetzaltenango", 0L)));

        ReportTable table = new OccupancyReport(queries).generate(PERIOD);

        assertThat(table.rows().get(0)).containsExactly("Casa en Zunil", "Zunil", 3L, 30L, new BigDecimal("10.0"));
        assertThat(table.rows().get(1).get(4)).isEqualTo(new BigDecimal("0.0"));
        assertThat(table.rows().get(0)).hasSize(table.columns().size());
        assertThat(summary(table, "Noches reservadas")).isEqualTo(3L);
        assertThat(summary(table, "Ocupación promedio (%)")).isEqualTo(new BigDecimal("5.0"));
    }

    @Test
    void propertiesByCategoryTranslatesTheStatusAndWeightsThePrice() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row("Hostal", "Quetzaltenango", "Quetzaltenango", "PUBLISHED", "GTQ", 2L, new BigDecimal("250.00")),
                row("Casa", "Quetzaltenango", "Zunil", "PAUSED", "GTQ", 1L, new BigDecimal("850.00"))));

        ReportTable table = new PropertiesByCategoryReport(queries).generate(PERIOD);

        assertThat(table.rows()).extracting(r -> r.get(3)).containsExactly("Publicado", "Pausado");
        assertThat(summary(table, "Alojamientos vigentes")).isEqualTo(3L);
        assertThat(summary(table, "Publicados")).isEqualTo(2L);
        assertThat(summary(table, "Precio promedio por noche (GTQ)")).isEqualTo(new BigDecimal("450.00"));
    }

    @Test
    void topRatedUsesTheTotalsQuery() {
        when(queries.rows(anyString(), anyMap()))
                .thenReturn(List.of(row("Casa en Zunil", "Zunil", 2L, new BigDecimal("4.50"), NOVEMBER)));
        when(queries.single(anyString(), anyMap())).thenReturn(row(2L, new BigDecimal("4.50"), 1L));

        ReportTable table = new TopRatedReport(queries).generate(PERIOD);

        assertThat(table.rows().get(0)).hasSize(table.columns().size());
        assertThat(summary(table, "Reseñas")).isEqualTo(2L);
        assertThat(summary(table, "Calificación promedio general")).isEqualTo(new BigDecimal("4.50"));
    }

    @Test
    void emptyTotalsGiveEmptySummaryValues() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of());
        when(queries.single(anyString(), anyMap())).thenReturn(List.of());

        ReportTable table = new TopRatedReport(queries).generate(PERIOD);

        assertThat(table.summary()).extracting(ReportSummary::value).containsOnlyNulls();
    }

    @Test
    void incomeByHostAddsReservationsAndAmount() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row("Ana López", 4L, 3L, new BigDecimal("1200.00")),
                row("Luis Pérez", 1L, 1L, new BigDecimal("250.00"))));

        ReportTable table = new IncomeByHostReport(queries).generate(PERIOD);

        assertThat(summary(table, "Anfitriones con reservas")).isEqualTo(2L);
        assertThat(summary(table, "Reservas")).isEqualTo(4L);
        assertThat(summary(table, "Monto aprobado (GTQ)")).isEqualTo(new BigDecimal("1450.00"));
    }

    @Test
    void usersByRoleSummarizesEachRole() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row(OCTOBER, "GUEST", 2L),
                row(NOVEMBER, "GUEST", 2L),
                row(NOVEMBER, "HOST", 1L)));

        ReportTable table = new UsersByMonthAndRoleReport(queries).generate(PERIOD);

        assertThat(summary(table, "Con rol GUEST")).isEqualTo(4L);
        assertThat(summary(table, "Con rol HOST")).isEqualTo(1L);
    }

    @Test
    void paymentsTranslateMethodAndStatusAndComputeTheApprovalRate() {
        when(queries.rows(anyString(), anyMap())).thenReturn(List.of(
                row(NOVEMBER, "CARD", "APPROVED", "GTQ", 3L, new BigDecimal("1500.00")),
                row(NOVEMBER, "CASH", "REJECTED", "GTQ", 1L, new BigDecimal("250.00"))));

        ReportTable table = new PaymentsByMethodReport(queries).generate(PERIOD);

        assertThat(table.rows().get(0)).containsExactly("2026-11", "Tarjeta", "Aprobado", "GTQ", 3L,
                new BigDecimal("1500.00"));
        assertThat(table.rows().get(1).get(1)).isEqualTo("Efectivo");
        assertThat(summary(table, "% de aprobación")).isEqualTo(new BigDecimal("75.0"));
        assertThat(summary(table, "Monto (GTQ)")).isEqualTo(new BigDecimal("1750.00"));
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
