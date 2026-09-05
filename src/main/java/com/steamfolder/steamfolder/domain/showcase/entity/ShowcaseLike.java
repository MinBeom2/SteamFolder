package com.steamfolder.steamfolder.domain.showcase.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "showcase_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_showcase_like_user_target", columnNames = {"user_id", "target_user_id"})
}, indexes = {
        @Index(name = "idx_showcase_like_target_user", columnList = "target_user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ShowcaseLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;
}
