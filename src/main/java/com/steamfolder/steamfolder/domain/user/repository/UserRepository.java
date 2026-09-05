package com.steamfolder.steamfolder.domain.user.repository;

import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findBySteamId(String steamId);

    Optional<User> findByUsername(String username);

    boolean existsBySteamId(String steamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdWithPessimisticLock(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.likeCount = u.likeCount + 1 WHERE u.id = :userId")
    void incrementLikeCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.likeCount = CASE WHEN u.likeCount > 0 THEN u.likeCount - 1 ELSE 0 END WHERE u.id = :userId")
    void decrementLikeCount(@Param("userId") Long userId);

    @Query("SELECT u.likeCount FROM User u WHERE u.id = :userId")
    int getLikeCount(@Param("userId") Long userId);
}
