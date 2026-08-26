package com.steamfolder.steamfolder.domain.game.repository;

import com.steamfolder.steamfolder.domain.game.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByNameContainingIgnoreCase(String keyword);
}

