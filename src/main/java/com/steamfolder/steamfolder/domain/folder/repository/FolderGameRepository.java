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

    List<FolderGame> findByFolderAndGame(Folder folder, Game game);

    Optional<FolderGame> findByIdAndFolder(Long id, Folder folder);

    long countByFolder(Folder folder);

    boolean existsByFolderAndPositionXAndPositionY(Folder folder, int positionX, int positionY);

    void deleteByFolderAndGame(Folder folder, Game game);

    void deleteByFolder(Folder folder);
}
