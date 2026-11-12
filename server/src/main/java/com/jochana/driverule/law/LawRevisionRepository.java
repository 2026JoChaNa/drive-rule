package com.jochana.driverule.law;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LawRevisionRepository extends JpaRepository<LawRevision, Long> {

	boolean existsByLawNo(String lawNo);

	List<LawRevision> findAllByOrderByPromulgatedOnDescIdDesc();

	List<LawRevision> findAllByTriageStatusOrderByPromulgatedOnDescIdDesc(TriageStatus triageStatus);

}
