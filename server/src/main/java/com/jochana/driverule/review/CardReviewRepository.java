package com.jochana.driverule.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardReviewRepository extends JpaRepository<CardReview, Long> {

	boolean existsByCardIdAndCardVersion(Long cardId, int cardVersion);

	boolean existsByCardIdAndCardVersionAndReviewerId(Long cardId, int cardVersion, Long reviewerId);

	long countByCardIdAndCardVersionAndDecision(Long cardId, int cardVersion, ReviewDecision decision);

	List<CardReview> findAllByCardIdOrderByReviewedAtAscIdAsc(Long cardId);

}
