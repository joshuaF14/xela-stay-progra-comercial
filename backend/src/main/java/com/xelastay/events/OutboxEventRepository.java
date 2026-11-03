package com.xelastay.events;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Toma y bloquea el siguiente lote de eventos por publicar. SKIP LOCKED evita que dos instancias
     * del backend publiquen el mismo evento: cada una salta las filas que la otra ya bloqueó.
     * Debe llamarse dentro de una transacción; el bloqueo dura hasta el commit.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status IN ('PENDING', 'FAILED') AND attempts < :maxAttempts
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("maxAttempts") int maxAttempts, @Param("batchSize") int batchSize);
}
