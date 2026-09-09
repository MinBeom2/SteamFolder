package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamAppDetailsResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameMetadataEnrichmentServiceTest {

    @Mock
    private SteamApiClient steamApiClient;

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private GameMetadataEnrichmentService enrichmentService;

    private Game emptyGame;
    private Game alreadyEnrichedGame;

    @BeforeEach
    void setUp() {
        emptyGame = Game.builder()
                .appId(1091500L)
                .name("Cyberpunk 2077")
                .headerImage("https://example.com/header.jpg")
                .heroImage("https://example.com/hero.jpg")
                .genres(new ArrayList<>())
                .developer("")
                .build();

        alreadyEnrichedGame = Game.builder()
                .appId(730L)
                .name("Counter-Strike 2")
                .headerImage("https://example.com/cs2.jpg")
                .genres(List.of("Action", "FPS"))
                .developer("Valve")
                .build();
    }

    @Test
    @DisplayName("장르가 비어있는 게임은 스팀 상점 API를 호출하여 장르 및 메타데이터를 보강한다.")
    void enrichSingleGame_Success() {
        // given
        given(gameRepository.findById(1091500L)).willReturn(Optional.of(emptyGame));

        SteamAppDetailsResponseDto.AppData appData = new SteamAppDetailsResponseDto.AppData();
        ReflectionTestUtils.setField(appData, "steamAppId", 1091500L);
        ReflectionTestUtils.setField(appData, "name", "Cyberpunk 2077");
        ReflectionTestUtils.setField(appData, "headerImage", "https://cdn.akamai.steamstatic.com/header.jpg");
        ReflectionTestUtils.setField(appData, "background", "https://cdn.akamai.steamstatic.com/bg.jpg");
        ReflectionTestUtils.setField(appData, "developers", List.of("CD PROJEKT RED"));

        SteamAppDetailsResponseDto.GenreDto g1 = new SteamAppDetailsResponseDto.GenreDto();
        ReflectionTestUtils.setField(g1, "description", "RPG");
        SteamAppDetailsResponseDto.GenreDto g2 = new SteamAppDetailsResponseDto.GenreDto();
        ReflectionTestUtils.setField(g2, "description", "액션");
        ReflectionTestUtils.setField(appData, "genres", List.of(g1, g2));

        SteamAppDetailsResponseDto.ReleaseDateDto releaseDate = new SteamAppDetailsResponseDto.ReleaseDateDto();
        ReflectionTestUtils.setField(releaseDate, "date", "2020-12-10");
        ReflectionTestUtils.setField(appData, "releaseDate", releaseDate);

        given(steamApiClient.getAppDetails(1091500L)).willReturn(Optional.of(appData));

        // when
        enrichmentService.enrichSingleGame(1091500L);

        // then
        assertThat(emptyGame.getGenres()).containsExactly("RPG", "액션");
        assertThat(emptyGame.getDeveloper()).isEqualTo("CD PROJEKT RED");
        assertThat(emptyGame.getReleaseDate()).isEqualTo("2020-12-10");
        verify(gameRepository).save(emptyGame);
    }

    @Test
    @DisplayName("이미 장르가 채워진 게임은 외부 스팀 상점 API를 호출하지 않고 건너뛴다.")
    void enrichSingleGame_AlreadyEnriched_SkipsApiCall() {
        // given
        given(gameRepository.findById(730L)).willReturn(Optional.of(alreadyEnrichedGame));

        // when
        enrichmentService.enrichSingleGame(730L);

        // then
        verify(steamApiClient, never()).getAppDetails(any());
        verify(gameRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 appId에 대해서는 안전하게 건너뛴다.")
    void enrichSingleGame_GameNotFound_Skips() {
        // given
        given(gameRepository.findById(999999L)).willReturn(Optional.empty());

        // when
        enrichmentService.enrichSingleGame(999999L);

        // then
        verify(steamApiClient, never()).getAppDetails(any());
        verify(gameRepository, never()).save(any());
    }

    @Test
    @DisplayName("비동기 일괄 보강 호출 시 null 및 빈 목록에 대해 안전하게 종료된다.")
    void enrichGameMetadataAsync_EmptyOrNullList_SafeReturn() {
        // when
        enrichmentService.enrichGameMetadataAsync(null);
        enrichmentService.enrichGameMetadataAsync(Collections.emptyList());

        // then
        verify(gameRepository, never()).findById(any());
    }
}
