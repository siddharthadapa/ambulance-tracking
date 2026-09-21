package com.ats.service;

import com.ats.model.Ambulance;
import com.ats.model.BookingRequest;
import com.ats.repository.AmbulanceRepository;
import com.ats.repository.BookingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles nearest-ambulance dispatch for a booking request.
 *
 * Concurrency problem this solves: two emergency requests near the same
 * ambulance can be processed by two different server threads (or two pods
 * behind a load balancer) at almost the same instant. Both read the same
 * ambulance as AVAILABLE and both try to claim it. Without protection,
 * both bookings would be told "your ambulance is on the way" for one
 * physical vehicle.
 *
 * Fix: Ambulance carries a @Version column. dispatch() loads the nearest
 * candidate, flips its status to DISPATCHED, and saves it inside a single
 * transaction. If another thread already committed a change to that row
 * first, Hibernate throws ObjectOptimisticLockingFailureException on
 * commit. We catch it, discard that candidate, and retry against the
 * *next* nearest available ambulance - so a collision costs one retry
 * instead of a double-booked patient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchService {

    private static final int MAX_CANDIDATES_TO_TRY = 5;

    /**
     * Higher-priority bookings search a wider radius, so a critical/ICU
     * patient can still be matched to an ambulance further away rather than
     * being told NO_AMBULANCE_AVAILABLE just because nothing was AVAILABLE
     * within the default 10km. This is the concrete effect of the priority
     * field, on top of it being shown in the dispatcher's UI.
     */
    private static double radiusKmFor(BookingRequest.Priority priority) {
        return switch (priority) {
            case NORMAL -> 10.0;
            case SERIOUS -> 15.0;
            case CRITICAL -> 25.0;
            case ICU -> 40.0;
        };
    }

    private final AmbulanceRepository ambulanceRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Attempts to dispatch the nearest available ambulance (within
     * a priority-dependent radius) to the given booking. Walks the nearest-first
     * candidate list and retries on optimistic-lock conflicts before
     * giving up.
     */
    public BookingRequest dispatch(BookingRequest booking) {
        double radiusKm = radiusKmFor(booking.getPriority());
        List<Ambulance> candidates = ambulanceRepository.findNearestAvailable(
                booking.getPickupLat(), booking.getPickupLng(), radiusKm);

        if (candidates.isEmpty()) {
            booking.setStatus(BookingRequest.Status.NO_AMBULANCE_AVAILABLE);
            return bookingRequestRepository.save(booking);
        }

        int attempts = 0;
        for (Ambulance candidate : candidates) {
            if (attempts >= MAX_CANDIDATES_TO_TRY) break;
            attempts++;
            try {
                Ambulance claimed = claimAmbulance(candidate.getId());
                return finalizeDispatch(booking, claimed);
            } catch (ObjectOptimisticLockingFailureException conflict) {
                log.warn("Optimistic lock conflict claiming ambulance {} for booking {} - trying next candidate",
                        candidate.getId(), booking.getId());
                // fall through to next-nearest candidate
            } catch (IllegalStateException alreadyTaken) {
                log.info("Ambulance {} no longer available, trying next candidate", candidate.getId());
            }
        }

        booking.setStatus(BookingRequest.Status.NO_AMBULANCE_AVAILABLE);
        return bookingRequestRepository.save(booking);
    }

    /**
     * Separate transactional method so each candidate claim is its own
     * transaction/commit - required for the optimistic-lock exception to
     * surface per-candidate rather than aborting the whole dispatch loop.
     */
    @Transactional
    public Ambulance claimAmbulance(Long ambulanceId) {
        Ambulance ambulance = ambulanceRepository.findById(ambulanceId)
                .orElseThrow(() -> new IllegalArgumentException("Ambulance not found"));

        if (ambulance.getStatus() != Ambulance.Status.AVAILABLE) {
            throw new IllegalStateException("Ambulance no longer available");
        }

        ambulance.setStatus(Ambulance.Status.DISPATCHED);
        return ambulanceRepository.save(ambulance); // @Version check happens on flush/commit
    }

    @Transactional
    public BookingRequest finalizeDispatch(BookingRequest booking, Ambulance ambulance) {
        booking.setAmbulance(ambulance);
        booking.setStatus(BookingRequest.Status.DISPATCHED);
        booking.setDispatchedAt(Instant.now());
        BookingRequest saved = bookingRequestRepository.save(booking);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("status", "DISPATCHED");
        payload.put("ambulanceId", ambulance.getId());
        payload.put("vehicleNumber", ambulance.getVehicleNumber());
        payload.put("driverName", ambulance.getDriver() != null ? ambulance.getDriver().getFullName() : "");
        payload.put("driverPhone", ambulance.getDriver() != null ? ambulance.getDriver().getPhone() : "");

        // Using send(destination, Message) instead of convertAndSend(destination, payload):
        // Spring Framework 7's SimpMessagingTemplate has an ambiguous convertAndSend(String, Object)
        // overload (inherited from two generic interface paths), so we build the Message explicitly.
        messagingTemplate.send("/topic/dispatch." + booking.getId(), MessageBuilder.withPayload(payload).build());

        // Also tell the DRIVER's own app where to go. The patient/dispatcher
        // topic above announces the dispatch outcome; this one is what the
        // driver's frontend subscribes to (by its own ambulance id) so it
        // knows the pickup coordinates to navigate toward.
        Map<String, Object> assignment = new LinkedHashMap<>();
        assignment.put("bookingId", booking.getId());
        assignment.put("pickupLat", booking.getPickupLat());
        assignment.put("pickupLng", booking.getPickupLng());
        assignment.put("pickupAddress", booking.getPickupAddress());
        messagingTemplate.send("/topic/ambulance." + ambulance.getId() + ".assignment",
                MessageBuilder.withPayload(assignment).build());

        return saved;
    }
}
