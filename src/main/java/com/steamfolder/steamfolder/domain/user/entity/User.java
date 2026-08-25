package com.steamfolder.steamfolder.domain.user.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_steam_id", columnList = "steam_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "steam_id", length = 64, nullable = false, unique = true)
    private String steamId;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Builder.Default
    @Column(name = "theme_color", length = 20, nullable = false)
    private String themeColor = "#1A1B26";

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 유저 커스텀: 쇼케이스 메인 테마 색상 수정
     */
    public void updateThemeColor(String themeColor) {
        if (themeColor != null && !themeColor.isBlank()) {
            this.themeColor = themeColor;
        }
    }

    /**
     * 유저 커스텀: 쇼케이스 공개 여부 수정
     */
    public void updateVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    /**
     * 스팀 연동 동기화: 스팀 프로필 정보(닉네임, 아바타) 자동 최신화
     */
    public void syncSteamProfile(String username, String avatarUrl) {
        if (username != null && !username.isBlank()) {
            this.username = username;
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
