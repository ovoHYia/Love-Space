package com.lovespace.repository;
import com.lovespace.domain.Memory;
import org.springframework.data.jpa.repository.*;
public interface MemoryRepository extends JpaRepository<Memory, Long>, JpaSpecificationExecutor<Memory> {
    java.util.Optional<Memory> findByIdAndCoupleIdAndDeletedAtIsNull(Long id, Long coupleId);
    java.util.Optional<Memory> findByIdAndCoupleIdAndDeletedBy(Long id, Long coupleId, Long deletedBy);
    java.util.List<Memory> findByCoupleIdAndDeletedByOrderByDeletedAtDesc(Long coupleId, Long deletedBy);
    java.util.List<Memory> findByCoupleIdOrderById(Long coupleId);
}
