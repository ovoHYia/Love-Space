package com.lovespace.repository;
import com.lovespace.domain.LetterMessage;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LetterMessageRepository extends JpaRepository<LetterMessage, Long> {
    List<LetterMessage> findByCoupleIdOrderByCreatedAtDesc(Long coupleId);
    List<LetterMessage> findTop4ByCoupleIdOrderByCreatedAtDesc(Long coupleId);
    Page<LetterMessage> findByCoupleId(Long coupleId, Pageable pageable);
    Optional<LetterMessage> findByIdAndCoupleId(Long id, Long coupleId);
    long countByCoupleIdAndRecipientIdAndReadAtIsNull(Long coupleId, Long recipientId);
}
