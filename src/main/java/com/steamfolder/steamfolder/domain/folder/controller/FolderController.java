package com.steamfolder.steamfolder.domain.folder.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.domain.folder.dto.request.CreateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdatePositionsRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /**
     * 폴더 생성 API
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateFolderRequest request
    ) {
        FolderResponse response = folderService.createFolder(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("폴더가 성공적으로 생성되었습니다.", response));
    }

    /**
     * 내 전체 폴더 계층 트리 목록 조회 API
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getMyFolders(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<FolderResponse> response = folderService.getMyFolderHierarchy(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특정 폴더 상세 조회 API (본인 소유이거나 공개 유저의 폴더인 경우 게스트/타인 조회 허용)
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> getFolderDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId
    ) {
        Long userId = (principal != null) ? principal.getUserId() : null;
        FolderResponse response = folderService.getFolderDetail(userId, folderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 폴더 정보(이름, 색상, 아이콘) 수정 API
     */
    @PatchMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> updateFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @Valid @RequestBody UpdateFolderRequest request
    ) {
        FolderResponse response = folderService.updateFolder(principal.getUserId(), folderId, request);
        return ResponseEntity.ok(ApiResponse.success("폴더 정보가 성공적으로 수정되었습니다.", response));
    }

    /**
     * 폴더 이동(부모 변경, 2D 위치 변경, 순환 참조 방지) API
     */
    @PatchMapping("/{folderId}/move")
    public ResponseEntity<ApiResponse<FolderResponse>> moveFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId,
            @RequestBody MoveFolderRequest request
    ) {
        FolderResponse response = folderService.moveFolder(principal.getUserId(), folderId, request);
        return ResponseEntity.ok(ApiResponse.success("폴더가 성공적으로 이동되었습니다.", response));
    }

    /**
     * 2D 격자 좌표 일괄 업데이트(드래그 앤 드롭) API
     */
    @PatchMapping("/positions")
    public ResponseEntity<ApiResponse<Void>> updatePositions(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePositionsRequest request
    ) {
        folderService.updatePositions(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.successWithMessage("아이템 위치가 성공적으로 업데이트되었습니다."));
    }

    /**
     * 폴더 삭제 API (하위 자식 폴더 연쇄 삭제)
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long folderId
    ) {
        folderService.deleteFolder(principal.getUserId(), folderId);
        return ResponseEntity.ok(ApiResponse.successWithMessage("폴더가 성공적으로 삭제되었습니다."));
    }
}
