package com.ats.service;

import com.ats.dto.BookingCreateRequest;
import com.ats.model.Ambulance;
import com.ats.model.BookingRequest;
import com.ats.model.User;
import com.ats.repository.BookingRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRequestRepository bookingRequestRepository;
    private final DispatchService dispatchService;
    private final AmbulanceService ambulanceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public BookingRequest createAndDispatch(User patient, BookingCreateRequest req) {
        BookingRequest booking = BookingRequest.builder()
                .patient(patient)
                .pickupLat(req.getPickupLat())
                .pickupLng(req.getPickupLng())
                .pickupAddress(req.getPickupAddress())
                .notes(req.getNotes())
                .status(BookingRequest.Status.PENDING)
                .priority(req.resolvePriority())
                .build();

        booking = bookingRequestRepository.save(booking);

        // Dispatch happens in its own transactional calls inside DispatchService
        // so optimistic-lock retries against the Ambulance table don't roll back
        // the booking row itself.
        return dispatchService.dispatch(booking);
    }

    public List<BookingRequest> myBookings(User patient) {
        return bookingRequestRepository.findByPatientOrderByRequestedAtDesc(patient);
    }

    public List<BookingRequest> pendingBookings() {
        return bookingRequestRepository.findByStatus(BookingRequest.Status.PENDING);
    }

    @Transactional
    public BookingRequest cancel(Long bookingId, User requester) {
        BookingRequest booking = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (!booking.getPatient().getId().equals(requester.getId())) {
            throw new SecurityException("Not authorized to cancel this booking");
        }

        booking.setStatus(BookingRequest.Status.CANCELLED);

        if (booking.getAmbulance() != null
                && booking.getAmbulance().getStatus() == Ambulance.Status.DISPATCHED) {
            Long ambulanceId = booking.getAmbulance().getId();
            ambulanceService.setStatus(ambulanceId, Ambulance.Status.AVAILABLE);

            // Tell the driver's app to stop navigating toward a booking that
            // no longer exists - an empty assignment means "go idle".
            messagingTemplate.send("/topic/ambulance." + ambulanceId + ".assignment",
                    MessageBuilder.withPayload(Map.of("cleared", true)).build());
        }

        return bookingRequestRepository.save(booking);
    }

    @Transactional
    public BookingRequest completeTrip(Long bookingId) {
        BookingRequest booking = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        booking.setStatus(BookingRequest.Status.COMPLETED);
        booking.setCompletedAt(Instant.now());

        if (booking.getAmbulance() != null) {
            ambulanceService.setStatus(booking.getAmbulance().getId(), Ambulance.Status.AVAILABLE);
        }

        BookingRequest saved = bookingRequestRepository.save(booking);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("status", "COMPLETED");

        // send(destination, Message) instead of the ambiguous convertAndSend(String, Object)
        // overload on Spring Framework 7's SimpMessagingTemplate - see DispatchService for details.
        messagingTemplate.send("/topic/dispatch." + booking.getId(), MessageBuilder.withPayload(payload).build());

        return saved;
    }
}
