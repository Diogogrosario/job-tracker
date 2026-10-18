package com.jobtracker.email.review;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-queue")
public class ReviewQueueController {
    private final ReviewQueueService service;

    public ReviewQueueController(ReviewQueueService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReviewQueueResponse> list(
            @RequestParam(defaultValue = "PENDING") ReviewStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReviewApprovalRequest request) {
        service.approve(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        service.reject(id);
        return ResponseEntity.noContent().build();
    }
}
