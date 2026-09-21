package com.ats.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne
    @JoinColumn(name = "ambulance_id")
    private Ambulance ambulance;

    @Column(nullable = false)
    private double pickupLat;

    @Column(nullable = false)
    private double pickupLng;

    @Column
    private String pickupAddress;

    @Column
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    private Instant dispatchedAt;
    private Instant completedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        this.requestedAt = Instant.now();
    }

    public enum Status {
        PENDING, DISPATCHED, EN_ROUTE, COMPLETED, CANCELLED, NO_AMBULANCE_AVAILABLE
    }

    /**
     * Ordered low -> high urgency. DispatchService widens the nearest-ambulance
     * search radius as priority increases, so a critical/ICU patient can still
     * be matched to an ambulance further away rather than getting
     * NO_AMBULANCE_AVAILABLE just because nothing was within the default radius.
     */
    public enum Priority {
        NORMAL, SERIOUS, CRITICAL, ICU
    }
}
