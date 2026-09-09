package com.steamfolder.steamfolder.domain.folder.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.request.AddFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.RemoveFolderGamesRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderGameDetailResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.game.service.GameMetadataEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderGameService {

    public static final int MAX_GAMES_PER_FOLDER = 102;

    private final FolderRepository folderRepository;
    private final FolderGameRepository folderGameRepository;
    private final GameRepository gameRepository;
    private final FolderService folderService;
    private final GameMetadataEnrichmentService gameMetadataEnrichmentService;

    /**
     * 폴더 내 게임 추가 및 복사 (동일 게임 다중 복제 지원, 최대 100개 제한, 2D 스마트 빈 칸 자동 탐색)
     */
    @Transactional
    public FolderGameDetailResponse addGameToFolder(Long userId, Long folderId, AddFolderGameRequest request) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);

        // 1. 폴더당 최대 게임 개수(100개) 제한 검증 (무한 증식 폭탄 방어)
        if (folderGameRepository.countByFolder(folder) >= MAX_GAMES_PER_FOLDER) {
            throw new CustomException(ErrorCode.MAX_FOLDER_GAME_COUNT_EXCEEDED);
        }

        Game game = findOrCreateGame(request.getAppId());
        if (game.getGenres() == null || game.getGenres().isEmpty()) {
            gameMetadataEnrichmentService.enrichGameMetadataAsync(List.of(game.getAppId()));
        }

        // 2. 2D 격자 좌표 결정 (미지정 또는 겹침 발생 시 스마트 빈 칸 자동 탐색)
        int posX;
        int posY;
        if (request.getPositionX() == null || request.getPositionY() == null
                || folderGameRepository.existsByFolderAndPositionXAndPositionY(folder, request.getPositionX(), request.getPositionY())) {
            int[] availablePos = folderService.findNextAvailablePosition(folder.getUser(), folder, FolderService.DEFAULT_GRID_COLS);
            posX = availablePos[0];
            posY = availablePos[1];
        } else {
            posX = request.getPositionX();
            posY = request.getPositionY();
        }

        FolderGame folderGame = FolderGame.builder()
                .folder(folder)
                .game(game)
                .positionX(posX)
                .positionY(posY)
                .build();

        FolderGame saved = folderGameRepository.save(folderGame);
        return FolderGameDetailResponse.from(saved);
    }

    /**
     * 폴더 내 게임 여러 개 일괄 추가/복사 붙여넣기 (다중 복제 지원 & 102개 제한 사전 방어)
     */
    @Transactional
    public List<FolderGameDetailResponse> batchAddGamesToFolder(Long userId, Long folderId, BatchAddFolderGamesRequest request) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);

        // 1. 최대 게임 개수(102개) 초과 사전 검증
        if (folderGameRepository.countByFolder(folder) + request.getAppIds().size() > MAX_GAMES_PER_FOLDER) {
            throw new CustomException(ErrorCode.MAX_FOLDER_GAME_COUNT_EXCEEDED);
        }

        List<FolderGameDetailResponse> addedGames = new ArrayList<>();
        List<Long> appIdsNeedingEnrichment = new ArrayList<>();

        for (Long appId : request.getAppIds()) {
            Game game = findOrCreateGame(appId);
            if (game.getGenres() == null || game.getGenres().isEmpty()) {
                appIdsNeedingEnrichment.add(appId);
            }

            int[] availablePos = folderService.findNextAvailablePosition(folder.getUser(), folder, FolderService.DEFAULT_GRID_COLS);

            FolderGame folderGame = FolderGame.builder()
                    .folder(folder)
                    .game(game)
                    .positionX(availablePos[0])
                    .positionY(availablePos[1])
                    .build();

            FolderGame saved = folderGameRepository.save(folderGame);
            addedGames.add(FolderGameDetailResponse.from(saved));
        }

        if (!appIdsNeedingEnrichment.isEmpty()) {
            gameMetadataEnrichmentService.enrichGameMetadataAsync(appIdsNeedingEnrichment);
        }

        return addedGames;
    }

    /**
     * 폴더 내 특정 게임 배치 인스턴스(folderGameId) 단일 삭제
     */
    @Transactional
    public void removeFolderGame(Long userId, Long folderId, Long folderGameId) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);
        FolderGame folderGame = folderGameRepository.findByIdAndFolder(folderGameId, folder)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        folderGameRepository.delete(folderGame);
    }

    /**
     * 폴더 내 선택한 게임 배치 인스턴스들(folderGameIds) 일괄 삭제
     */
    @Transactional
    public void removeFolderGames(Long userId, Long folderId, RemoveFolderGamesRequest request) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);

        for (Long folderGameId : request.getFolderGameIds()) {
            Optional<FolderGame> folderGameOpt = folderGameRepository.findByIdAndFolder(folderGameId, folder);
            folderGameOpt.ifPresent(folderGameRepository::delete);
        }
    }

    /**
     * 폴더 간 게임 이동 (출발지 인스턴스 삭제 ➡️ 대상 폴더에 2D 격자 배치)
     */
    @Transactional
    public FolderGameDetailResponse moveGameToAnotherFolder(Long userId, Long sourceFolderId, Long folderGameId, MoveFolderGameRequest request) {
        Folder sourceFolder = findFolderByIdAndValidateOwnership(sourceFolderId, userId);
        Folder targetFolder = findFolderByIdAndValidateOwnership(request.getTargetFolderId(), userId);

        // 대상 폴더 최대 개수(100개) 제한 검증
        if (folderGameRepository.countByFolder(targetFolder) >= MAX_GAMES_PER_FOLDER) {
            throw new CustomException(ErrorCode.MAX_FOLDER_GAME_COUNT_EXCEEDED);
        }

        // 출발지 폴더의 특정 배치 인스턴스 조회
        FolderGame sourceFolderGame = folderGameRepository.findByIdAndFolder(folderGameId, sourceFolder)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        Game game = sourceFolderGame.getGame();

        // 대상 폴더 2D 격자 좌표 결정
        int targetX;
        int targetY;
        if (request.getTargetPositionX() == null || request.getTargetPositionY() == null
                || folderGameRepository.existsByFolderAndPositionXAndPositionY(targetFolder, request.getTargetPositionX(), request.getTargetPositionY())) {
            int[] availablePos = folderService.findNextAvailablePosition(targetFolder.getUser(), targetFolder, FolderService.DEFAULT_GRID_COLS);
            targetX = availablePos[0];
            targetY = availablePos[1];
        } else {
            targetX = request.getTargetPositionX();
            targetY = request.getTargetPositionY();
        }

        // 출발지에서 삭제 후 대상에 생성
        folderGameRepository.delete(sourceFolderGame);

        FolderGame newFolderGame = FolderGame.builder()
                .folder(targetFolder)
                .game(game)
                .positionX(targetX)
                .positionY(targetY)
                .build();

        FolderGame saved = folderGameRepository.save(newFolderGame);
        return FolderGameDetailResponse.from(saved);
    }

    /**
     * 폴더 간 게임 여러 개 일괄 이동 (잘라내기 ➡️ 대상 폴더에 2D 스마트 빈 칸 순차 안착)
     */
    @Transactional
    public List<FolderGameDetailResponse> batchMoveFolderGames(Long userId, Long sourceFolderId, BatchMoveFolderGamesRequest request) {
        Folder sourceFolder = findFolderByIdAndValidateOwnership(sourceFolderId, userId);
        Folder targetFolder = findFolderByIdAndValidateOwnership(request.getTargetFolderId(), userId);

        // 대상 폴더 최대 개수(102개) 초과 사전 검증
        if (folderGameRepository.countByFolder(targetFolder) + request.getFolderGameIds().size() > MAX_GAMES_PER_FOLDER) {
            throw new CustomException(ErrorCode.MAX_FOLDER_GAME_COUNT_EXCEEDED);
        }

        List<FolderGameDetailResponse> movedGames = new java.util.ArrayList<>();

        for (Long folderGameId : request.getFolderGameIds()) {
            Optional<FolderGame> sourceFolderGameOpt = folderGameRepository.findByIdAndFolder(folderGameId, sourceFolder);
            if (sourceFolderGameOpt.isPresent()) {
                FolderGame sourceFolderGame = sourceFolderGameOpt.get();
                Game game = sourceFolderGame.getGame();

                // 대상 폴더의 다음 빈 칸 탐색
                int[] availablePos = folderService.findNextAvailablePosition(targetFolder.getUser(), targetFolder, FolderService.DEFAULT_GRID_COLS);

                // 출발지에서 삭제
                folderGameRepository.delete(sourceFolderGame);

                // 대상 폴더에 신규 생성
                FolderGame newFolderGame = FolderGame.builder()
                        .folder(targetFolder)
                        .game(game)
                        .positionX(availablePos[0])
                        .positionY(availablePos[1])
                        .build();

                FolderGame saved = folderGameRepository.save(newFolderGame);
                movedGames.add(FolderGameDetailResponse.from(saved));
            }
        }

        return movedGames;
    }

    private Game findOrCreateGame(Long appId) {
        return gameRepository.findById(appId).orElseGet(() -> {
            Game defaultGame = Game.builder()
                    .appId(appId)
                    .name("App " + appId)
                    .headerImage("https://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header.jpg")
                    .heroImage("https://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/library_hero.jpg")
                    .genres(new ArrayList<>())
                    .developer("")
                    .lastSyncedAt(LocalDateTime.now())
                    .build();
            return gameRepository.save(defaultGame);
        });
    }

    private Folder findFolderByIdAndValidateOwnership(Long folderId, Long userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }
        return folder;
    }
}
