package com.xelastay.reporting;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ejecuta las consultas SQL de los reportes (solo lectura) y normaliza los valores con {@link ReportValues}.
 * Los reportes dependen de esta clase y no del EntityManager, así se prueban con un mock.
 */
@Component
@Transactional(readOnly = true)
public class NativeQueries {

    @PersistenceContext
    private EntityManager entityManager;

    /** Filas de la consulta; cada fila es una lista con un valor por columna del SELECT. */
    public List<List<Object>> rows(String sql, Map<String, Object> parameters) {
        Query query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        List<?> result = query.getResultList();
        return result.stream().map(NativeQueries::toRow).toList();
    }

    /** Primera fila de la consulta (para totales); una lista vacía si no hay filas. */
    public List<Object> single(String sql, Map<String, Object> parameters) {
        List<List<Object>> rows = rows(sql, parameters);
        return rows.isEmpty() ? List.of() : rows.get(0);
    }

    /** Parámetros :from y :to del periodo (instantes en hora de Guatemala). */
    public static Map<String, Object> period(ReportPeriod period) {
        return Map.of("from", period.from(), "to", period.toExclusive());
    }

    private static List<Object> toRow(Object row) {
        Object[] columns = row instanceof Object[] array ? array : new Object[] {row};
        java.util.List<Object> values = new java.util.ArrayList<>(columns.length);
        for (Object column : columns) {
            values.add(ReportValues.normalize(column));
        }
        return values;
    }
}
