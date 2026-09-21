package com.ats.repository;

import com.ats.model.Ambulance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AmbulanceRepository extends JpaRepository<Ambulance, Long> {

    Optional<Ambulance> findByDriverId(Long driverId);

    /**
     * Nearest-first search among AVAILABLE ambulances using the Haversine
     * formula computed in SQL, restricted to a radius (km). This avoids
     * pulling every ambulance row into Java memory and filtering there -
     * the distance math and the ORDER BY both happen in MySQL.
     *
     * distance = 6371 * acos( cos(rad(:lat)) * cos(rad(current_lat))
     *                        * cos(rad(current_lng) - rad(:lng))
     *                        + sin(rad(:lat)) * sin(rad(current_lat)) )
     */
    @Query(value = """
        SELECT a.*,
               (6371 * acos(
                    cos(radians(:lat)) * cos(radians(a.current_lat)) *
                    cos(radians(a.current_lng) - radians(:lng)) +
                    sin(radians(:lat)) * sin(radians(a.current_lat))
               )) AS distance_km
        FROM ambulances a
        WHERE a.status = 'AVAILABLE'
        HAVING distance_km <= :radiusKm
        ORDER BY distance_km ASC
        """, nativeQuery = true)
    List<Ambulance> findNearestAvailable(@Param("lat") double lat,
                                          @Param("lng") double lng,
                                          @Param("radiusKm") double radiusKm);

    /**
     * Re-fetch a single ambulance row with a pessimistic write lock as a
     * fallback path if you want to avoid optimistic-retry storms under very
     * heavy contention on one specific vehicle. Not used by default (we use
     * @Version optimistic locking instead), but kept here as the documented
     * alternative discussed with candidates in interviews.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Ambulance a where a.id = :id")
    Optional<Ambulance> findByIdForUpdate(@Param("id") Long id);

    @NonNull
    @Override
    List<Ambulance> findAll();
}
