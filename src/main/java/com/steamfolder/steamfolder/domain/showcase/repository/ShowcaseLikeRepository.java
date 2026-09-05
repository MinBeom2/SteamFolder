package com.steamfolder.steamfolder.domain.showcase.repository;

import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShowcaseLikeRepository extends JpaRepository<ShowcaseLike, Long> {

    Optional<ShowcaseLike> findByUserAndTargetUser(User user, User targetUser);

    boolean existsByUserAndTargetUser(User user, User targetUser);

    long countByTargetUser(User targetUser);

    void deleteByUserAndTargetUser(User user, User targetUser);
}
