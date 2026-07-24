package com.lovespace.repository;
import com.lovespace.domain.Mood;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MoodRepository extends JpaRepository<Mood, Long> {
    Optional<Mood> findByUserIdAndMoodDate(Long userId, LocalDate date);
    List<Mood> findByCoupleIdAndMoodDateOrderByUserId(Long coupleId, LocalDate date);
    List<Mood> findByCoupleIdOrderByMoodDateAscUserIdAsc(Long coupleId);
}
