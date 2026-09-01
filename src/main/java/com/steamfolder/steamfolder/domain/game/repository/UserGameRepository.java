package com.steamfolder.steamfolder.domain.game.repository;

import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserGameRepository extends JpaRepository<UserGame, Long> {

    Optional<UserGame> findByUserAndGame(User user, Game game);

    @EntityGraph(attributePaths = {"game"})
    List<UserGame> findByUserOrderByPlaytimeForeverDesc(User user);

    List<UserGame> findByUser(User user);

    boolean existsByUserAndGame(User user, Game game);

    long countByUser(User user);

    @Query("SELECT COALESCE(SUM(ug.playtimeForever), 0) FROM UserGame ug WHERE ug.user = :user")
    int sumPlaytimeByUser(@Param("user") User user);

    void deleteByUser(User user);

    /**
     * No-Offset 커서 기반 조회: 첫 페이지 (cursor == null)
     */
    @EntityGraph(attributePaths = {"game"})
    @Query("SELECT ug FROM UserGame ug WHERE ug.user = :user ORDER BY ug.playtimeForever DESC, ug.id DESC")
    List<UserGame> findFirstPageByUserOrderByPlaytimeDesc(@Param("user") User user, Pageable pageable);

    /**
     * No-Offset 커서 기반 조회: 다음 페이지 (플레이타임 & ID 복합 커서)
     */
    @EntityGraph(attributePaths = {"game"})
    @Query("SELECT ug FROM UserGame ug WHERE ug.user = :user " +
           "AND (ug.playtimeForever < :cursorPlaytime OR (ug.playtimeForever = :cursorPlaytime AND ug.id < :cursorId)) " +
           "ORDER BY ug.playtimeForever DESC, ug.id DESC")
    List<UserGame> findNextPageByUserOrderByPlaytimeDesc(
            @Param("user") User user,
            @Param("cursorPlaytime") int cursorPlaytime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    /**
     * 단순 ID 커서 기반 조회
     */
    @EntityGraph(attributePaths = {"game"})
    List<UserGame> findByUserAndIdLessThanOrderByIdDesc(User user, Long cursor, Pageable pageable);

    @EntityGraph(attributePaths = {"game"})
    List<UserGame> findByUserOrderByIdDesc(User user, Pageable pageable);
}
