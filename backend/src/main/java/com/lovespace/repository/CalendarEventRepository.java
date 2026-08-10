package com.lovespace.repository;

import com.lovespace.domain.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    @Query("""
            select e from CalendarEvent e
            where e.coupleId = :coupleId
              and e.deletedAt is null
              and e.startAt < :endExclusive
              and (e.endAt is null or e.endAt > :startInclusive)
            order by e.startAt asc, e.id asc
            """)
    List<CalendarEvent> findActiveInRange(
            @Param("coupleId") Long coupleId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    Optional<CalendarEvent> findByIdAndCoupleIdAndDeletedAtIsNull(Long id, Long coupleId);
    Optional<CalendarEvent> findByIdAndCoupleIdAndDeletedBy(Long id, Long coupleId, Long deletedBy);
    List<CalendarEvent> findByCoupleIdAndDeletedByOrderByDeletedAtDesc(Long coupleId, Long deletedBy);
    List<CalendarEvent> findByCoupleIdOrderById(Long coupleId);
}
