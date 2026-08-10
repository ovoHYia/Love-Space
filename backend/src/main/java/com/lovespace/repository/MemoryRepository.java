package com.lovespace.repository;
import com.lovespace.domain.Memory;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface MemoryRepository extends JpaRepository<Memory, Long>, JpaSpecificationExecutor<Memory> {
    java.util.Optional<Memory> findByIdAndCoupleIdAndDeletedAtIsNull(Long id, Long coupleId);
    java.util.Optional<Memory> findByIdAndCoupleIdAndDeletedBy(Long id, Long coupleId, Long deletedBy);
    java.util.List<Memory> findByCoupleIdAndDeletedByOrderByDeletedAtDesc(Long coupleId, Long deletedBy);
    java.util.List<Memory> findByCoupleIdOrderById(Long coupleId);
    java.util.List<Memory> findByCoupleIdAndDeletedAtIsNullOrderByEventAtDesc(Long coupleId);
    java.util.List<Memory> findByCoupleIdAndDeletedAtIsNullAndEventAtGreaterThanEqualAndEventAtLessThanOrderByEventAt(
            Long coupleId, java.time.LocalDateTime startInclusive, java.time.LocalDateTime endExclusive);

    @Query(value = """
            select * from memories
            where couple_id = :coupleId
              and deleted_at is null
              and (:excludeId is null or id <> :excludeId)
            order by rand()
            limit 1
            """, nativeQuery = true)
    java.util.Optional<Memory> findRandomActive(
            @Param("coupleId") Long coupleId, @Param("excludeId") Long excludeId);

    @Query("""
            select min(tag) as name, count(distinct memory.id) as memoryCount
            from Memory memory join memory.tags tag
            where memory.coupleId = :coupleId and memory.deletedAt is null
            group by lower(tag)
            order by count(distinct memory.id) desc, lower(min(tag)) asc
            """)
    List<MemoryTagCountProjection> aggregateActiveTags(Long coupleId);
}
