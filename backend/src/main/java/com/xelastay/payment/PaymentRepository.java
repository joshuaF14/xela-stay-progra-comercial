package com.xelastay.payment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    /** Pagos de las reservas en las que el usuario es huésped. */
    @Query(value = "select p from Payment p where p.bookingId in (select b.id from Booking b where b.guestId = :guestId)",
            countQuery = "select count(p) from Payment p "
                    + "where p.bookingId in (select b.id from Booking b where b.guestId = :guestId)")
    Page<Payment> findByGuestId(@Param("guestId") UUID guestId, Pageable pageable);

    static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Payment> createdFrom(OffsetDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<Payment> createdBefore(OffsetDateTime before) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
    }
}
