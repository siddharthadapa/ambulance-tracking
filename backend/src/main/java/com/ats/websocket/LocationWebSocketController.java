package com.ats.websocket;

import com.ats.dto.LocationUpdateDTO;
import com.ats.service.AmbulanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP handler for /app/location.update.
 *
 * A driver's device sends a frame like:
 *   { "ambulanceId": 12, "lat": 16.506, "lng": 80.648 }
 * roughly every 2-5 seconds while en route. AmbulanceService persists it
 * and republishes to /topic/ambulance.{id}.location, which the patient's
 * map view and the dispatcher's live board are both subscribed to.
 */
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final AmbulanceService ambulanceService;

    @MessageMapping("/location.update")
    public void handleLocationUpdate(LocationUpdateDTO update) {
        ambulanceService.updateLocation(update.getAmbulanceId(), update.getLat(), update.getLng());
    }
}
