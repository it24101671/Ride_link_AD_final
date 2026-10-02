package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.entity.Ride;
import com.ridelink.ride.entity.RideStatus;
import com.ridelink.ride.exception.DriverUnavailableException;
import com.ridelink.ride.exception.InvalidRideStateException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RideService {

    private static final Logger logger = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final DriverServiceClient driverServiceClient;
    private final FareServiceClient fareServiceClient;

    public RideService(RideRepository rideRepository,
                       DriverServiceClient driverServiceClient,
                       FareServiceClient fareServiceClient) {
        this.rideRepository = rideRepository;
        this.driverServiceClient = driverServiceClient;
        this.fareServiceClient = fareServiceClient;
    }

    @Transactional
    public RideResponse createRide(RideCreateRequest request, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to create a ride");
        }

        Ride ride = new Ride();
        ride.setPassengerId(currentUser.getUserId());
        ride.setPickupLocation(request.getPickupLocation().trim());
        ride.setDestinationLocation(request.getDestinationLocation().trim());
        ride.setStatus(RideStatus.REQUESTED);

        // Interservice interaction with Service 4 (Fare Service) for fare estimate
        try {
            FareEstimateDto fareEstimate = fareServiceClient.estimateFare(
                    ride.getPickupLocation(),
                    ride.getDestinationLocation()
            );
            if (fareEstimate != null) {
                ride.setFareAmount(fareEstimate.getTotalFare());
                ride.setFareReference(fareEstimate.getFareReference());
            }
        } catch (Exception e) {
            logger.warn("Could not retrieve fare estimate, continuing with null fare: {}", e.getMessage());
        }

        Ride savedRide = rideRepository.save(ride);
        logger.info("Ride created successfully with ID: {} by passenger: {}", savedRide.getId(), currentUser.getUserId());
        return RideResponse.fromEntity(savedRide);
    }

    @Transactional(readOnly = true)
    public RideResponse getRideById(Long id, UserPrincipal currentUser, String token) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + id));

        validateRideAccess(ride, currentUser, token);
        return RideResponse.fromEntity(ride);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRides(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        List<Ride> rides = rideRepository.findByPassengerIdOrderByCreatedAtDesc(currentUser.getUserId());
        return rides.stream().map(RideResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRidesByDriver(Long driverId, UserPrincipal currentUser, String token) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isThisDriver = isUserAssignedDriver(driverId, currentUser, token);

        if (!isAdmin && !isThisDriver) {
            throw new AccessDeniedException("Access denied: You are not authorized to view rides for driver ID: " + driverId);
        }

        List<Ride> rides = rideRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
        return rides.stream().map(RideResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public RideResponse assignDriver(Long rideId, RideAssignRequest request, UserPrincipal currentUser, String token) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new InvalidRideStateException("Cannot assign driver to ride in state: " + ride.getStatus() + ". Ride must be in REQUESTED state.");
        }

        Long assignedDriverId;
        if (request != null && request.getDriverId() != null) {
            assignedDriverId = request.getDriverId();
            // Verify driver eligibility via Service 2 (Driver & Vehicle Service)
            driverServiceClient.verifyDriverEligibility(assignedDriverId, token);
        } else {
            // Interservice interaction with Service 2: Find eligible available drivers matching pickup location
            List<EligibleDriverDto> eligibleDrivers = driverServiceClient.findEligibleDrivers(ride.getPickupLocation());
            if (eligibleDrivers.isEmpty()) {
                // Try searching all available drivers if area-specific search yielded none
                eligibleDrivers = driverServiceClient.findEligibleDrivers(null);
            }

            if (eligibleDrivers.isEmpty()) {
                throw new DriverUnavailableException("No eligible available drivers found for ride assignment.");
            }
            assignedDriverId = eligibleDrivers.get(0).getDriverId();
        }

        ride.setDriverId(assignedDriverId);
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setAssignedAt(LocalDateTime.now());

        Ride updatedRide = rideRepository.save(ride);
        logger.info("Driver {} assigned to ride {}", assignedDriverId, rideId);
        return RideResponse.fromEntity(updatedRide);
    }

    @Transactional
    public RideResponse acceptRide(Long rideId, UserPrincipal currentUser, String token) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getStatus() != RideStatus.ASSIGNED) {
            throw new InvalidRideStateException("Cannot accept ride in state: " + ride.getStatus() + ". Ride must be in ASSIGNED state.");
        }

        validateDriverAction(ride, currentUser, token, "accept");

        ride.setStatus(RideStatus.ACCEPTED);
        ride.setAcceptedAt(LocalDateTime.now());

        Ride updatedRide = rideRepository.save(ride);
        logger.info("Ride {} accepted by driver {}", rideId, ride.getDriverId());
        return RideResponse.fromEntity(updatedRide);
    }

    @Transactional
    public RideResponse startRide(Long rideId, UserPrincipal currentUser, String token) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw new InvalidRideStateException("Cannot start ride in state: " + ride.getStatus() + ". Ride must be in ACCEPTED state.");
        }

        validateDriverAction(ride, currentUser, token, "start");

        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(LocalDateTime.now());

        Ride updatedRide = rideRepository.save(ride);
        logger.info("Ride {} started by driver {}", rideId, ride.getDriverId());
        return RideResponse.fromEntity(updatedRide);
    }

    @Transactional
    public RideResponse completeRide(Long rideId, UserPrincipal currentUser, String token) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidRideStateException("Cannot complete ride in state: " + ride.getStatus() + ". Ride must be in IN_PROGRESS state.");
        }

        validateDriverAction(ride, currentUser, token, "complete");

        // Interservice interaction with Service 4 (Fare Service) for finalized fare calculation
        try {
            FareEstimateDto finalFare = fareServiceClient.finalizeFare(
                    ride.getId(),
                    ride.getPickupLocation(),
                    ride.getDestinationLocation()
            );
            if (finalFare != null && finalFare.getTotalFare() != null) {
                ride.setFareAmount(finalFare.getTotalFare());
                ride.setFareReference(finalFare.getFareReference());
            }
        } catch (Exception e) {
            logger.warn("Could not finalize fare via Fare Service: {}", e.getMessage());
        }

        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        Ride updatedRide = rideRepository.save(ride);
        logger.info("Ride {} completed successfully", rideId);
        return RideResponse.fromEntity(updatedRide);
    }

    @Transactional
    public RideResponse cancelRide(Long rideId, RideCancelRequest request, UserPrincipal currentUser, String token) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getStatus() == RideStatus.COMPLETED) {
            throw new InvalidRideStateException("Ride is already completed and cannot be cancelled.");
        }
        if (ride.getStatus() == RideStatus.CANCELLED) {
            throw new InvalidRideStateException("Ride is already cancelled.");
        }

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to cancel a ride");
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isPassenger = currentUser.getUserId().equals(ride.getPassengerId());
        boolean isDriver = isUserAssignedDriver(ride.getDriverId(), currentUser, token);

        if (!isAdmin && !isPassenger && !isDriver) {
            throw new AccessDeniedException("Access denied: You are not authorized to cancel this ride.");
        }

        // Role-aware and state-aware cancellation rules
        if (isPassenger && !isAdmin) {
            if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ASSIGNED) {
                throw new InvalidRideStateException("Passenger cannot cancel a ride that has already been accepted or is in progress.");
            }
        } else if (isDriver && !isAdmin) {
            if (ride.getStatus() != RideStatus.ASSIGNED && ride.getStatus() != RideStatus.ACCEPTED) {
                throw new InvalidRideStateException("Driver cannot cancel a ride that is already in progress.");
            }
        }

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(LocalDateTime.now());
        ride.setCancelledBy(isAdmin ? "ADMIN" : (isPassenger ? "PASSENGER" : "DRIVER"));
        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "Cancelled by " + ride.getCancelledBy();
        ride.setCancellationReason(reason);

        Ride updatedRide = rideRepository.save(ride);
        logger.info("Ride {} cancelled by {}", rideId, ride.getCancelledBy());
        return RideResponse.fromEntity(updatedRide);
    }

    private void validateRideAccess(Ride ride, UserPrincipal currentUser, String token) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isPassenger = currentUser.getUserId().equals(ride.getPassengerId());
        boolean isDriver = isUserAssignedDriver(ride.getDriverId(), currentUser, token);

        if (!isAdmin && !isPassenger && !isDriver) {
            throw new AccessDeniedException("Access denied: You are not authorized to view this ride.");
        }
    }

    private void validateDriverAction(Ride ride, UserPrincipal currentUser, String token, String action) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isDriver = isUserAssignedDriver(ride.getDriverId(), currentUser, token);

        if (!isAdmin && !isDriver) {
            throw new AccessDeniedException("Access denied: Only the assigned driver can " + action + " this ride.");
        }
    }

    private boolean isUserAssignedDriver(Long assignedDriverId, UserPrincipal currentUser, String token) {
        if (assignedDriverId == null || currentUser == null) {
            return false;
        }

        // Direct user ID match
        if (currentUser.getUserId().equals(assignedDriverId)) {
            return true;
        }

        // Query Service 2 (Driver Service) to verify if currentUser.userId owns assignedDriverId
        try {
            DriverDetailsDto driver = driverServiceClient.getDriverById(assignedDriverId, token);
            if (driver != null && currentUser.getUserId().equals(driver.getUserId())) {
                return true;
            }
        } catch (Exception e) {
            logger.debug("Could not verify driver ownership via Driver Service: {}", e.getMessage());
        }

        return false;
    }
}
