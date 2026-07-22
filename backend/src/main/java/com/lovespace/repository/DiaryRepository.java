package com.lovespace.repository;
import com.lovespace.domain.Diary;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DiaryRepository extends JpaRepository<Diary, Long> {
    List<Diary> findByCoupleIdOrderByDiaryDateDescCreatedAtDesc(Long coupleId);
    List<Diary> findTop4ByCoupleIdOrderByDiaryDateDescCreatedAtDesc(Long coupleId);
    List<Diary> findByCoupleIdAndAuthorIdOrderByDiaryDateDescCreatedAtDesc(Long coupleId, Long authorId);
    Optional<Diary> findByIdAndCoupleId(Long id, Long coupleId);
}
