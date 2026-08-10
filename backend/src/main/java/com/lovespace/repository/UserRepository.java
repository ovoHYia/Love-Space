package com.lovespace.repository;
import com.lovespace.domain.User;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<User> findByCoupleIdOrderById(Long coupleId);
    List<User> findByIdInAndCoupleId(Collection<Long> ids, Long coupleId);
    Optional<User> findByIdAndCoupleId(Long id, Long coupleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.couple.id = :coupleId")
    Optional<User> findByIdAndCoupleIdForUpdate(@Param("id") Long id, @Param("coupleId") Long coupleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCaseForUpdate(@Param("username") String username);
}
