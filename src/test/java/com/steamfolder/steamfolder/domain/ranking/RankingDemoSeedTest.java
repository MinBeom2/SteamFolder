package com.steamfolder.steamfolder.domain.ranking;

import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("dev")
class RankingDemoSeedTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShowcaseLikeRepository showcaseLikeRepository;

    @Test
    void createsOneHundredPublicUsersWithConsistentLikes() {
        assertThat(userRepository.count()).isEqualTo(100);
        assertThat(showcaseLikeRepository.count()).isEqualTo(1050);
        assertThat(userRepository.findAll()).allSatisfy(user -> {
            assertThat(user.getSteamId()).startsWith("ranking-demo-");
            assertThat(user.isPublic()).isTrue();
            assertThat(user.getLikeCount()).isEqualTo(showcaseLikeRepository.countByTargetUser(user));
        });

        assertThat(userRepository.findFirstPageShowcaseRankings(PageRequest.of(0, 10)))
                .extracting(User::getLikeCount)
                .containsExactly(20, 20, 20, 20, 20, 19, 19, 19, 19, 19);
    }
}
