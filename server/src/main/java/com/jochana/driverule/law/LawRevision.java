package com.jochana.driverule.law;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "law_revision")
public class LawRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "law_name", nullable = false, length = 100)
	private String lawName;

	@Column(name = "law_no", nullable = false, length = 30, unique = true)
	private String lawNo;

	@Column(name = "source_key", nullable = false, length = 40)
	private String sourceKey;

	@Column(name = "promulgated_on", nullable = false)
	private LocalDate promulgatedOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "triage_status", nullable = false)
	private TriageStatus triageStatus;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected LawRevision() {
	}

	public LawRevision(String lawName, String lawNo, String sourceKey, LocalDate promulgatedOn,
			LocalDateTime createdAt) {
		this.lawName = lawName;
		this.lawNo = lawNo;
		this.sourceKey = sourceKey;
		this.promulgatedOn = promulgatedOn;
		this.triageStatus = TriageStatus.PENDING;
		this.createdAt = createdAt;
	}

	public void changeTriageStatus(TriageStatus triageStatus) {
		this.triageStatus = triageStatus;
	}

	public Long getId() {
		return id;
	}

	public String getLawName() {
		return lawName;
	}

	public String getLawNo() {
		return lawNo;
	}

	public String getSourceKey() {
		return sourceKey;
	}

	public LocalDate getPromulgatedOn() {
		return promulgatedOn;
	}

	public TriageStatus getTriageStatus() {
		return triageStatus;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
