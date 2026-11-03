package com.xelastay.review;

import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.common.PageResponse;
import com.xelastay.review.dto.ReviewRequest;
import com.xelastay.review.dto.ReviewResponse;
import com.xelastay.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @PostMapping("/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@PathVariable UUID bookingId, @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(currentUser.requireId(), bookingId, request);
    }

    /** Público (sin token). */
    @GetMapping("/properties/{propertyId}/reviews")
    public PageResponse<ReviewResponse> listForProperty(@PathVariable UUID propertyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listForProperty(propertyId, page, size);
    }

    @PutMapping("/reviews/{id}")
    public ReviewResponse update(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(currentUser.requireId(), id, request);
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        reviewService.delete(currentUser.requireId(), id);
    }
}
