-- Development-only showcase ranking data. Hibernate runs this after ddl-auto=create.
INSERT INTO users (steam_id, username, theme_color, is_public, like_count, created_at, updated_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT CONCAT('ranking-demo-', LPAD(n, 3, '0')),
       CONCAT('Demo User ', LPAD(n, 3, '0')),
       CASE MOD(n, 5)
           WHEN 0 THEN '#1A1B26'
           WHEN 1 THEN '#1E3A5F'
           WHEN 2 THEN '#2D3A2E'
           WHEN 3 THEN '#4A2C40'
           ELSE '#3F3528'
       END,
       TRUE,
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM seq;

-- Each demo user receives 1-20 likes from other demo users; there are no self-likes.
INSERT INTO showcase_likes (user_id, target_user_id, created_at, updated_at)
SELECT liker.id, target.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users liker
CROSS JOIN users target
WHERE liker.steam_id LIKE 'ranking-demo-%'
  AND target.steam_id LIKE 'ranking-demo-%'
  AND MOD(liker.id - target.id + 100, 100)
      BETWEEN 1 AND MOD(target.id - 1, 20) + 1;

UPDATE users target
SET like_count = (
    SELECT COUNT(*)
    FROM showcase_likes liked
    WHERE liked.target_user_id = target.id
)
WHERE target.steam_id LIKE 'ranking-demo-%';
