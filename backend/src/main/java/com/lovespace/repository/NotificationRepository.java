package com.lovespace.repository;
import com.lovespace.domain.Notification;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtAsc(Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    long countByUserIdAndReferenceType(Long userId, String referenceType);
    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (:status = 'ALL'
                   or (:status = 'UNREAD' and n.readAt is null)
                   or (:status = 'READ' and n.readAt is not null))
              and (:category = 'ALL' or n.referenceType = :category)
              and (:keyword = ''
                   or lower(n.title) like lower(concat('%', :keyword, '%'))
                   or lower(n.body) like lower(concat('%', :keyword, '%')))
            order by n.createdAt desc, n.id desc
            """)
    Page<Notification> search(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("category") String category,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.userId = :userId
              and (:status = 'ALL'
                   or (:status = 'UNREAD' and n.readAt is null)
                   or (:status = 'READ' and n.readAt is not null))
              and (:category = 'ALL' or n.referenceType = :category)
              and (:keyword = ''
                   or lower(n.title) like lower(concat('%', :keyword, '%'))
                   or lower(n.body) like lower(concat('%', :keyword, '%')))
            """)
    long countSearch(@Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("category") String category,
                     @Param("keyword") String keyword);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
    int markAllReadForUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.userId = :userId and n.id in :ids")
    int markReadForUser(
            @Param("userId") Long userId,
            @Param("ids") Collection<Long> ids,
            @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("update Notification n set n.readAt = null where n.userId = :userId and n.id in :ids")
    int markUnreadForUser(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    long deleteByUserIdAndIdIn(Long userId, Collection<Long> ids);
    long deleteByUserIdAndReadAtIsNotNull(Long userId);
}
