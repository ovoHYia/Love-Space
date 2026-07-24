package com.lovespace.repository;
import com.lovespace.domain.Diary;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DiaryRepository extends JpaRepository<Diary, Long> {
    List<Diary> findByCoupleIdAndDeletedAtIsNullOrderByDiaryDateDescCreatedAtDesc(Long coupleId);
    List<Diary> findTop4ByCoupleIdAndDeletedAtIsNullOrderByDiaryDateDescCreatedAtDesc(Long coupleId);
    List<Diary> findByCoupleIdAndAuthorIdAndDeletedAtIsNullOrderByDiaryDateDescCreatedAtDesc(Long coupleId, Long authorId);
    Optional<Diary> findByIdAndCoupleIdAndDeletedAtIsNull(Long id, Long coupleId);
    Optional<Diary> findByIdAndCoupleIdAndDeletedBy(Long id, Long coupleId, Long deletedBy);
    List<Diary> findByCoupleIdAndDeletedByOrderByDeletedAtDesc(Long coupleId, Long deletedBy);
    List<Diary> findByCoupleIdOrderById(Long coupleId);
}
