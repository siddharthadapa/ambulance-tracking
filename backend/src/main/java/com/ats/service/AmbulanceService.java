package com.ats.service;

import com.ats.model.Ambulance;
import com.ats.repository.AmbulanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AmbulanceService {

    private final AmbulanceRepository ambulanceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<Ambulance> findAll() {
        return ambulanceRepository.findAll();
    }

    /**
     * Called from the WebSocket controller every time a driver's device
     * pushes a new GPS fix. Persists the position and immediately
     * re-broadcasts it to /topic/ambulance.{id}.location so any patient or
     * dispatcher screen subscribed to that ambulance updates live.
     *
     * Retries on optimistic-lock conflicts because location writes are
     * frequent (every few seconds) and can race with a dispatch-time status
     * change on the same row; a short retry is far cheaper than dropping
     * the location frame.
     */
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void updateLocation(Long ambulanceId, double lat, double lng) {
        Ambulance ambulance = ambulanceRepository.findById(ambulanceId)
                .orElseThrow(() -> new IllegalArgumentException("Ambulance not found: " + ambulanceId));

        ambulance.setCurrentLat(lat);
        ambulance.setCurrentLng(lng);
        ambulanceRepository.save(ambulance);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ambulanceId", ambulanceId);
        payload.put("lat", lat);
        payload.put("lng", lng);

        // send(destination, Message) instead of the ambiguous convertAndSend(String, Object)
        // overload on Spring Framework 7's SimpMessagingTemplate - see DispatchService for details.
        messagingTemplate.send("/topic/ambulance." + ambulanceId + ".location", MessageBuilder.withPayload(payload).build());
    }

    @Transactional
    public void setStatus(Long ambulanceId, Ambulance.Status status) {
        Ambulance ambulance = ambulanceRepository.findById(ambulanceId)
                .orElseThrow(() -> new IllegalArgumentException("Ambulance not found: " + ambulanceId));
        ambulance.setStatus(status);
        ambulanceRepository.save(ambulance);
    }
}
