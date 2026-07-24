package com.lovespace.repository;
import com.lovespace.domain.Notification;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdOrderByCreatedAtAsc(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
    int markAllReadForUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
