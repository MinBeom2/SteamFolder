package com.steamfolder.steamfolder.domain.folder.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.request.CreateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdatePositionsRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderService {

    public static final int MAX_DEPTH = 5;
    public static final int MAX_FOLDER_COUNT = 100;
    public static final int DEFAULT_GRID_COLS = 6;

    private final FolderRepository folderRepository;
    private final FolderGameRepository folderGameRepository;
    private final UserRepository userRepository;

    /**
     * 폴더 생성 (2D 격자 좌표 지정 또는 자동 빈 칸 탐색)
     */
    @Transactional
    public FolderResponse createFolder(Long userId, CreateFolderRequest request) {
        User user = findUserById(userId);

        // 1. 최대 폴더 생성 개수(100개) 제한
        if (folderRepository.countByUser(user) >= MAX_FOLDER_COUNT) {
            throw new CustomException(ErrorCode.MAX_FOLDER_COUNT_EXCEEDED);
        }

        // 2. 부모 폴더 조회 및 깊이 계산
        Folder parentFolder = null;
        int depth = 0;

        if (request.getParentFolderId() != null) {
            parentFolder = findFolderByIdAndValidateOwnership(request.getParentFolderId(), userId);
            depth = parentFolder.getDepth() + 1;
            if (depth >= MAX_DEPTH) {
                throw new CustomException(ErrorCode.MAX_FOLDER_DEPTH_EXCEEDED);
            }
        }

        // 3. 동일 경로 내 폴더명 중복 검증
        validateDuplicateFolderName(user, parentFolder, request.getName(), null);

        // 4. 2D 격자 좌표 결정 (미지정 시 스마트 빈 칸 자동 탐색)
        int posX;
        int posY;
        if (request.getPositionX() == null || request.getPositionY() == null) {
            int[] availablePos = findNextAvailablePosition(user, parentFolder, DEFAULT_GRID_COLS);
            posX = availablePos[0];
            posY = availablePos[1];
        } else {
            posX = request.getPositionX();
            posY = request.getPositionY();
        }

        // 5. 엔티티 생성 및 저장
        Folder folder = Folder.builder()
                .user(user)
                .parentFolder(parentFolder)
                .name(request.getName())
                .themeColor(request.getThemeColor() != null ? request.getThemeColor() : "#1A1B26")
                .icon(request.getIcon() != null ? request.getIcon() : "folder")
                .positionX(posX)
                .positionY(posY)
                .depth(depth)
                .build();

        Folder savedFolder = folderRepository.save(folder);
        return FolderResponse.from(savedFolder);
    }

    /**
     * 폴더 정보(이름, 테마색상, 아이콘) 수정
     */
    @Transactional
    public FolderResponse updateFolder(Long userId, Long folderId, UpdateFolderRequest request) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);

        if (request.getName() != null && !request.getName().isBlank() && !request.getName().equals(folder.getName())) {
            validateDuplicateFolderName(folder.getUser(), folder.getParentFolder(), request.getName(), folderId);
        }

        folder.updateFolder(request.getName(), request.getThemeColor(), request.getIcon());
        return FolderResponse.from(folder);
    }

    /**
     * 폴더 이동 (부모 변경, 2D 좌표 변경, 깊이 재계산 및 순환 참조 방지)
     */
    @Transactional
    public FolderResponse moveFolder(Long userId, Long folderId, MoveFolderRequest request) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);

        Folder targetParent = null;
        int newDepth = 0;

        if (request.getTargetParentFolderId() != null) {
            targetParent = findFolderByIdAndValidateOwnership(request.getTargetParentFolderId(), userId);

            // 순환 참조(Circular Reference) 검증: 자기 자신이거나 자신의 하위 자손인지 검사
            if (isDescendantOf(targetParent, folderId)) {
                throw new CustomException(ErrorCode.CIRCULAR_FOLDER_REFERENCE);
            }

            newDepth = targetParent.getDepth() + 1;
            int maxSubtreeDepth = calculateMaxSubtreeDepth(folder);
            if (newDepth + maxSubtreeDepth >= MAX_DEPTH) {
                throw new CustomException(ErrorCode.MAX_FOLDER_DEPTH_EXCEEDED);
            }
        }

        // 동일 부모 내 동일 이름 중복 검증
        validateDuplicateFolderName(folder.getUser(), targetParent, folder.getName(), folderId);

        // 2D 격자 위치 결정
        int targetX;
        int targetY;
        if (request.getTargetPositionX() == null || request.getTargetPositionY() == null) {
            int[] availablePos = findNextAvailablePosition(folder.getUser(), targetParent, DEFAULT_GRID_COLS);
            targetX = availablePos[0];
            targetY = availablePos[1];
        } else {
            targetX = request.getTargetPositionX();
            targetY = request.getTargetPositionY();
        }

        // 이동 및 하위 자식들의 depth 일괄 갱신
        folder.moveFolder(targetParent, newDepth, targetX, targetY);
        updateChildDepthsRecursively(folder, newDepth);

        return FolderResponse.from(folder);
    }

    /**
     * 폴더 삭제 (Cascade로 하위 자식 폴더 및 게임 매핑 연쇄 삭제)
     */
    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        Folder folder = findFolderByIdAndValidateOwnership(folderId, userId);
        folderRepository.delete(folder);
    }

    /**
     * 내 전체 폴더 계층 목록 조회 (루트 폴더 + 각 루트의 직속 1단계 자식 정보)
     */
    @Transactional(readOnly = true)
    public List<FolderResponse> getMyFolderHierarchy(Long userId) {
        User user = findUserById(userId);
        List<Folder> rootFolders = folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(user);
        return rootFolders.stream()
                .map(FolderResponse::from)
                .toList();
    }

    /**
     * 특정 폴더 상세 조회 (직속 1단계 하위 폴더 및 포함된 게임 목록)
     * 본인 소유이거나, 소유자가 공개(isPublic=true) 프로필인 경우 비회원/타인 조회 허용
     */
    @Transactional(readOnly = true)
    public FolderResponse getFolderDetail(Long userId, Long folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        // 1. 본인 소유 폴더인 경우 즉시 허용
        if (userId != null && folder.getUser().getId().equals(userId)) {
            return FolderResponse.from(folder);
        }

        // 2. 비로그인이거나 타인인 경우, 폴더 소유자의 프로필 공개 여부(isPublic) 검증
        if (!folder.getUser().isPublic() || folder.getUser().isDeleted()) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        return FolderResponse.from(folder);
    }

    /**
     * 2D 격자 좌표 일괄 업데이트 (드래그 앤 드롭 위치 저장)
     */
    @Transactional
    public void updatePositions(Long userId, UpdatePositionsRequest request) {
        for (UpdatePositionsRequest.ItemPositionDto item : request.getPositions()) {
            if ("FOLDER".equalsIgnoreCase(item.getType())) {
                Folder folder = findFolderByIdAndValidateOwnership(item.getId(), userId);
                folder.updatePosition(item.getPositionX(), item.getPositionY());
            } else if ("GAME".equalsIgnoreCase(item.getType())) {
                FolderGame folderGame = folderGameRepository.findById(item.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
                if (!folderGame.getFolder().getUser().getId().equals(userId)) {
                    throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
                }
                folderGame.updatePosition(item.getPositionX(), item.getPositionY());
            }
        }
    }

    /**
     * 현재 화면(부모 폴더)에서 가장 먼저 비어있는 2D 격자 좌표 (x, y) 자동 탐색
     */
    public int[] findNextAvailablePosition(User user, Folder parentFolder, int gridCols) {
        Set<String> occupiedSlots = new HashSet<>();

        // 1. 부모 폴더 내 자식 폴더들의 좌표 수집
        List<Folder> folders = parentFolder == null
                ? folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(user)
                : folderRepository.findByUserAndParentFolderOrderByPositionYAscPositionXAsc(user, parentFolder);

        for (Folder f : folders) {
            occupiedSlots.add(f.getPositionX() + "," + f.getPositionY());
        }

        // 2. 부모 폴더 내 배치된 게임들의 좌표 수집
        if (parentFolder != null) {
            List<FolderGame> games = folderGameRepository.findByFolderOrderByPositionYAscPositionXAsc(parentFolder);
            for (FolderGame fg : games) {
                occupiedSlots.add(fg.getPositionX() + "," + fg.getPositionY());
            }
        }

        // 3. (0,0)부터 행 우선 탐색하여 최초 빈 칸 반환
        int y = 0;
        while (true) {
            for (int x = 0; x < gridCols; x++) {
                if (!occupiedSlots.contains(x + "," + y)) {
                    return new int[]{x, y};
                }
            }
            y++;
        }
    }

    private void validateDuplicateFolderName(User user, Folder parentFolder, String name, Long currentFolderId) {
        boolean exists = parentFolder == null
                ? folderRepository.existsByUserAndParentFolderIsNullAndName(user, name)
                : folderRepository.existsByUserAndParentFolderAndName(user, parentFolder, name);

        if (exists) {
            // 본인 자신과의 중복이 아닌 경우에만 예외
            if (currentFolderId != null) {
                Folder existing = folderRepository.findById(currentFolderId).orElse(null);
                if (existing != null && existing.getName().equals(name) && Objects.equals(existing.getParentFolder(), parentFolder)) {
                    return;
                }
            }
            throw new CustomException(ErrorCode.DUPLICATE_FOLDER_NAME);
        }
    }

    private boolean isDescendantOf(Folder targetParent, Long sourceFolderId) {
        Folder current = targetParent;
        while (current != null) {
            if (current.getId().equals(sourceFolderId)) {
                return true;
            }
            current = current.getParentFolder();
        }
        return false;
    }

    private int calculateMaxSubtreeDepth(Folder folder) {
        if (folder.getChildFolders() == null || folder.getChildFolders().isEmpty()) {
            return 0;
        }
        int maxChildDepth = 0;
        for (Folder child : folder.getChildFolders()) {
            maxChildDepth = Math.max(maxChildDepth, 1 + calculateMaxSubtreeDepth(child));
        }
        return maxChildDepth;
    }

    private void updateChildDepthsRecursively(Folder folder, int parentDepth) {
        if (folder.getChildFolders() != null) {
            for (Folder child : folder.getChildFolders()) {
                int childDepth = parentDepth + 1;
                child.moveFolder(folder, childDepth, child.getPositionX(), child.getPositionY());
                updateChildDepthsRecursively(child, childDepth);
            }
        }
    }

    private Folder findFolderByIdAndValidateOwnership(Long folderId, Long userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }
        return folder;
    }

    private User findUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
