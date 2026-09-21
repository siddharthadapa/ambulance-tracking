package com.ats.service;

import com.ats.model.Ambulance;
import com.ats.model.BookingRequest;
import com.ats.repository.AmbulanceRepository;
import com.ats.repository.BookingRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the behavior this project's whole "no double-booking" claim rests
 * on: DispatchService.dispatch() must (a) pick the nearest AVAILABLE
 * ambulance, (b) fall through to the next-nearest candidate when a claim
 * loses an optimistic-lock race rather than failing the booking outright,
 * (c) report NO_AMBULANCE_AVAILABLE honestly when nothing is in range, and
 * (d) actually widen the search radius as booking priority increases.
 */
@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private AmbulanceRepository ambulanceRepository;

    @Mock
    private BookingRequestRepository bookingRequestRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private DispatchService dispatchService;

    private BookingRequest booking;

    @BeforeEach
    void setUp() {
        booking = BookingRequest.builder()
                .id(100L)
                .pickupLat(16.5062)
                .pickupLng(80.6480)
                .priority(BookingRequest.Priority.NORMAL)
                .status(BookingRequest.Status.PENDING)
                .build();

        // save(...) echoes back whatever was passed in, like a real JPA save would for an
        // already-identified entity - lets us assert on the object dispatch() returns.
        when(bookingRequestRepository.save(any(BookingRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Ambulance ambulance(long id, Ambulance.Status status) {
        return Ambulance.builder()
                .id(id)
                .vehicleNumber("AP16-AM-" + id)
                .status(status)
                .currentLat(16.51)
                .currentLng(80.65)
                .build();
    }

    @Test
    void dispatchesToNearestAvailableAmbulance() {
        Ambulance nearest = ambulance(1L, Ambulance.Status.AVAILABLE);

        when(ambulanceRepository.findNearestAvailable(eq(16.5062), eq(80.6480), eq(10.0)))
                .thenReturn(List.of(nearest));
        when(ambulanceRepository.findById(1L)).thenReturn(java.util.Optional.of(nearest));
        when(ambulanceRepository.save(any(Ambulance.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingRequest result = dispatchService.dispatch(booking);

        assertThat(result.getStatus()).isEqualTo(BookingRequest.Status.DISPATCHED);
        assertThat(result.getAmbulance()).isEqualTo(nearest);
        assertThat(nearest.getStatus()).isEqualTo(Ambulance.Status.DISPATCHED);
        verify(messagingTemplate, atLeastOnce()).send(anyString(), any(Message.class));
    }

    @Test
    void fallsThroughToNextCandidateOnOptimisticLockConflict() {
        // Simulates two dispatch requests racing for ambulance #1 at nearly the
        // same instant: our claim on #1 loses the version-conflict race (as if
        // another transaction committed first), so dispatch() must retry
        // against the next-nearest candidate, #2, instead of failing the booking.
        Ambulance contested = ambulance(1L, Ambulance.Status.AVAILABLE);
        Ambulance fallback = ambulance(2L, Ambulance.Status.AVAILABLE);

        when(ambulanceRepository.findNearestAvailable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(contested, fallback));
        when(ambulanceRepository.findById(1L)).thenReturn(java.util.Optional.of(contested));
        when(ambulanceRepository.findById(2L)).thenReturn(java.util.Optional.of(fallback));

        when(ambulanceRepository.save(argThat((Ambulance a) -> a != null && a.getId() == 1L)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Ambulance.class, 1L));
        when(ambulanceRepository.save(argThat((Ambulance a) -> a != null && a.getId() == 2L)))
                .thenAnswer(inv -> inv.getArgument(0));

        BookingRequest result = dispatchService.dispatch(booking);

        assertThat(result.getStatus()).isEqualTo(BookingRequest.Status.DISPATCHED);
        assertThat(result.getAmbulance()).isEqualTo(fallback);
        assertThat(result.getAmbulance().getId()).isEqualTo(2L);
    }

    @Test
    void marksNoAmbulanceAvailableWhenNothingInRange() {
        when(ambulanceRepository.findNearestAvailable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        BookingRequest result = dispatchService.dispatch(booking);

        assertThat(result.getStatus()).isEqualTo(BookingRequest.Status.NO_AMBULANCE_AVAILABLE);
        assertThat(result.getAmbulance()).isNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void widensSearchRadiusForHigherPriorityBookings() {
        booking.setPriority(BookingRequest.Priority.ICU);
        when(ambulanceRepository.findNearestAvailable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        dispatchService.dispatch(booking);

        // ICU must search the widest radius (40km), not the NORMAL default (10km) -
        // this is the concrete behavior behind the priority feature, not just a label.
        verify(ambulanceRepository).findNearestAvailable(eq(16.5062), eq(80.6480), eq(40.0));
    }

    @Test
    void givesUpAfterExhaustingAllCandidatesToConflicts() {
        // Every single candidate loses its optimistic-lock race - dispatch()
        // must not loop forever or throw; it should land on
        // NO_AMBULANCE_AVAILABLE just like the empty-candidates case.
        Ambulance a1 = ambulance(1L, Ambulance.Status.AVAILABLE);
        Ambulance a2 = ambulance(2L, Ambulance.Status.AVAILABLE);

        when(ambulanceRepository.findNearestAvailable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(a1, a2));
        when(ambulanceRepository.findById(1L)).thenReturn(java.util.Optional.of(a1));
        when(ambulanceRepository.findById(2L)).thenReturn(java.util.Optional.of(a2));
        when(ambulanceRepository.save(any(Ambulance.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Ambulance.class, 0L));

        BookingRequest result = dispatchService.dispatch(booking);

        assertThat(result.getStatus()).isEqualTo(BookingRequest.Status.NO_AMBULANCE_AVAILABLE);
    }
}
