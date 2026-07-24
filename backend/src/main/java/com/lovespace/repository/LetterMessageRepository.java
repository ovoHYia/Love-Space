package com.lovespace.repository;
import com.lovespace.domain.LetterMessage;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface LetterMessageRepository extends JpaRepository<LetterMessage, Long> {
    @Query("""
            select m from LetterMessage m
            where m.coupleId = :coupleId
              and (m.authorId = :userId
                   or (m.recipientId = :userId and m.deliverAt <= :now))
            """)
    Page<LetterMessage> findVisibleByCoupleAndUser(
            @Param("coupleId") Long coupleId,
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    Optional<LetterMessage> findByIdAndCoupleId(Long id, Long coupleId);

    @Query("""
            select count(m) from LetterMessage m
            where m.coupleId = :coupleId
              and m.recipientId = :recipientId
              and m.readAt is null
              and m.deliverAt <= :now
            """)
    long countUnreadDelivered(
            @Param("coupleId") Long coupleId,
            @Param("recipientId") Long recipientId,
            @Param("now") LocalDateTime now);

    List<LetterMessage> findByScheduledTrueAndNotifiedAtIsNullAndDeliverAtLessThanEqualOrderByDeliverAtAsc(
            LocalDateTime now);
}
