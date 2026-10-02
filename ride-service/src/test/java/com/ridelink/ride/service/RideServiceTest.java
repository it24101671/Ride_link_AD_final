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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private FareServiceClient fareServiceClient;

    @InjectMocks
    private RideService rideService;

    private UserPrincipal passengerPrincipal;
    private UserPrincipal driverPrincipal;
    private UserPrincipal otherPassengerPrincipal;
    private UserPrincipal adminPrincipal;
    private Ride sampleRide;

    @BeforeEach
    void setUp() {
        passengerPrincipal = new UserPrincipal(101L, "passenger@example.com", "PASSENGER");
        driverPrincipal = new UserPrincipal(202L, "driver@example.com", "DRIVER");
        otherPassengerPrincipal = new UserPrincipal(303L, "other@example.com", "PASSENGER");
        adminPrincipal = new UserPrincipal(999L, "admin@ridelink.com", "ADMIN");

        sampleRide = new Ride();
        sampleRide.setId(1L);
        sampleRide.setPassengerId(101L);
        sampleRide.setPickupLocation("Colombo Fort");
        sampleRide.setDestinationLocation("Kandy City Center");
        sampleRide.setStatus(RideStatus.REQUESTED);
        sampleRide.setCreatedAt(LocalDateTime.now());
        sampleRide.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should create ride request in REQUESTED status with estimated fare")
    void testCreateRideSuccess() {
        RideCreateRequest request = new RideCreateRequest("Colombo Fort", "Kandy City Center");
        FareEstimateDto fareEstimate = new FareEstimateDto(new BigDecimal("450.00"), "FARE-12345", "ESTIMATED");

        when(fareServiceClient.estimateFare("Colombo Fort", "Kandy City Center")).thenReturn(fareEstimate);
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        RideResponse response = rideService.createRide(request, passengerPrincipal);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(101L, response.getPassengerId());
        assertEquals(RideStatus.REQUESTED, response.getStatus());
        assertEquals(new BigDecimal("450.00"), response.getFareAmount());
        assertEquals("FARE-12345", response.getFareReference());
        verify(rideRepository).save(any(Ride.class));
    }

    @Test
    @DisplayName("Should reject ride creation when authentication principal is null")
    void testCreateRideWithoutAuthThrowsAccessDenied() {
        RideCreateRequest request = new RideCreateRequest("Colombo", "Kandy");
        assertThrows(AccessDeniedException.class, () -> rideService.createRide(request, null));
    }

    @Test
    @DisplayName("Should retrieve ride by ID for ride owner passenger")
    void testGetRideByIdSuccess() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        RideResponse response = rideService.getRideById(1L, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(101L, response.getPassengerId());
    }

    @Test
    @DisplayName("Should throw RideNotFoundException for non-existent ride ID")
    void testGetRideByIdNotFound() {
        when(rideRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class, () -> rideService.getRideById(999L, passengerPrincipal, "token"));
    }

    @Test
    @DisplayName("Should deny access when another passenger attempts to view private ride")
    void testGetRideByIdAccessDeniedForOtherPassenger() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(AccessDeniedException.class, () -> rideService.getRideById(1L, otherPassengerPrincipal, "token"));
    }

    @Test
    @DisplayName("Should retrieve passenger's ride history")
    void testGetMyRidesSuccess() {
        when(rideRepository.findByPassengerIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(sampleRide));

        List<RideResponse> responses = rideService.getMyRides(passengerPrincipal);

        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).getId());
    }

    @Test
    @DisplayName("Should retrieve assigned rides for the driver")
    void testGetRidesByDriverSuccess() {
        sampleRide.setDriverId(202L);
        when(rideRepository.findByDriverIdOrderByCreatedAtDesc(202L)).thenReturn(List.of(sampleRide));

        List<RideResponse> responses = rideService.getRidesByDriver(202L, driverPrincipal, "token");

        assertEquals(1, responses.size());
        assertEquals(202L, responses.get(0).getDriverId());
    }

    @Test
    @DisplayName("Should assign explicit driver to REQUESTED ride and transition to ASSIGNED")
    void testAssignDriverExplicitSuccess() {
        RideAssignRequest request = new RideAssignRequest(202L);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        doNothing().when(driverServiceClient).verifyDriverEligibility(202L, "token");
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponse response = rideService.assignDriver(1L, request, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.ASSIGNED, response.getStatus());
        assertEquals(202L, response.getDriverId());
        assertNotNull(response.getAssignedAt());
        verify(driverServiceClient).verifyDriverEligibility(202L, "token");
    }

    @Test
    @DisplayName("Should auto-match available driver when no explicit driver ID is provided")
    void testAssignDriverAutoMatchSuccess() {
        EligibleDriverDto driverDto = new EligibleDriverDto(202L, 25L, "Colombo Fort", "AVAILABLE", null, null);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(driverServiceClient.findEligibleDrivers("Colombo Fort")).thenReturn(List.of(driverDto));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponse response = rideService.assignDriver(1L, null, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.ASSIGNED, response.getStatus());
        assertEquals(202L, response.getDriverId());
    }

    @Test
    @DisplayName("Should throw DriverUnavailableException when no available drivers exist")
    void testAssignDriverFailsWhenNoDriverAvailable() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(driverServiceClient.findEligibleDrivers("Colombo Fort")).thenReturn(Collections.emptyList());
        when(driverServiceClient.findEligibleDrivers(null)).thenReturn(Collections.emptyList());

        assertThrows(DriverUnavailableException.class, () -> rideService.assignDriver(1L, null, passengerPrincipal, "token"));
    }

    @Test
    @DisplayName("Should reject driver assignment if ride is not in REQUESTED state")
    void testAssignDriverFailsWhenRideNotInRequestedState() {
        sampleRide.setStatus(RideStatus.ASSIGNED);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.assignDriver(1L, new RideAssignRequest(202L), passengerPrincipal, "token"));
    }

    @Test
    @DisplayName("Driver should successfully accept an ASSIGNED ride")
    void testAcceptRideSuccess() {
        sampleRide.setStatus(RideStatus.ASSIGNED);
        sampleRide.setDriverId(202L);

        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponse response = rideService.acceptRide(1L, driverPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.ACCEPTED, response.getStatus());
        assertNotNull(response.getAcceptedAt());
    }

    @Test
    @DisplayName("Should reject accept when ride is not in ASSIGNED state")
    void testAcceptRideFailsWhenRideNotInAssignedState() {
        sampleRide.setStatus(RideStatus.REQUESTED);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.acceptRide(1L, driverPrincipal, "token"));
    }

    @Test
    @DisplayName("Should reject accept when caller is not the assigned driver")
    void testAcceptRideFailsForUnauthorizedDriver() {
        sampleRide.setStatus(RideStatus.ASSIGNED);
        sampleRide.setDriverId(888L); // Different driver
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(AccessDeniedException.class, () -> rideService.acceptRide(1L, driverPrincipal, "token"));
    }

    @Test
    @DisplayName("Driver should successfully start an ACCEPTED ride")
    void testStartRideSuccess() {
        sampleRide.setStatus(RideStatus.ACCEPTED);
        sampleRide.setDriverId(202L);

        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponse response = rideService.startRide(1L, driverPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.IN_PROGRESS, response.getStatus());
        assertNotNull(response.getStartedAt());
    }

    @Test
    @DisplayName("Should reject start when ride is not in ACCEPTED state")
    void testStartRideFailsWhenRideNotInAcceptedState() {
        sampleRide.setStatus(RideStatus.ASSIGNED);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.startRide(1L, driverPrincipal, "token"));
    }

    @Test
    @DisplayName("Driver should successfully complete an IN_PROGRESS ride and finalize fare")
    void testCompleteRideSuccess() {
        sampleRide.setStatus(RideStatus.IN_PROGRESS);
        sampleRide.setDriverId(202L);

        FareEstimateDto finalFare = new FareEstimateDto(new BigDecimal("550.00"), "FARE-FIN-1", "COMPLETED");
        when(fareServiceClient.finalizeFare(1L, "Colombo Fort", "Kandy City Center")).thenReturn(finalFare);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponse response = rideService.completeRide(1L, driverPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.COMPLETED, response.getStatus());
        assertEquals(new BigDecimal("550.00"), response.getFareAmount());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    @DisplayName("Should reject complete when ride is not in IN_PROGRESS state")
    void testCompleteRideFailsWhenRideNotInProgressState() {
        sampleRide.setStatus(RideStatus.ACCEPTED);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.completeRide(1L, driverPrincipal, "token"));
    }

    @Test
    @DisplayName("Passenger should successfully cancel a REQUESTED ride")
    void testCancelRideByPassengerSuccess() {
        sampleRide.setStatus(RideStatus.REQUESTED);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideCancelRequest request = new RideCancelRequest("Changed travel plans");
        RideResponse response = rideService.cancelRide(1L, request, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.CANCELLED, response.getStatus());
        assertEquals("PASSENGER", response.getCancelledBy());
        assertEquals("Changed travel plans", response.getCancellationReason());
        assertNotNull(response.getCancelledAt());
    }

    @Test
    @DisplayName("Passenger cannot cancel a ride that is already IN_PROGRESS")
    void testCancelRideByPassengerFailsWhenInProgress() {
        sampleRide.setStatus(RideStatus.IN_PROGRESS);
        sampleRide.setDriverId(202L);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.cancelRide(1L, null, passengerPrincipal, "token"));
    }

    @Test
    @DisplayName("Cannot cancel a COMPLETED ride")
    void testCancelRideFailsWhenAlreadyCompleted() {
        sampleRide.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.cancelRide(1L, null, adminPrincipal, "token"));
    }

    @Test
    @DisplayName("Admin can cancel a ride in any active state")
    void testCancelRideByAdminSuccess() {
        sampleRide.setStatus(RideStatus.IN_PROGRESS);
        sampleRide.setDriverId(202L);
        when(rideRepository.findById(1L)).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponse response = rideService.cancelRide(1L, new RideCancelRequest("Admin intervention"), adminPrincipal, "token");

        assertNotNull(response);
        assertEquals(RideStatus.CANCELLED, response.getStatus());
        assertEquals("ADMIN", response.getCancelledBy());
    }
}
