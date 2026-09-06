package com.steamfolder.steamfolder.domain.user.repository;

import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.util.List;
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

    /**
     * No-Offset 커서 기반 인기 쇼케이스 랭킹 조회: 첫 페이지 (cursor == null)
     * 공개 상태(isPublic = true) 및 미탈퇴(deletedAt IS NULL) 유저 대상
     */
    @Query("SELECT u FROM User u WHERE u.isPublic = true AND u.deletedAt IS NULL " +
           "ORDER BY u.likeCount DESC, u.id ASC")
    List<User> findFirstPageShowcaseRankings(Pageable pageable);

    /**
     * No-Offset 커서 기반 인기 쇼케이스 랭킹 조회: 다음 페이지 (좋아요 수 & 유저 ID 복합 커서)
     */
    @Query("SELECT u FROM User u WHERE u.isPublic = true AND u.deletedAt IS NULL " +
           "AND (u.likeCount < :cursorLikeCount OR (u.likeCount = :cursorLikeCount AND u.id > :cursorUserId)) " +
           "ORDER BY u.likeCount DESC, u.id ASC")
    List<User> findNextPageShowcaseRankings(
            @Param("cursorLikeCount") int cursorLikeCount,
            @Param("cursorUserId") Long cursorUserId,
            Pageable pageable
    );

    /**
     * 커서 앞선 순위 유저 수 집계 (동적 순위 rank 계산용)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isPublic = true AND u.deletedAt IS NULL " +
           "AND (u.likeCount > :cursorLikeCount OR (u.likeCount = :cursorLikeCount AND u.id <= :cursorUserId))")
    long countShowcasesAheadOfCursor(
            @Param("cursorLikeCount") int cursorLikeCount,
            @Param("cursorUserId") Long cursorUserId
    );

    /**
     * 특정 유저보다 순위가 엄격히 앞선(높은) 공개 유저 수 집계 (본인 순위 myRank 계산용)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isPublic = true AND u.deletedAt IS NULL " +
           "AND (u.likeCount > :likeCount OR (u.likeCount = :likeCount AND u.id < :userId))")
    long countShowcasesStrictlyAhead(
            @Param("likeCount") int likeCount,
            @Param("userId") Long userId
    );
}
