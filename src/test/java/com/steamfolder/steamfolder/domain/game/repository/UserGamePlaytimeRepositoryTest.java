package com.steamfolder.steamfolder.domain.game.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserGamePlaytimeRepositoryTest {

    @Autowired UserGameRepository ownedGames;
    @Autowired UserRepository users;
    @Autowired GameRepository games;

    @Test
    void sumsAllOwnedGamesInMinutesUsingLongWithoutMixingOwners() {
        User first = users.save(User.builder().steamId("time-first").username("First").build());
        User second = users.save(User.builder().steamId("time-second").username("Second").build());
        Game one = games.save(Game.builder().appId(1L).name("One").build());
        Game two = games.save(Game.builder().appId(2L).name("Two").build());
        ownedGames.save(UserGame.builder().user(first).game(one).playtimeForever(Integer.MAX_VALUE).build());
        ownedGames.save(UserGame.builder().user(first).game(two).playtimeForever(125).build());
        ownedGames.save(UserGame.builder().user(second).game(one).playtimeForever(99).build());

        List<Object[]> rows = ownedGames.sumPlaytimeByUserIds(List.of(first.getId()));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()[0]).isEqualTo(first.getId());
        assertThat(rows.getFirst()[1]).isEqualTo((long) Integer.MAX_VALUE + 125);
    }

    @Test
    void returnsNoAggregateRowForAUserWithoutOwnedGames() {
        User empty = users.save(User.builder().steamId("time-empty").username("Empty").build());
        assertThat(ownedGames.sumPlaytimeByUserIds(List.of(empty.getId()))).isEmpty();
    }
}
