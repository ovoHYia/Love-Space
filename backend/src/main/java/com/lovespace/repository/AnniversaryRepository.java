package com.lovespace.repository;
import com.lovespace.domain.Anniversary;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {
    List<Anniversary> findByCoupleIdAndDeletedAtIsNullOrderByEventDateAsc(Long coupleId);
    Optional<Anniversary> findByIdAndCoupleIdAndDeletedAtIsNull(Long id, Long coupleId);
    Optional<Anniversary> findByIdAndCoupleIdAndDeletedBy(Long id, Long coupleId, Long deletedBy);
    List<Anniversary> findByCoupleIdAndDeletedByOrderByDeletedAtDesc(Long coupleId, Long deletedBy);
    List<Anniversary> findByCoupleIdOrderById(Long coupleId);
    List<Anniversary> findByDeletedAtIsNull();
}
