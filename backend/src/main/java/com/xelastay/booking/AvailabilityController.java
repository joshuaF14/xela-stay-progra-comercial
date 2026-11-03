package com.xelastay.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.booking.dto.AvailabilityBlockRequest;
import com.xelastay.booking.dto.AvailabilityBlockResponse;
import com.xelastay.booking.dto.AvailabilityResponse;
import com.xelastay.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/properties/{propertyId}")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentUser currentUser;

    public AvailabilityController(AvailabilityService availabilityService, CurrentUser currentUser) {
        this.availabilityService = availabilityService;
        this.currentUser = currentUser;
    }

    /** Público (sin token): fechas ocupadas para pintar el calendario. Fechas en formato yyyy-MM-dd. */
    @GetMapping("/availability")
    public AvailabilityResponse availability(@PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availabilityService.occupiedDates(propertyId, from, to);
    }

    @GetMapping("/availability-blocks")
    public List<AvailabilityBlockResponse> listBlocks(@PathVariable UUID propertyId) {
        return availabilityService.listBlocks(currentUser.requireId(), propertyId);
    }

    @PostMapping("/availability-blocks")
    public ResponseEntity<AvailabilityBlockResponse> createBlock(@PathVariable UUID propertyId,
            @Valid @RequestBody AvailabilityBlockRequest request) {
        AvailabilityBlockResponse block = availabilityService.createBlock(currentUser.requireId(), propertyId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(block);
    }

    @PutMapping("/availability-blocks/{blockId}")
    public AvailabilityBlockResponse updateBlock(@PathVariable UUID propertyId, @PathVariable UUID blockId,
            @Valid @RequestBody AvailabilityBlockRequest request) {
        return availabilityService.updateBlock(currentUser.requireId(), propertyId, blockId, request);
    }

    @DeleteMapping("/availability-blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlock(@PathVariable UUID propertyId, @PathVariable UUID blockId) {
        availabilityService.deleteBlock(currentUser.requireId(), propertyId, blockId);
    }
}
