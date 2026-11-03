package com.xelastay.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xelastay.reporting.dto.ReportInfo;
import com.xelastay.reporting.dto.ReportResult;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Registro de reportes: descubre todos los {@link ReportDefinition} y los ejecuta por id. */
@Service
public class ReportService {

    /** Periodo por defecto si no se envían fechas: los últimos 30 días. */
    static final int DEFAULT_DAYS = 30;
    /** Rango máximo para no recorrer años de datos por error. */
    static final int MAX_DAYS = 3 * 366;

    private final Map<String, ReportDefinition> reports = new LinkedHashMap<>();

    public ReportService(List<ReportDefinition> definitions) {
        definitions.stream()
                .sorted(Comparator.comparingInt(ReportDefinition::number))
                .forEach(definition -> {
                    if (reports.putIfAbsent(definition.id(), definition) != null) {
                        throw new IllegalStateException("Hay dos reportes con el id " + definition.id());
                    }
                });
    }

    public List<ReportInfo> list() {
        return reports.values().stream().map(ReportInfo::from).toList();
    }

    public ReportResult generate(String id, LocalDate start, LocalDate end) {
        ReportDefinition definition = reports.get(id);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado: " + id);
        }
        ReportPeriod period = period(start, end);
        ReportTable table = definition.generate(period);
        return new ReportResult(definition.number(), definition.id(), definition.title(), definition.description(),
                period.start(), period.end(), Instant.now(), table.columns(), table.rows(), table.summary());
    }

    static ReportPeriod period(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now(ReportPeriod.ZONE);
        LocalDate effectiveEnd = end != null ? end : today;
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusDays(DEFAULT_DAYS - 1L);
        if (effectiveEnd.isBefore(effectiveStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha final no puede ser anterior a la inicial");
        }
        if (effectiveStart.plusDays(MAX_DAYS).isBefore(effectiveEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo máximo es de 3 años");
        }
        return new ReportPeriod(effectiveStart, effectiveEnd);
    }
}
