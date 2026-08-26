package com.steamfolder.steamfolder.domain.game.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import com.steamfolder.steamfolder.common.util.StringListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "games", indexes = {
        @Index(name = "idx_game_name", columnList = "name")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Game extends BaseTimeEntity {

    @Id
    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "header_image", length = 500)
    private String headerImage;

    @Column(name = "hero_image", length = 500)
    private String heroImage;

    @Builder.Default
    @Convert(converter = StringListConverter.class)
    @Column(name = "genres", length = 255)
    private List<String> genres = new ArrayList<>();

    @Column(name = "developer", length = 200)
    private String developer;

    @Column(name = "release_date", length = 50)
    private String releaseDate;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    public void updateMetadata(String name, String headerImage, String heroImage, List<String> genres, String developer, String releaseDate) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (headerImage != null) {
            this.headerImage = headerImage;
        }
        if (heroImage != null) {
            this.heroImage = heroImage;
        }
        if (genres != null) {
            this.genres = new ArrayList<>(genres);
        }
        if (developer != null) {
            this.developer = developer;
        }
        if (releaseDate != null) {
            this.releaseDate = releaseDate;
        }
        this.lastSyncedAt = LocalDateTime.now();
    }
}
