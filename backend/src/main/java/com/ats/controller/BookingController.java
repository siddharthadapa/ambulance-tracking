package com.ats.controller;

import com.ats.dto.BookingCreateRequest;
import com.ats.dto.BookingResponse;
import com.ats.model.BookingRequest;
import com.ats.model.User;
import com.ats.repository.UserRepository;
import com.ats.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final UserRepository userRepository;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingCreateRequest req,
                                                           Authentication auth) {
        BookingRequest booking = bookingService.createAndDispatch(currentUser(auth), req);
        return ResponseEntity.ok(BookingResponse.from(booking));
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> myBookings(Authentication auth) {
        List<BookingResponse> res = bookingService.myBookings(currentUser(auth)).stream()
                .map(BookingResponse::from).toList();
        return ResponseEntity.ok(res);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<BookingResponse>> pendingBookings() {
        List<BookingResponse> res = bookingService.pendingBookings().stream()
                .map(BookingResponse::from).toList();
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id, Authentication auth) {
        BookingRequest booking = bookingService.cancel(id, currentUser(auth));
        return ResponseEntity.ok(BookingResponse.from(booking));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<BookingResponse> complete(@PathVariable Long id) {
        BookingRequest booking = bookingService.completeTrip(id);
        return ResponseEntity.ok(BookingResponse.from(booking));
    }
}
