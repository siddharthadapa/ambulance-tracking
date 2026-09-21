package com.ats.dto;

import com.ats.model.BookingRequest;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private String status;
    private String priority;
    private double pickupLat;
    private double pickupLng;
    private String pickupAddress;
    private Long ambulanceId;
    private String ambulanceVehicleNumber;
    private String driverName;
    private String driverPhone;
    private Instant requestedAt;
    private Instant dispatchedAt;

    public static BookingResponse from(BookingRequest b) {
        BookingResponseBuilder builder = BookingResponse.builder()
                .id(b.getId())
                .status(b.getStatus().name())
                .priority(b.getPriority().name())
                .pickupLat(b.getPickupLat())
                .pickupLng(b.getPickupLng())
                .pickupAddress(b.getPickupAddress())
                .requestedAt(b.getRequestedAt())
                .dispatchedAt(b.getDispatchedAt());

        if (b.getAmbulance() != null) {
            builder.ambulanceId(b.getAmbulance().getId())
                   .ambulanceVehicleNumber(b.getAmbulance().getVehicleNumber());
            if (b.getAmbulance().getDriver() != null) {
                builder.driverName(b.getAmbulance().getDriver().getFullName())
                       .driverPhone(b.getAmbulance().getDriver().getPhone());
            }
        }
        return builder.build();
    }
}
