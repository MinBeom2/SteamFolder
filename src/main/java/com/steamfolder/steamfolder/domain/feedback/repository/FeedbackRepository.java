package com.steamfolder.steamfolder.domain.feedback.repository;

import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
