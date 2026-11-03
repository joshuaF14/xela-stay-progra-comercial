package com.xelastay.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vence las reservas {@code PENDING} que el anfitrión no respondió (decisión 2 de docs/decisions/PENDIENTES.md):
 * a las 24 horas de creadas (configurable) o cuando llega el día de entrada, lo que pase primero. Quedan
 * {@code CANCELLED} con un motivo automático y se auditan con {@code source = SYSTEM}.
 * <p>
 * Corre cada 15 minutos dentro del backend. Se apaga con {@code BOOKING_EXPIRATION_ENABLED=false}.
 */
@Component
@ConditionalOnProperty(prefix = "xelastay.bookings.expiration", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class PendingBookingExpirer {

    static final String REASON = "Venció: el anfitrión no respondió a tiempo";

    private static final Logger log = LoggerFactory.getLogger(PendingBookingExpirer.class);

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final Duration pendingTime;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public PendingBookingExpirer(BookingRepository bookings, BookingService bookingService,
            @Value("${xelastay.bookings.expiration.pending-hours:24}") long pendingHours,
            @Value("${xelastay.bookings.expiration.batch-size:100}") int batchSize) {
        this(bookings, bookingService, Duration.ofHours(pendingHours), batchSize, Clock.systemUTC());
    }

    PendingBookingExpirer(BookingRepository bookings, BookingService bookingService, Duration pendingTime,
            int batchSize, Clock clock) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.pendingTime = pendingTime;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${xelastay.bookings.expiration.initial-delay-ms:60000}",
            fixedDelayString = "${xelastay.bookings.expiration.interval-ms:900000}")
    public void run() {
        expireDue();
    }

    /**
     * Vence una ronda de reservas pendientes. Cada una va en su propia transacción: si una falla, sigue con las demás
     * y se reintenta en la próxima ronda.
     *
     * @return cuántas reservas vencieron
     */
    public int expireDue() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, BookingService.ZONE);
        List<UUID> due = bookings.findIdsToExpire(BookingStatus.PENDING, now.minus(pendingTime), today,
                PageRequest.of(0, batchSize));
        int expired = 0;
        for (UUID bookingId : due) {
            try {
                if (bookingService.expire(bookingId, REASON)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo vencer la reserva {}; se reintenta en la próxima ronda", bookingId, ex);
            }
        }
        if (expired > 0) {
            log.info("Reservas pendientes vencidas: {}", expired);
        }
        return expired;
    }
}
