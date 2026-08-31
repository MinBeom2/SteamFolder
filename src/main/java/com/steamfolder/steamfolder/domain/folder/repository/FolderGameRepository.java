package com.steamfolder.steamfolder.domain.folder.repository;

import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderGameRepository extends JpaRepository<FolderGame, Long> {

    @EntityGraph(attributePaths = {"game"})
    List<FolderGame> findByFolderOrderByPositionYAscPositionXAsc(Folder folder);

    Optional<FolderGame> findByFolderAndGame(Folder folder, Game game);

    boolean existsByFolderAndGame(Folder folder, Game game);

    void deleteByFolderAndGame(Folder folder, Game game);

    void deleteByFolder(Folder folder);
}
