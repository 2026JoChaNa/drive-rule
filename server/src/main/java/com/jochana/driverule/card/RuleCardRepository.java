package com.jochana.driverule.card;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleCardRepository extends JpaRepository<RuleCard, Long> {

	/** 상태를 바꾸는 작업은 이 잠금으로 직렬화한다 — 동시에 두 승인이 들어와도 게시를 놓치지 않게. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from RuleCard c where c.id = :id")
	Optional<RuleCard> findForUpdate(@Param("id") Long id);

	Optional<RuleCard> findByTopic(String topic);

	List<RuleCard> findAllByOrderByIdAsc();

	List<RuleCard> findAllByStatusOrderByIdAsc(CardStatus status);

	@Query("select c from RuleCard c left join fetch c.revision where c.status = :status order by c.id")
	List<RuleCard> findAllWithRevisionByStatus(@Param("status") CardStatus status);

	@Query("select c from RuleCard c left join fetch c.revision"
			+ " where c.status = :status and c.updatedAt >= :since order by c.id")
	List<RuleCard> findAllWithRevisionByStatusUpdatedSince(@Param("status") CardStatus status,
			@Param("since") LocalDateTime since);

	@Query("select c.id from RuleCard c where c.status = :status order by c.id")
	List<Long> findIdsByStatus(@Param("status") CardStatus status);

	@Query("select c.id from RuleCard c where c.status = :status and c.updatedAt >= :since order by c.id")
	List<Long> findIdsByStatusUpdatedSince(@Param("status") CardStatus status, @Param("since") LocalDateTime since);

}
