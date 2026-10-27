package com.steamfolder.steamfolder.domain.showcase.service;

import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingItemDto;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingResponse;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ShowcaseRankingServiceTest {

    @InjectMocks
    private ShowcaseService showcaseService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserGameRepository userGameRepository;

    @Mock
    private ShowcaseLikeRepository showcaseLikeRepository;

    @Test
    @DisplayName("첫 페이지 조회 시 1위부터 순위가 매겨지고 다음 페이지 커서가 정상 계산된다 (비로그인 myRanking == null).")
    void getShowcaseRankings_firstPage_success() {
        // given: 3명의 유저 반환 (limit=2 일 때 limit+1=3개 조회되어 hasNext=true)
        User u1 = User.builder().id(10L).username("King").avatarUrl("a1").likeCount(100).build();
        User u2 = User.builder().id(20L).username("Queen").avatarUrl("a2").likeCount(80).build();
        User u3 = User.builder().id(30L).username("Knight").avatarUrl("a3").likeCount(50).build();

        given(userRepository.findFirstPageShowcaseRankings(PageRequest.of(0, 3)))
                .willReturn(List.of(u1, u2, u3));

        // 게임 수 배치 집계 Mock (u1: 15개, u2: 8개)
        List<Object[]> gameCountsMock = List.of(
                new Object[]{10L, 15L},
                new Object[]{20L, 8L}
        );
        given(userGameRepository.countUserGamesByUserIds(List.of(10L, 20L)))
                .willReturn(gameCountsMock);

        given(userGameRepository.sumPlaytimeByUserIds(List.of(10L, 20L)))
                .willReturn(List.of(new Object[]{10L, 3_000_000_000L}, new Object[]{20L, 125L}));

        // when (비로그인: currentUserId == null)
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(null, null, 2, null);

        // then
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getMyRanking()).isNull();
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getNextCursorLikeCount()).isEqualTo(80);
        assertThat(response.getNextCursorUserId()).isEqualTo(20L);
        assertThat(response.getPageSize()).isEqualTo(2);

        ShowcaseRankingItemDto item1 = response.getContent().get(0);
        assertThat(item1.getRank()).isEqualTo(1L);
        assertThat(item1.getUserId()).isEqualTo(10L);
        assertThat(item1.getUsername()).isEqualTo("King");
        assertThat(item1.getLikeCount()).isEqualTo(100);
        assertThat(item1.getTotalGamesCount()).isEqualTo(15L);
        assertThat(item1.getTotalPlaytimeForever()).isEqualTo(3_000_000_000L);
        assertThat(response.getContent().get(1).getTotalPlaytimeForever()).isEqualTo(125L);

        ShowcaseRankingItemDto item2 = response.getContent().get(1);
        assertThat(item2.getRank()).isEqualTo(2L);
        assertThat(item2.getUserId()).isEqualTo(20L);
        assertThat(item2.getLikeCount()).isEqualTo(80);
        assertThat(item2.getTotalGamesCount()).isEqualTo(8L);
    }

    @Test
    @DisplayName("커서 기반 다음 페이지 조회 시 앞선 유저 수를 기반으로 rank가 정확히 연속 부여된다.")
    void getShowcaseRankings_nextPageWithCursor_success() {
        // given: 커서(likeCount=80, userId=20) 이후 유저 1명 반환 (limit=2)
        User u3 = User.builder().id(30L).username("Knight").avatarUrl("a3").likeCount(50).build();

        given(userRepository.findNextPageShowcaseRankings(80, 20L, PageRequest.of(0, 3)))
                .willReturn(List.of(u3));
        given(userRepository.countShowcasesAheadOfCursor(80, 20L))
                .willReturn(2L); // 커서 앞 순위 2명 (1위, 2위)

        List<Object[]> gameCountsMock = Collections.singletonList(new Object[]{30L, 3L});
        given(userGameRepository.countUserGamesByUserIds(List.of(30L)))
                .willReturn(gameCountsMock);

        // when
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(80, 20L, 2, null);

        // then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getMyRanking()).isNull();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursorLikeCount()).isNull();
        assertThat(response.getNextCursorUserId()).isNull();

        ShowcaseRankingItemDto item3 = response.getContent().get(0);
        assertThat(item3.getRank()).isEqualTo(3L); // 2 + 1 = 3위
        assertThat(item3.getUserId()).isEqualTo(30L);
        assertThat(item3.getLikeCount()).isEqualTo(50);
        assertThat(item3.getTotalGamesCount()).isEqualTo(3L);
        assertThat(item3.getTotalPlaytimeForever()).isZero();
    }

    @Test
    @DisplayName("로그인 유저의 경우 랭킹 페이지 목록에 포함되지 않아도(순위 밖이어도) 본인의 순위와 좋아요 수가 myRanking으로 항상 반환된다.")
    void getShowcaseRankings_withCurrentUserId_returnsMyRanking() {
        // given: 1페이지는 100위권 밖 유저들 없이 1~2위만 있음
        User u1 = User.builder().id(10L).username("Top1").likeCount(500).build();
        User u2 = User.builder().id(20L).username("Top2").likeCount(400).build();

        given(userRepository.findFirstPageShowcaseRankings(PageRequest.of(0, 3)))
                .willReturn(List.of(u1, u2));
        given(userGameRepository.countUserGamesByUserIds(List.of(10L, 20L)))
                .willReturn(Collections.emptyList());

        // 로그인 본인(userId=99L): 좋아요 15개
        User myUser = User.builder()
                .id(99L)
                .username("MyShowcase")
                .avatarUrl("https://my-avatar.jpg")
                .likeCount(15)
                .build();

        given(userRepository.findById(99L)).willReturn(Optional.of(myUser));
        given(userRepository.countShowcasesStrictlyAhead(15, 99L)).willReturn(41L); // 앞선 순위 41명
        given(userGameRepository.sumPlaytimeByUserIds(List.of(10L, 20L, 99L)))
                .willReturn(Collections.singletonList(new Object[]{99L, 3_000_000_000L}));
        given(userGameRepository.countByUser(myUser)).willReturn(12L); // 보유 게임 12개

        // when
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(null, null, 2, 99L);

        // then
        assertThat(response.getMyRanking()).isNotNull();
        assertThat(response.getMyRanking().getTotalPlaytimeForever()).isEqualTo(3_000_000_000L);
        assertThat(response.getMyRanking().getRank()).isEqualTo(42L); // 41 + 1 = 42위!
        assertThat(response.getMyRanking().getUserId()).isEqualTo(99L);
        assertThat(response.getMyRanking().getUsername()).isEqualTo("MyShowcase");
        assertThat(response.getMyRanking().getLikeCount()).isEqualTo(15);
        assertThat(response.getMyRanking().isPublic()).isTrue();
    }

    @Test
    @DisplayName("로그인 유저가 비공개(isPublic = false) 상태인 경우 myRanking의 rank는 null이고 isPublic은 false로 반환되며 순위 쿼리는 호출되지 않는다.")
    void getShowcaseRankings_withPrivateCurrentUserId_returnsMyRankingWithNullRank() {
        // given
        User u1 = User.builder().id(10L).username("Top1").likeCount(500).build();
        given(userRepository.findFirstPageShowcaseRankings(PageRequest.of(0, 3)))
                .willReturn(List.of(u1));
        given(userGameRepository.countUserGamesByUserIds(List.of(10L)))
                .willReturn(Collections.emptyList());

        User myPrivateUser = User.builder()
                .id(99L)
                .username("PrivateShowcase")
                .avatarUrl("https://my-avatar.jpg")
                .likeCount(5)
                .isPublic(false)
                .build();

        given(userRepository.findById(99L)).willReturn(Optional.of(myPrivateUser));
        given(userGameRepository.countByUser(myPrivateUser)).willReturn(7L);

        // when
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(null, null, 2, 99L);

        // then
        assertThat(response.getMyRanking()).isNotNull();
        assertThat(response.getMyRanking().getRank()).isNull();
        assertThat(response.getMyRanking().isPublic()).isFalse();
        assertThat(response.getMyRanking().getLikeCount()).isEqualTo(5);
        assertThat(response.getMyRanking().getTotalGamesCount()).isEqualTo(7L);
        assertThat(response.getMyRanking().getTotalPlaytimeForever()).isZero();
        // countShowcasesStrictlyAhead 순위 쿼리는 절대 호출되지 않아야 함 (0회 호출)
        then(userRepository).should(never()).countShowcasesStrictlyAhead(anyInt(), anyLong());
    }

    @Test
    @DisplayName("2페이지 이후 커서 스크롤 시에는 로그인 상태라도 불필요한 myRanking 조회를 건너뛰고 null을 반환한다 (중복 쿼리 0회 방어).")
    void getShowcaseRankings_nextPageWithCursor_doesNotCalculateMyRanking() {
        // given: 커서 조회 시
        User u3 = User.builder().id(30L).username("Knight").likeCount(50).build();
        given(userRepository.findNextPageShowcaseRankings(80, 20L, PageRequest.of(0, 3)))
                .willReturn(List.of(u3));
        given(userRepository.countShowcasesAheadOfCursor(80, 20L)).willReturn(2L);
        given(userGameRepository.countUserGamesByUserIds(List.of(30L)))
                .willReturn(Collections.emptyList());

        // when: 로그인 상태(currentUserId=99L)이지만 커서(80, 20L)로 2페이지 요청
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(80, 20L, 2, 99L);

        // then: myRanking은 null이어야 하며 본인 랭킹 쿼리는 일체 호출되지 않음
        assertThat(response.getMyRanking()).isNull();
        assertThat(response.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("랭킹 데이터가 없을 때 빈 목록과 null 커서를 반환한다.")
    void getShowcaseRankings_empty() {
        // given
        given(userRepository.findFirstPageShowcaseRankings(any(PageRequest.class)))
                .willReturn(Collections.emptyList());

        // when
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(null, null, 10, null);

        // then
        assertThat(response.getContent()).isEmpty();
        then(userGameRepository).should(never()).sumPlaytimeByUserIds(any());
        assertThat(response.getMyRanking()).isNull();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursorLikeCount()).isNull();
        assertThat(response.getNextCursorUserId()).isNull();
    }

    @Test
    @DisplayName("로그인 유저가 랭킹 조회 시 좋아요한 쇼케이스는 isLiked=true, 아니면 false로 정확히 세팅된다.")
    void getShowcaseRankings_withCurrentUserId_setsIsLikedProperly() {
        // given
        User u1 = User.builder().id(10L).username("LikedUser").likeCount(100).build();
        User u2 = User.builder().id(20L).username("UnlikedUser").likeCount(80).build();

        given(userRepository.findFirstPageShowcaseRankings(PageRequest.of(0, 3)))
                .willReturn(List.of(u1, u2));
        given(userGameRepository.countUserGamesByUserIds(List.of(10L, 20L)))
                .willReturn(Collections.emptyList());

        // 99L 유저는 10L 유저의 쇼케이스만 좋아요한 상태
        given(showcaseLikeRepository.findTargetUserIdsByUserIdAndTargetUserIdIn(99L, List.of(10L, 20L)))
                .willReturn(Set.of(10L));

        // when (currentUserId = 99L)
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(null, null, 2, 99L);

        // then
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getUserId()).isEqualTo(10L);
        assertThat(response.getContent().get(0).isLiked()).isTrue();
        assertThat(response.getContent().get(1).getUserId()).isEqualTo(20L);
        assertThat(response.getContent().get(1).isLiked()).isFalse();
    }
}
