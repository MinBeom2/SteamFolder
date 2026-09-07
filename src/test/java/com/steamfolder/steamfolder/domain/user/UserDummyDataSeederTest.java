package com.steamfolder.steamfolder.domain.user;

import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class UserDummyDataSeederTest {

    @Autowired
    private UserRepository userRepository;

    public static final String[] ADJECTIVES = {
            "Silent", "Dark", "Cyber", "Pixel", "Shadow", "Neon", "Hyper", "Swift", "Brave", "Iron",
            "Golden", "Cosmic", "Turbo", "Frost", "Blaze", "Storm", "Phantom", "Mystic", "Retro", "Omega",
            "Alpha", "Vortex", "Echo", "Stealth", "Noble", "Crimson", "Lunar", "Solar", "Atomic", "Apex"
    };

    public static final String[] NOUNS = {
            "Knight", "Hunter", "Sniper", "Gamer", "Warrior", "Wizard", "Ranger", "Ninja", "Rogue", "Pilot",
            "Striker", "Titan", "Ghost", "Slayer", "Dragon", "Phoenix", "Falcon", "Viper", "Wolf", "Samurai",
            "Paladin", "Beast", "Glitch", "Master", "Champion", "Hero", "Captain", "Legend", "Shade", "Wanderer"
    };

    public static final String[] THEME_COLORS = {
            "#1A1B26", "#2A475E", "#171A21", "#66C0F4", "#1B2838", "#C7D5E0", "#101822", "#212B36"
    };

    /**
     * 100명의 더미 유저 생성 및 DB 저장 검증 테스트
     */
    @Test
    @Transactional
    @DisplayName("100명의 더미 유저 생성 및 저장 검증")
    void testInsert100DummyUsers() {
        List<User> dummyUsers = create100DummyUsers();
        userRepository.saveAll(dummyUsers);

        long count = userRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(100L);

        // 첫 번째와 마지막 유저 데이터 유효성 검증
        User firstUser = dummyUsers.get(0);
        assertThat(firstUser.getSteamId()).startsWith("76561198");
        assertThat(firstUser.getUsername()).isNotBlank();
        assertThat(firstUser.getAvatarUrl()).isNotBlank();
    }

    public static List<User> create100DummyUsers() {
        Random random = new Random(42);
        List<User> list = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            String steamId = String.format("76561198%08d", 80000000 + i);
            String adj = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
            String noun = NOUNS[random.nextInt(NOUNS.length)];
            int num = random.nextInt(900) + 10;
            String username = adj + noun + "_" + num;
            String avatarUrl = "https://avatars.steamstatic.com/fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb_full.jpg";
            String theme = THEME_COLORS[random.nextInt(THEME_COLORS.length)];
            boolean isPublic = random.nextDouble() > 0.15; // 85% 공개
            int likeCount = (random.nextDouble() < 0.3) ? 0 : random.nextInt(150);

            User user = User.builder()
                    .steamId(steamId)
                    .username(username)
                    .avatarUrl(avatarUrl)
                    .themeColor(theme)
                    .isPublic(isPublic)
                    .likeCount(likeCount)
                    .build();

            list.add(user);
        }
        return list;
    }
}
