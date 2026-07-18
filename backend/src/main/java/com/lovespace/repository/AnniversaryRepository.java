package com.lovespace.repository;
import com.lovespace.domain.Anniversary;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {
    List<Anniversary> findByCoupleIdOrderByEventDateAsc(Long coupleId);
    Optional<Anniversary> findByIdAndCoupleId(Long id, Long coupleId);
}
