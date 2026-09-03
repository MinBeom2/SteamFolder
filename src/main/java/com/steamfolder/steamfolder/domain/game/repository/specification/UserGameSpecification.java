package com.steamfolder.steamfolder.domain.game.repository.specification;

import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserGameSpecification {

    public static Specification<UserGame> withDynamicFilter(User user, List<String> genres) {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("game", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();

            // 1. 유저 본인 소유 게임 조건
            predicates.add(cb.equal(root.get("user"), user));

            // 2. 다중 장르 OR 조건 (LOWER(genres) LIKE %genre%)
            if (genres != null && !genres.isEmpty()) {
                List<Predicate> genrePredicates = new ArrayList<>();
                for (String genre : genres) {
                    if (genre != null && !genre.isBlank()) {
                        genrePredicates.add(cb.like(
                                cb.lower(root.get("game").get("genres")),
                                "%" + genre.trim().toLowerCase() + "%"
                        ));
                    }
                }
                if (!genrePredicates.isEmpty()) {
                    predicates.add(cb.or(genrePredicates.toArray(new Predicate[0])));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
