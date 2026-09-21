package com.ats.dto;

import com.ats.model.BookingRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingCreateRequest {
    @NotNull
    private Double pickupLat;

    @NotNull
    private Double pickupLng;

    private String pickupAddress;
    private String notes;

    // NORMAL, SERIOUS, CRITICAL, ICU - optional, defaults to NORMAL if
    // omitted or unrecognized rather than rejecting the booking outright.
    private String priority;

    public BookingRequest.Priority resolvePriority() {
        if (priority == null || priority.isBlank()) {
            return BookingRequest.Priority.NORMAL;
        }
        try {
            return BookingRequest.Priority.valueOf(priority.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BookingRequest.Priority.NORMAL;
        }
    }
}
