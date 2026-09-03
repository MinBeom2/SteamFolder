package com.steamfolder.steamfolder.domain.game.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class GameRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private UserGameRepository userGameRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Game 엔티티 저장 및 AppID/이름 검색, List<String> 장르 매핑 검증")
    void saveAndFindGame() {
        // given
        Game game = Game.builder()
                .appId(730L)
                .name("Counter-Strike 2")
                .headerImage("https://cdn.akamai.steamstatic.com/steam/apps/730/header.jpg")
                .heroImage("https://cdn.akamai.steamstatic.com/steam/apps/730/library_hero.jpg")
                .genres(List.of("Action", "Free to Play", "FPS"))
                .developer("Valve")
                .releaseDate("2012-08-21")
                .build();

        // when
        Game savedGame = gameRepository.save(game);
        Optional<Game> found = gameRepository.findById(730L);
        List<Game> searched = gameRepository.findByNameContainingIgnoreCase("counter");

        // then
        assertThat(savedGame.getAppId()).isEqualTo(730L);
        assertThat(found).isPresent();
        assertThat(found.get().getAppId()).isEqualTo(730L);
        assertThat(found.get().getName()).isEqualTo("Counter-Strike 2");
        assertThat(found.get().getDeveloper()).isEqualTo("Valve");
        assertThat(found.get().getGenres()).containsExactly("Action", "Free to Play", "FPS");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(searched).hasSize(1);
    }

    @Test
    @DisplayName("UserGame 매핑 엔티티 저장 및 플레이타임 내림차순 정렬 조회 테스트")
    void saveAndFindUserGame() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000001")
                .username("ProGamer")
                .build());

        Game game1 = gameRepository.save(Game.builder().appId(10L).name("Counter-Strike").genres(List.of("Action")).build());
        Game game2 = gameRepository.save(Game.builder().appId(570L).name("Dota 2").genres(List.of("MOBA", "Strategy")).build());

        userGameRepository.save(UserGame.builder()
                .user(user)
                .game(game1)
                .playtimeForever(1200) // 20시간
                .lastPlayedAt(LocalDateTime.now().minusDays(2))
                .build());

        userGameRepository.save(UserGame.builder()
                .user(user)
                .game(game2)
                .playtimeForever(4800) // 80시간
                .lastPlayedAt(LocalDateTime.now().minusHours(5))
                .build());

        // when
        List<UserGame> userGames = userGameRepository.findByUserOrderByPlaytimeForeverDesc(user);

        // then
        assertThat(userGames).hasSize(2);
        // 플레이타임 내림차순: Dota 2 (4800) -> Counter-Strike (1200)
        assertThat(userGames.get(0).getGame().getName()).isEqualTo("Dota 2");
        assertThat(userGames.get(0).getGame().getGenres()).containsExactly("MOBA", "Strategy");
        assertThat(userGames.get(0).getPlaytimeForever()).isEqualTo(4800);
        assertThat(userGames.get(1).getGame().getName()).isEqualTo("Counter-Strike");
    }

    @Test
    @DisplayName("UserGame 플레이타임 갱신 및 유저별 일괄 삭제 테스트")
    void updatePlaytimeAndDeleteByUser() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000002")
                .username("CasualGamer")
                .build());

        Game game = gameRepository.save(Game.builder().appId(1091500L).name("Cyberpunk 2077").genres(List.of("RPG", "Open World")).build());

        UserGame userGame = userGameRepository.save(UserGame.builder()
                .user(user)
                .game(game)
                .playtimeForever(300)
                .build());

        // when (플레이타임 갱신)
        userGame.updatePlaytime(600, LocalDateTime.now());
        userGameRepository.flush();

        Optional<UserGame> updated = userGameRepository.findByUserAndGame(user, game);

        // then (갱신 확인)
        assertThat(updated).isPresent();
        assertThat(updated.get().getPlaytimeForever()).isEqualTo(600);

        // when (유저별 보유 게임 일괄 삭제)
        userGameRepository.deleteByUser(user);
        userGameRepository.flush();

        // then (삭제 확인)
        assertThat(userGameRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("UserGameSpecification 기반 DB 레벨 동적 장르 필터링 및 DB 정렬 검증")
    void filterUserGamesWithSpecification() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000003")
                .username("SpecGamer")
                .build());

        Game game1 = gameRepository.save(Game.builder().appId(10L).name("Cyberpunk 2077").genres(List.of("Action", "RPG")).build());
        Game game2 = gameRepository.save(Game.builder().appId(20L).name("Stardew Valley").genres(List.of("Simulation", "Indie")).build());
        Game game3 = gameRepository.save(Game.builder().appId(30L).name("Counter-Strike 2").genres(List.of("Action", "FPS")).build());

        userGameRepository.save(UserGame.builder().user(user).game(game1).playtimeForever(7200).build());
        userGameRepository.save(UserGame.builder().user(user).game(game2).playtimeForever(3000).build());
        userGameRepository.save(UserGame.builder().user(user).game(game3).playtimeForever(600).build());

        // when (장르 Action 필터 + 가나다순 정렬)
        var spec = com.steamfolder.steamfolder.domain.game.repository.specification.UserGameSpecification.withDynamicFilter(user, List.of("Action"));
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "game.name");
        List<UserGame> results = userGameRepository.findAll(spec, sort);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getGame().getName()).isEqualTo("Counter-Strike 2");
        assertThat(results.get(1).getGame().getName()).isEqualTo("Cyberpunk 2077");
    }
}
