package com.steamfolder.steamfolder.domain.user.repository;

import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findBySteamId(String steamId);

    Optional<User> findByUsername(String username);

    boolean existsBySteamId(String steamId);
}
