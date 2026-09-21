package com.ats.controller;

import com.ats.model.Ambulance;
import com.ats.repository.AmbulanceRepository;
import com.ats.service.AmbulanceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ambulances")
@RequiredArgsConstructor
public class AmbulanceController {

    private final AmbulanceRepository ambulanceRepository;
    private final AmbulanceService ambulanceService;

    @GetMapping
    public ResponseEntity<List<Ambulance>> all() {
        return ResponseEntity.ok(ambulanceRepository.findAll());
    }

    // Fallback REST endpoint for location updates (WebSocket is the primary path -
    // see LocationWebSocketController). Handy for quick testing with curl/Postman.
    @PutMapping("/{id}/location")
    public ResponseEntity<Void> updateLocation(@PathVariable Long id, @RequestBody LocationBody body) {
        ambulanceService.updateLocation(id, body.getLat(), body.getLng());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestBody StatusBody body) {
        ambulanceService.setStatus(id, Ambulance.Status.valueOf(body.getStatus().toUpperCase()));
        return ResponseEntity.ok().build();
    }

    @Data
    public static class LocationBody {
        private double lat;
        private double lng;
    }

    @Data
    public static class StatusBody {
        private String status;
    }
}
