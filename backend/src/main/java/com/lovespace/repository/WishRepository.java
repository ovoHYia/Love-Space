package com.lovespace.repository;
import com.lovespace.domain.Wish;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WishRepository extends JpaRepository<Wish, Long> {
    List<Wish> findByCoupleIdOrderByCreatedAtDesc(Long coupleId);
    Optional<Wish> findByIdAndCoupleId(Long id, Long coupleId);
}
