package com.ats.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ambulance entity.
 *
 * The @Version field is the core of our optimistic-locking strategy:
 * whenever two dispatch requests race to claim the same ambulance,
 * only the transaction that read the original version number can
 * commit its UPDATE. The second transaction gets an
 * OptimisticLockingFailureException, which DispatchService catches
 * and retries against the next-nearest available ambulance instead
 * of double-booking.
 */
@Entity
@Table(name = "ambulances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ambulance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String vehicleNumber;

    @OneToOne
    @JoinColumn(name = "driver_id", unique = true)
    private User driver;

    @Column(nullable = false)
    private double currentLat;

    @Column(nullable = false)
    private double currentLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Version
    private Long version;

    public enum Status {
        AVAILABLE, DISPATCHED, EN_ROUTE, OFFLINE
    }
}
