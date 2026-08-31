package com.steamfolder.steamfolder.domain.folder.repository;

import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    // 유저의 루트 폴더 목록 (2D 그리드 Y -> X 순서 정렬)
    @EntityGraph(attributePaths = {"folderGames", "folderGames.game"})
    List<Folder> findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(User user);

    // 유저의 특정 부모 폴더 하위 자식 폴더 목록
    @EntityGraph(attributePaths = {"folderGames", "folderGames.game"})
    List<Folder> findByUserAndParentFolderOrderByPositionYAscPositionXAsc(User user, Folder parentFolder);

    // 유저의 전체 폴더 목록
    List<Folder> findByUserOrderByPositionYAscPositionXAsc(User user);

    // 동일 경로(동일 부모 폴더) 내 동일 이름 존재 여부 확인
    boolean existsByUserAndParentFolderAndName(User user, Folder parentFolder, String name);

    // 루트 레벨에서 동일 이름 존재 여부 확인
    boolean existsByUserAndParentFolderIsNullAndName(User user, String name);

    // 특정 유저의 폴더 개수 확인
    long countByUser(User user);

    // 유저 탈퇴 시 연동 폴더 일괄 삭제
    void deleteByUser(User user);
}
