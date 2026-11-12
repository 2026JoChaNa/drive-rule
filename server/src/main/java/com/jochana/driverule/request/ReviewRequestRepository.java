package com.jochana.driverule.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, Long> {

	@Query("select r from ReviewRequest r"
			+ " where (:status is null or r.status = :status) and (:type is null or r.requestType = :type)"
			+ " order by r.createdAt desc, r.id desc")
	List<ReviewRequest> search(@Param("status") RequestStatus status, @Param("type") RequestType type);

}
