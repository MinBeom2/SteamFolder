package com.steamfolder.steamfolder.domain.folder.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.request.CreateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdatePositionsRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @InjectMocks
    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderGameRepository folderGameRepository;

    @Mock
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("FolderMaster")
                .build();
    }

    @Test
    @DisplayName("루트 폴더 생성 시 빈 칸 자동 탐색을 통해 (0,0)에 배치 검증")
    void createFolder_Root_AutoPlacement() {
        // given
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("FPS")
                .themeColor("#00F0FF")
                .icon("gamepad")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.countByUser(testUser)).willReturn(0L);
        given(folderRepository.existsByUserAndParentFolderIsNullAndName(testUser, "FPS")).willReturn(false);
        given(folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(testUser))
                .willReturn(List.of());

        Folder savedFolder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("FPS")
                .themeColor("#00F0FF")
                .icon("gamepad")
                .positionX(0)
                .positionY(0)
                .depth(0)
                .build();

        given(folderRepository.save(any(Folder.class))).willReturn(savedFolder);

        // when
        FolderResponse response = folderService.createFolder(1L, request);

        // then
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("FPS");
        assertThat(response.getPositionX()).isEqualTo(0);
        assertThat(response.getPositionY()).isEqualTo(0);
        assertThat(response.getDepth()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 경로 내 동일 폴더명 존재 시 DUPLICATE_FOLDER_NAME 예외 발생")
    void createFolder_DuplicateName_ThrowsException() {
        // given
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("FPS")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.countByUser(testUser)).willReturn(1L);
        given(folderRepository.existsByUserAndParentFolderIsNullAndName(testUser, "FPS")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> folderService.createFolder(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_FOLDER_NAME);
    }

    @Test
    @DisplayName("폴더 최대 깊이(5단계) 초과 시 MAX_FOLDER_DEPTH_EXCEEDED 예외 발생")
    void createFolder_MaxDepthExceeded_ThrowsException() {
        // given
        Folder depth4Folder = Folder.builder()
                .id(4L)
                .user(testUser)
                .name("Depth4")
                .depth(4)
                .build();

        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("Depth5")
                .parentFolderId(4L)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.countByUser(testUser)).willReturn(5L);
        given(folderRepository.findById(4L)).willReturn(Optional.of(depth4Folder));

        // when & then
        assertThatThrownBy(() -> folderService.createFolder(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAX_FOLDER_DEPTH_EXCEEDED);
    }

    @Test
    @DisplayName("폴더 이동 시 자기 자신 또는 하위 자식으로 이동 시 CIRCULAR_FOLDER_REFERENCE 예외 발생")
    void moveFolder_CircularReference_ThrowsException() {
        // given
        Folder parentFolder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("Parent")
                .depth(0)
                .childFolders(new ArrayList<>())
                .build();

        Folder childFolder = Folder.builder()
                .id(20L)
                .user(testUser)
                .parentFolder(parentFolder)
                .name("Child")
                .depth(1)
                .childFolders(new ArrayList<>())
                .build();
        parentFolder.addChildFolder(childFolder);

        MoveFolderRequest request = MoveFolderRequest.builder()
                .targetParentFolderId(20L) // 부모를 자식의 하위로 이동 시도
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(parentFolder));
        given(folderRepository.findById(20L)).willReturn(Optional.of(childFolder));

        // when & then
        assertThatThrownBy(() -> folderService.moveFolder(1L, 10L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CIRCULAR_FOLDER_REFERENCE);
    }

    @Test
    @DisplayName("폴더 정보(이름, 테마색상, 아이콘) 정상 수정 검증")
    void updateFolder_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("OldName")
                .themeColor("#1A1B26")
                .icon("folder")
                .build();

        UpdateFolderRequest request = UpdateFolderRequest.builder()
                .name("NewName")
                .themeColor("#EF4444")
                .icon("fire")
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));
        given(folderRepository.existsByUserAndParentFolderIsNullAndName(testUser, "NewName")).willReturn(false);

        // when
        FolderResponse response = folderService.updateFolder(1L, 10L, request);

        // then
        assertThat(response.getName()).isEqualTo("NewName");
        assertThat(response.getThemeColor()).isEqualTo("#EF4444");
        assertThat(response.getIcon()).isEqualTo("fire");
    }

    @Test
    @DisplayName("폴더 삭제 시 folderRepository.delete 호출 검증")
    void deleteFolder_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("DeleteMe")
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));

        // when
        folderService.deleteFolder(1L, 10L);

        // then
        verify(folderRepository).delete(folder);
    }

    @Test
    @DisplayName("드래그 앤 드롭 2D 격자 좌표 일괄 업데이트 검증")
    void updatePositions_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .positionX(0)
                .positionY(0)
                .build();

        UpdatePositionsRequest request = UpdatePositionsRequest.builder()
                .positions(List.of(
                        UpdatePositionsRequest.ItemPositionDto.builder()
                                .type("FOLDER")
                                .id(10L)
                                .positionX(3)
                                .positionY(2)
                                .build()
                ))
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));

        // when
        folderService.updatePositions(1L, request);

        // then
        assertThat(folder.getPositionX()).isEqualTo(3);
        assertThat(folder.getPositionY()).isEqualTo(2);
    }
}
