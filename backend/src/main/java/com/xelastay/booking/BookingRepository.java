package com.xelastay.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByGuestIdAndIdempotencyKey(UUID guestId, String idempotencyKey);

    /** Bloquea la reserva para cambiar su estado sin carreras (dos clics de "confirmar", etc.). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /** Reservas con estado en {@code statuses} que se cruzan con [checkIn, checkOut). */
    @Query("""
            select count(b) from Booking b
            where b.propertyId = :propertyId and b.status in :statuses
              and b.checkIn < :checkOut and b.checkOut > :checkIn
            """)
    long countOverlapping(@Param("propertyId") UUID propertyId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Query("""
            select b from Booking b
            where b.propertyId = :propertyId and b.status in :statuses
              and b.checkIn < :checkOut and b.checkOut > :checkIn
            order by b.checkIn
            """)
    List<Booking> findOverlapping(@Param("propertyId") UUID propertyId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    Page<Booking> findByGuestId(UUID guestId, Pageable pageable);

    Page<Booking> findByGuestIdAndStatus(UUID guestId, BookingStatus status, Pageable pageable);

    Page<Booking> findByPropertyIdIn(Collection<UUID> propertyIds, Pageable pageable);

    Page<Booking> findByPropertyIdInAndStatus(Collection<UUID> propertyIds, BookingStatus status, Pageable pageable);
}
