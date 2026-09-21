package com.ats.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String message;

    public ErrorResponse(int status, String message) {
        this.timestamp = Instant.now();
        this.status = status;
        this.message = message;
    }
}
