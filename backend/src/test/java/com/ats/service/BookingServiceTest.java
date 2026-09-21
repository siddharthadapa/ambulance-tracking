package com.ats.service;

import com.ats.dto.BookingCreateRequest;
import com.ats.model.Ambulance;
import com.ats.model.BookingRequest;
import com.ats.model.User;
import com.ats.repository.BookingRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRequestRepository bookingRequestRepository;
    @Mock private DispatchService dispatchService;
    @Mock private AmbulanceService ambulanceService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BookingService bookingService;

    private User patient;
    private User otherPatient;

    @BeforeEach
    void setUp() {
        patient = User.builder().id(1L).fullName("Patient One").role(User.Role.PATIENT).build();
        otherPatient = User.builder().id(2L).fullName("Patient Two").role(User.Role.PATIENT).build();

        lenient().when(bookingRequestRepository.save(any(BookingRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cancelFreesAmbulanceAndNotifiesDriver_whenBookingWasDispatched() {
        Ambulance ambulance = Ambulance.builder().id(10L).status(Ambulance.Status.DISPATCHED).build();
        BookingRequest booking = BookingRequest.builder()
                .id(5L).patient(patient).ambulance(ambulance)
                .status(BookingRequest.Status.DISPATCHED)
                .build();
        when(bookingRequestRepository.findById(5L)).thenReturn(Optional.of(booking));

        BookingRequest result = bookingService.cancel(5L, patient);

        assertThat(result.getStatus()).isEqualTo(BookingRequest.Status.CANCELLED);
        verify(ambulanceService).setStatus(10L, Ambulance.Status.AVAILABLE);
        verify(messagingTemplate).send(eq("/topic/ambulance.10.assignment"), any(Message.class));
    }

    @Test
    void cancelRejectsRequestFromSomeoneElsesBooking() {
        BookingRequest booking = BookingRequest.builder()
                .id(5L).patient(patient).status(BookingRequest.Status.PENDING)
                .build();
        when(bookingRequestRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(5L, otherPatient))
                .isInstanceOf(SecurityException.class);

        verify(ambulanceService, never()).setStatus(anyLong(), any());
    }

    @Test
    void completeTripFreesTheAmbulanceForTheNextRequest() {
        Ambulance ambulance = Ambulance.builder().id(10L).status(Ambulance.Status.DISPATCHED).build();
        BookingRequest booking = BookingRequest.builder()
                .id(5L).patient(patient).ambulance(ambulance)
                .status(BookingRequest.Status.DISPATCHED)
                .build();
        when(bookingRequestRepository.findById(5L)).thenReturn(Optional.of(booking));

        BookingRequest result = bookingService.completeTrip(5L);

        assertThat(result.getStatus()).isEqualTo(BookingRequest.Status.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(ambulanceService).setStatus(10L, Ambulance.Status.AVAILABLE);
    }

    @Test
    void createAndDispatchDelegatesToDispatchService() {
        var req = new BookingCreateRequest();
        req.setPickupLat(16.5);
        req.setPickupLng(80.6);

        BookingRequest dispatched = BookingRequest.builder().id(7L).status(BookingRequest.Status.DISPATCHED).build();
        when(dispatchService.dispatch(any(BookingRequest.class))).thenReturn(dispatched);

        BookingRequest result = bookingService.createAndDispatch(patient, req);

        assertThat(result).isEqualTo(dispatched);
        verify(dispatchService).dispatch(any(BookingRequest.class));
    }
}
