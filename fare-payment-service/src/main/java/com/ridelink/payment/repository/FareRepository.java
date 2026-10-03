package com.ridelink.payment.repository;

import com.ridelink.payment.entity.Fare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FareRepository extends JpaRepository<Fare, Long> {

    Optional<Fare> findByFareReference(String fareReference);

    Optional<Fare> findTopByRideIdOrderByCreatedAtDesc(Long rideId);

    List<Fare> findByRideId(Long rideId);
}
