package com.steamfolder.steamfolder.domain.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameSeedDataLoaderTest {

    @Mock
    private GameRepository gameRepository;

    private ObjectMapper objectMapper;
    private GameSeedDataLoader seedDataLoader;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        seedDataLoader = new GameSeedDataLoader(gameRepository, objectMapper);
    }

    @Test
    @DisplayName("시드 JSON 파일을 정상적으로 파싱하여 미등록 게임을 DB에 일괄 적재한다.")
    void loadSeedData_InsertsNewGames() {
        // given
        given(gameRepository.findAllById(anyList())).willReturn(new ArrayList<>());

        // when
        seedDataLoader.loadSeedData();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Game>> captor = ArgumentCaptor.forClass(List.class);
        verify(gameRepository).saveAll(captor.capture());

        List<Game> savedGames = captor.getValue();
        assertThat(savedGames).isNotEmpty();
        assertThat(savedGames.size()).isGreaterThanOrEqualTo(50);

        // 배틀그라운드(578080) 또는 사이버펑크(1091500) 확인
        Game pubg = savedGames.stream()
                .filter(g -> g.getAppId().equals(578080L))
                .findFirst()
                .orElse(null);

        assertThat(pubg).isNotNull();
        assertThat(pubg.getName()).isEqualTo("PUBG: BATTLEGROUNDS");
        assertThat(pubg.getGenres()).contains("Action", "액션");
    }
}
