package com.xelastay.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.xelastay.reporting.dto.ReportInfo;
import com.xelastay.reporting.dto.ReportResult;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReportServiceTest {

    @Test
    void listsReportsOrderedByNumber() {
        ReportService service = new ReportService(List.of(fake(12, "b"), fake(3, "a"), fake(9, "c")));

        assertThat(service.list()).extracting(ReportInfo::number).containsExactly(3, 9, 12);
        assertThat(service.list().get(0).id()).isEqualTo("a");
    }

    @Test
    void duplicatedIdsFailAtStartup() {
        assertThatThrownBy(() -> new ReportService(List.of(fake(1, "same"), fake(2, "same"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    @Test
    void unknownReportIs404() {
        ReportService service = new ReportService(List.of(fake(1, "a")));

        assertThatThrownBy(() -> service.generate("nope", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void generatePassesThePeriodAndCopiesTheTable() {
        AtomicReference<ReportPeriod> received = new AtomicReference<>();
        ReportDefinition definition = new FakeReport(9, "cancellations") {
            @Override
            public ReportTable generate(ReportPeriod period) {
                received.set(period);
                return new ReportTable(List.of(ReportColumn.text("Nombre"), ReportColumn.integer("Total")),
                        List.of(List.of("Casa", 3L)), List.of(new ReportSummary("Total", 3L)));
            }
        };
        ReportService service = new ReportService(List.of(definition));

        ReportResult result = service.generate("cancellations", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(received.get()).isEqualTo(new ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(result.number()).isEqualTo(9);
        assertThat(result.start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.end()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(result.generatedAt()).isNotNull();
        assertThat(result.columns()).hasSize(2);
        assertThat(result.rows()).containsExactly(List.of("Casa", 3L));
        assertThat(result.summary()).containsExactly(new ReportSummary("Total", 3L));
    }

    @Test
    void withoutDatesUsesTheLast30DaysUpToToday() {
        ReportPeriod period = ReportService.period(null, null);

        LocalDate today = LocalDate.now(ReportPeriod.ZONE);
        assertThat(period.end()).isEqualTo(today);
        assertThat(period.start()).isEqualTo(today.minusDays(29));
    }

    @Test
    void onlyEndUses30DaysBeforeIt() {
        ReportPeriod period = ReportService.period(null, LocalDate.of(2026, 3, 31));

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void endBeforeStartIs400() {
        assertThatThrownBy(() -> ReportService.period(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void periodsLongerThanThreeYearsAre400() {
        assertThatThrownBy(() -> ReportService.period(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void periodCoversWholeDaysInGuatemalaTime() {
        ReportPeriod period = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        // Guatemala es UTC-6 todo el año.
        assertThat(period.from()).hasToString("2026-11-01T06:00:00Z");
        assertThat(period.toExclusive()).hasToString("2026-11-02T06:00:00Z");
    }

    private static ReportDefinition fake(int number, String id) {
        return new FakeReport(number, id);
    }

    private static class FakeReport implements ReportDefinition {

        private final int number;
        private final String id;

        FakeReport(int number, String id) {
            this.number = number;
            this.id = id;
        }

        @Override
        public int number() {
            return number;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String title() {
            return "Reporte " + number;
        }

        @Override
        public String description() {
            return "Descripción " + number;
        }

        @Override
        public ReportTable generate(ReportPeriod period) {
            return new ReportTable(List.of(ReportColumn.text("Nombre")), List.of(), List.of());
        }
    }
}
