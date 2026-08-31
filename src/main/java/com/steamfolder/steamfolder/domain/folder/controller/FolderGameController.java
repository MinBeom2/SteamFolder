package com.steamfolder.steamfolder.domain.folder.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.domain.folder.dto.request.AddFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.RemoveFolderGamesRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderGameDetailResponse;
import com.steamfolder.steamfolder.domain.folder.service.FolderGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/folders/{folderId}/games")
@RequiredArgsConstructor
public class FolderGameController {

    private final FolderGameService folderGameService;

    /**
     * 폴더 내 게임 추가 및 단일 복사 API (동일 게임 다중 복제 지원)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FolderGameDetailResponse>> addGameToFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @Valid @RequestBody AddFolderGameRequest request
    ) {
        FolderGameDetailResponse response = folderGameService.addGameToFolder(principal.getUserId(), folderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("게임이 폴더에 성공적으로 추가되었습니다.", response));
    }

    /**
     * 폴더 내 게임 여러 개 일괄 추가 및 복사 붙여넣기 API
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<java.util.List<FolderGameDetailResponse>>> batchAddGamesToFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @Valid @RequestBody com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest request
    ) {
        java.util.List<FolderGameDetailResponse> response = folderGameService.batchAddGamesToFolder(principal.getUserId(), folderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("게임들이 폴더에 성공적으로 추가되었습니다.", response));
    }

    /**
     * 폴더 내 특정 게임 인스턴스(folderGameId) 단일 삭제 API
     */
    @DeleteMapping("/{folderGameId}")
    public ResponseEntity<ApiResponse<Void>> removeFolderGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @PathVariable Long folderGameId
    ) {
        folderGameService.removeFolderGame(principal.getUserId(), folderId, folderGameId);
        return ResponseEntity.ok(ApiResponse.successWithMessage("게임이 폴더에서 성공적으로 제거되었습니다."));
    }

    /**
     * 폴더 내 선택한 게임 인스턴스들(folderGameIds) 일괄 삭제 API
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> removeFolderGames(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @Valid @RequestBody RemoveFolderGamesRequest request
    ) {
        folderGameService.removeFolderGames(principal.getUserId(), folderId, request);
        return ResponseEntity.ok(ApiResponse.successWithMessage("선택한 게임들이 폴더에서 성공적으로 제거되었습니다."));
    }

    /**
     * 폴더 간 게임 이동(잘라내기/붙여넣기) API
     */
    @PatchMapping("/{folderGameId}/move")
    public ResponseEntity<ApiResponse<FolderGameDetailResponse>> moveGameToAnotherFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @PathVariable Long folderGameId,
            @Valid @RequestBody MoveFolderGameRequest request
    ) {
        FolderGameDetailResponse response = folderGameService.moveGameToAnotherFolder(principal.getUserId(), folderId, folderGameId, request);
        return ResponseEntity.ok(ApiResponse.success("게임이 대상 폴더로 성공적으로 이동되었습니다.", response));
    }

    /**
     * 폴더 간 게임 여러 개 일괄 이동(잘라내기/붙여넣기) API
     */
    @PatchMapping("/move")
    public ResponseEntity<ApiResponse<java.util.List<FolderGameDetailResponse>>> batchMoveFolderGames(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @Valid @RequestBody com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest request
    ) {
        java.util.List<FolderGameDetailResponse> response = folderGameService.batchMoveFolderGames(principal.getUserId(), folderId, request);
        return ResponseEntity.ok(ApiResponse.success("선택한 게임들이 대상 폴더로 성공적으로 이동되었습니다.", response));
    }
}
