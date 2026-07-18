package com.lovespace.repository;
import com.lovespace.domain.User;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<User> findByCoupleIdOrderById(Long coupleId);
    List<User> findByIdInAndCoupleId(Collection<Long> ids, Long coupleId);
    Optional<User> findByIdAndCoupleId(Long id, Long coupleId);
}
