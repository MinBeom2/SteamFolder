package com.steamfolder.steamfolder.domain.folder.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "folders", indexes = {
        @Index(name = "idx_folders_user_parent", columnList = "user_id, parent_folder_id"),
        @Index(name = "idx_folders_user_id", columnList = "user_id"),
        @Index(name = "idx_folders_position", columnList = "user_id, parent_folder_id, position_y, position_x")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Folder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    @Builder.Default
    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("positionY ASC, positionX ASC")
    private List<Folder> childFolders = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("positionY ASC, positionX ASC")
    private List<FolderGame> folderGames = new ArrayList<>();

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "theme_color", length = 20, nullable = false)
    private String themeColor = "#1A1B26";

    @Builder.Default
    @Column(name = "icon", length = 50, nullable = false)
    private String icon = "folder";

    @Builder.Default
    @Column(name = "position_x", nullable = false)
    private int positionX = 0;

    @Builder.Default
    @Column(name = "position_y", nullable = false)
    private int positionY = 0;

    @Builder.Default
    @Column(name = "depth", nullable = false)
    private int depth = 0;

    @Builder.Default
    @Column(name = "is_open", nullable = false)
    private boolean isOpen = false;

    public void addChildFolder(Folder childFolder) {
        this.childFolders.add(childFolder);
        childFolder.parentFolder = this;
    }

    public void updateFolder(String name, String themeColor, String icon) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (themeColor != null && !themeColor.isBlank()) {
            this.themeColor = themeColor;
        }
        if (icon != null && !icon.isBlank()) {
            this.icon = icon;
        }
    }

    public void moveFolder(Folder newParentFolder, int newDepth, int newPositionX, int newPositionY) {
        this.parentFolder = newParentFolder;
        this.depth = newDepth;
        this.positionX = newPositionX;
        this.positionY = newPositionY;
    }

    public void updatePosition(int positionX, int positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
    }

    public void toggleOpen(boolean isOpen) {
        this.isOpen = isOpen;
    }
}
