package com.steamfolder.steamfolder.domain.showcase.service;

import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ShowcaseConcurrencyTest {

    @Autowired
    private ShowcaseService showcaseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShowcaseLikeRepository showcaseLikeRepository;

    private User targetUser;

    @BeforeEach
    void setUp() {
        showcaseLikeRepository.deleteAll();
        userRepository.deleteAll();

        targetUser = userRepository.save(User.builder()
                .steamId("76561198000000099")
                .username("TargetStreamer")
                .avatarUrl("https://steamcdn.com/avatar.jpg")
                .isPublic(true)
                .likeCount(0)
                .build());
    }

    @AfterEach
    void tearDown() {
        showcaseLikeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("동시성 검증: 서로 다른 30명의 유저가 동시에 좋아요 클릭 시 Lost Update 없이 정확히 30 카운트 반영")
    void concurrentLikes_FromMultipleUsers_CountNeverLost() throws InterruptedException {
        // given
        int threadCount = 30;
        List<User> likerUsers = new ArrayList<>();
        for (int i = 1; i <= threadCount; i++) {
            likerUsers.add(userRepository.save(User.builder()
                    .steamId("765611980000000" + String.format("%02d", i))
                    .username("Liker_" + i)
                    .avatarUrl("https://steamcdn.com/avatar.jpg")
                    .isPublic(true)
                    .likeCount(0)
                    .build()));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when
        for (User liker : likerUsers) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 출발하도록 대기
                    showcaseService.toggleShowcaseLike(liker.getId(), targetUser.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 출발 신호
        doneLatch.await();      // 30개 스레드 완료 대기
        executorService.shutdown();

        // then
        User refreshedTarget = userRepository.findById(targetUser.getId()).orElseThrow();
        long actualLikeEntityCount = showcaseLikeRepository.countByTargetUser(refreshedTarget);

        // Lost Update 방어 검증: 실제 생성된 ShowcaseLike 엔티티 수와 User의 likeCount 컬럼이 정확히 30으로 일치해야 함
        assertThat(refreshedTarget.getLikeCount()).isEqualTo(threadCount);
        assertThat(actualLikeEntityCount).isEqualTo((long) threadCount);
    }

    @Test
    @DisplayName("동시성 검증: 동일 유저가 10번 동시 광클(더블클릭) 시 직렬화되어 에러 없이 데이터 정합성 유지")
    void concurrentDoubleClick_FromSameUser_SerializedWithoutException() throws InterruptedException {
        // given
        int requestCount = 10;
        User singleLiker = userRepository.save(User.builder()
                .steamId("76561198000000100")
                .username("RapidClicker")
                .avatarUrl("https://steamcdn.com/avatar.jpg")
                .isPublic(true)
                .likeCount(0)
                .build());

        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 10개 요청 동시 폭격
                    showcaseService.toggleShowcaseLike(singleLiker.getId(), targetUser.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then: 10개 요청 모두 500 에러 없이 성공적으로 직렬화 처리됨
        assertThat(successCount.get()).isEqualTo(requestCount);

        User refreshedTarget = userRepository.findById(targetUser.getId()).orElseThrow();
        long actualLikeEntityCount = showcaseLikeRepository.countByTargetUser(refreshedTarget);

        // 10번 토글(등록 -> 취소 -> 등록 -> ...) 후 최종 DB 엔티티 수와 likeCount 컬럼이 100% 일치해야 함
        // 짝수 번(10번) 토글되었으므로 최종 상태는 0이어야 함
        assertThat(refreshedTarget.getLikeCount()).isEqualTo((int) actualLikeEntityCount);
        assertThat(refreshedTarget.getLikeCount()).isEqualTo(0);
        assertThat(actualLikeEntityCount).isEqualTo(0L);
    }
}
