package com.steamfolder.steamfolder.domain.showcase.repository;

import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ShowcaseLikeRepository extends JpaRepository<ShowcaseLike, Long> {

    Optional<ShowcaseLike> findByUserAndTargetUser(User user, User targetUser);

    boolean existsByUserAndTargetUser(User user, User targetUser);

    boolean existsByUser_IdAndTargetUser_Id(Long userId, Long targetUserId);

    long countByTargetUser(User targetUser);

    void deleteByUserAndTargetUser(User user, User targetUser);

    @Query("SELECT sl.targetUser.id FROM ShowcaseLike sl WHERE sl.user.id = :userId AND sl.targetUser.id IN :targetUserIds")
    Set<Long> findTargetUserIdsByUserIdAndTargetUserIdIn(@Param("userId") Long userId, @Param("targetUserIds") List<Long> targetUserIds);

    @Query("SELECT COUNT(sl) FROM ShowcaseLike sl " +
           "WHERE sl.user = :user AND sl.targetUser.deletedAt IS NULL AND sl.targetUser.isPublic = true")
    long countActiveLikedShowcasesByUser(@Param("user") User user);

    @Query("SELECT sl FROM ShowcaseLike sl " +
           "JOIN FETCH sl.targetUser tu " +
           "WHERE sl.user = :user AND tu.deletedAt IS NULL AND tu.isPublic = true " +
           "ORDER BY sl.id DESC")
    List<ShowcaseLike> findFirstPageLikedShowcases(@Param("user") User user, Pageable pageable);

    @Query("SELECT sl FROM ShowcaseLike sl " +
           "JOIN FETCH sl.targetUser tu " +
           "WHERE sl.user = :user AND sl.id < :cursor AND tu.deletedAt IS NULL AND tu.isPublic = true " +
           "ORDER BY sl.id DESC")
    List<ShowcaseLike> findNextPageLikedShowcases(@Param("user") User user, @Param("cursor") Long cursor, Pageable pageable);
}
