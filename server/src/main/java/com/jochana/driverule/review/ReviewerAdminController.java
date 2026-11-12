package com.jochana.driverule.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CMS · 검수자")
@RestController
@RequestMapping("/api/v1/admin/reviewers")
public class ReviewerAdminController {

	private final ReviewerRepository repository;

	public ReviewerAdminController(ReviewerRepository repository) {
		this.repository = repository;
	}

	public record CreateRequest(@NotBlank @Size(max = 50) String name) {
	}

	public record ReviewerResponse(Long id, String name) {

		static ReviewerResponse of(Reviewer r) {
			return new ReviewerResponse(r.getId(), r.getName());
		}
	}

	@Operation(summary = "검수자 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public ReviewerResponse create(@Valid @RequestBody CreateRequest req) {
		return ReviewerResponse.of(repository.save(new Reviewer(req.name())));
	}

	@Operation(summary = "검수자 목록")
	@GetMapping
	@Transactional(readOnly = true)
	public List<ReviewerResponse> list() {
		return repository.findAll(Sort.by("id")).stream().map(ReviewerResponse::of).toList();
	}

}
