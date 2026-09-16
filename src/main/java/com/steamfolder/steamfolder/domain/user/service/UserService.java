package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateProfileRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateVisibilityRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.UserResponse;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserDataCleanupService userDataCleanupService;

    public UserResponse getMyProfile(Long userId) {
        User user = findActiveUserById(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUserById(userId);
        user.updateThemeColor(request.getThemeColor());
        log.info("유저 테마 색상 업데이트 완료: userId={}, themeColor={}", userId, request.getThemeColor());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateVisibility(Long userId, UpdateVisibilityRequest request) {
        User user = findActiveUserById(userId);
        user.updateVisibility(request.getIsPublic());
        log.info("유저 쇼케이스 공개 범위 업데이트 완료: userId={}, isPublic={}", userId, request.getIsPublic());
        return UserResponse.from(user);
    }

    @Transactional
    public void resetWorkspace(Long userId) {
        User user = findActiveUserById(userId);
        userDataCleanupService.resetWorkspace(user);
        log.info("유저 작업 공간 초기화 완료: userId={}", userId);
    }

    @Transactional
    public void deleteAccount(Long userId) {
        User user = findActiveUserById(userId);

        userDataCleanupService.purgeAccountData(user);
        user.softDelete();
        log.info("유저 회원 탈퇴 완료: userId={}, steamId={}", userId, user.getSteamId());
    }

    private User findActiveUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND, "이미 탈퇴한 회원입니다.");
        }

        return user;
    }
}
