package com.ats.dto;

import lombok.Data;

/**
 * Payload a driver's app publishes over WebSocket (STOMP destination
 * /app/location.update) to broadcast its live GPS position.
 */
@Data
public class LocationUpdateDTO {
    private Long ambulanceId;
    private double lat;
    private double lng;
}
