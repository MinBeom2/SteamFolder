package com.steamfolder.steamfolder.domain.folder.entity;

import com.steamfolder.steamfolder.domain.game.entity.Game;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "folder_games", indexes = {
        @Index(name = "idx_folder_games_folder_app", columnList = "folder_id, app_id", unique = true),
        @Index(name = "idx_folder_games_folder_id", columnList = "folder_id"),
        @Index(name = "idx_folder_games_position", columnList = "folder_id, position_y, position_x")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FolderGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private Game game;

    @Builder.Default
    @Column(name = "position_x", nullable = false)
    private int positionX = 0;

    @Builder.Default
    @Column(name = "position_y", nullable = false)
    private int positionY = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void updatePosition(int positionX, int positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
    }
}
