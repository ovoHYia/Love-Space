package com.lovespace.repository;
import com.lovespace.domain.Wish;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WishRepository extends JpaRepository<Wish, Long> {
    List<Wish> findByCoupleIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long coupleId);
    Optional<Wish> findByIdAndCoupleIdAndDeletedAtIsNull(Long id, Long coupleId);
    Optional<Wish> findByIdAndCoupleIdAndDeletedBy(Long id, Long coupleId, Long deletedBy);
    List<Wish> findByCoupleIdAndDeletedByOrderByDeletedAtDesc(Long coupleId, Long deletedBy);
    List<Wish> findByCoupleIdOrderById(Long coupleId);
    List<Wish> findByCoupleIdAndDeletedAtIsNullAndTargetDateBetweenOrderByTargetDate(
            Long coupleId, java.time.LocalDate from, java.time.LocalDate to);
}
