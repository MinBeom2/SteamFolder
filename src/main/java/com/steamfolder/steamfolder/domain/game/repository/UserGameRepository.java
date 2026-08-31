package com.steamfolder.steamfolder.domain.game.repository;

import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.user.entity.User;
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
}
