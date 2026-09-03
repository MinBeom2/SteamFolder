package com.steamfolder.steamfolder.domain.game.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_games", indexes = {
        @Index(name = "idx_user_games_user_app", columnList = "user_id, app_id", unique = true),
        @Index(name = "idx_user_games_user_id", columnList = "user_id"),
        @Index(name = "idx_user_games_playtime", columnList = "playtime_forever"),
        @Index(name = "idx_user_games_last_played", columnList = "last_played_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserGame extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private Game game;

    @Builder.Default
    @Column(name = "playtime_forever", nullable = false)
    private int playtimeForever = 0;

    @Column(name = "last_played_at")
    private LocalDateTime lastPlayedAt;

    @Column(name = "total_achievements")
    private Integer totalAchievements;

    @Column(name = "achieved_achievements")
    private Integer achievedAchievements;

    @Column(name = "achievement_rate")
    private Double achievementRate;

    @Column(name = "achievements_synced_at")
    private LocalDateTime achievementsSyncedAt;

    public void updatePlaytime(int playtimeForever, LocalDateTime lastPlayedAt) {
        this.playtimeForever = playtimeForever;
        if (lastPlayedAt != null) {
            this.lastPlayedAt = lastPlayedAt;
        }
    }

    public void updateAchievements(Integer totalAchievements, Integer achievedAchievements, Double achievementRate) {
        this.totalAchievements = totalAchievements;
        this.achievedAchievements = achievedAchievements;
        this.achievementRate = achievementRate;
        this.achievementsSyncedAt = LocalDateTime.now();
    }
}

