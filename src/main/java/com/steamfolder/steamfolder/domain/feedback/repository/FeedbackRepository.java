package com.steamfolder.steamfolder.domain.feedback.repository;

import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    void deleteByUser(User user);
}
