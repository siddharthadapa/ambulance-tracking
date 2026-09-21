package com.ats.repository;

import com.ats.model.BookingRequest;
import com.ats.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {
    List<BookingRequest> findByPatientOrderByRequestedAtDesc(User patient);
    List<BookingRequest> findByAmbulanceIdAndStatusIn(Long ambulanceId, List<BookingRequest.Status> statuses);
    List<BookingRequest> findByStatus(BookingRequest.Status status);
}
