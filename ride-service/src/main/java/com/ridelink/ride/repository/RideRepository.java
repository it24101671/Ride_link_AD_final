package com.ridelink.ride.repository;

import com.ridelink.ride.entity.Ride;
import com.ridelink.ride.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    List<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    List<Ride> findByStatus(RideStatus status);

    boolean existsByPassengerIdAndStatusIn(Long passengerId, List<RideStatus> statuses);

    boolean existsByDriverIdAndStatusIn(Long driverId, List<RideStatus> statuses);
}
