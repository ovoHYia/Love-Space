package com.lovespace.repository;
import com.lovespace.domain.Memory;
import org.springframework.data.jpa.repository.*;
public interface MemoryRepository extends JpaRepository<Memory, Long>, JpaSpecificationExecutor<Memory> {
    java.util.Optional<Memory> findByIdAndCoupleId(Long id, Long coupleId);
}
