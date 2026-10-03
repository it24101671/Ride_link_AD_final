package com.ridelink.payment.service;

import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.dto.FareCalculationRequest;
import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.FareResponse;
import com.ridelink.payment.dto.RideDto;
import com.ridelink.payment.entity.Fare;
import com.ridelink.payment.entity.FareStatus;
import com.ridelink.payment.exception.FareNotFoundException;
import com.ridelink.payment.repository.FareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FareServiceTest {

    @Mock
    private FareRepository fareRepository;

    @Mock
    private RideServiceClient rideServiceClient;

    @InjectMocks
    private FareService fareService;

    private Fare sampleFare;

    @BeforeEach
    void setUp() {
        sampleFare = new Fare();
        sampleFare.setId(1L);
        sampleFare.setRideId(10L);
        sampleFare.setFareReference("FARE-EST-12345678");
        sampleFare.setPickupLocation("Colombo Fort");
        sampleFare.setDestinationLocation("Kandy City Center");
        sampleFare.setDistanceKm(new BigDecimal("115.00"));
        sampleFare.setDurationMinutes(291);
        sampleFare.setBaseAmount(new BigDecimal("150.00"));
        sampleFare.setDistanceAmount(new BigDecimal("6900.00"));
        sampleFare.setTimeAmount(new BigDecimal("1455.00"));
        sampleFare.setTotalAmount(new BigDecimal("8505.00"));
        sampleFare.setCurrency("LKR");
        sampleFare.setStatus(FareStatus.ESTIMATED);
        sampleFare.setCreatedAt(LocalDateTime.now());
        sampleFare.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should calculate fare estimate using standard formula: base + distance + duration")
    void testEstimateFareSuccess() {
        FareEstimateRequest request = new FareEstimateRequest(
                "Colombo Fort",
                "Galle Face Green",
                new BigDecimal("2.50"),
                10
        );

        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> {
            Fare f = inv.getArgument(0);
            f.setId(100L);
            return f;
        });

        FareResponse response = fareService.estimateFare(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), response.getBaseFare());
        assertEquals(new BigDecimal("2.50"), response.getDistanceKm());
        // 2.50 * 60.00 = 150.00
        assertEquals(new BigDecimal("150.00"), response.getDistanceAmount());
        assertEquals(10, response.getDurationMinutes());
        // 10 * 5.00 = 50.00
        assertEquals(new BigDecimal("50.00"), response.getTimeAmount());
        // 150 + 150 + 50 = 350.00
        assertEquals(new BigDecimal("350.00"), response.getTotalFare());
        assertEquals(new BigDecimal("350.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("350.00"), response.getEstimatedFare());
        assertEquals("ESTIMATED", response.getStatus());
        assertTrue(response.getFareReference().startsWith("FARE-EST-"));
        verify(fareRepository).save(any(Fare.class));
    }

    @Test
    @DisplayName("Should calculate deterministic distance when distance is not provided")
    void testEstimateFareDeterministicDistance() {
        FareEstimateRequest request = new FareEstimateRequest("Colombo Fort", "Bandaranaike International Airport");

        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> {
            Fare f = inv.getArgument(0);
            f.setId(101L);
            return f;
        });

        FareResponse response = fareService.estimateFare(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("32.50"), response.getDistanceKm());
        assertNotNull(response.getTotalFare());
        assertTrue(response.getTotalFare().compareTo(new BigDecimal("150.00")) > 0);
    }

    @Test
    @DisplayName("Should finalize fare for completed ride and persist with FINALIZED status")
    void testCalculateFinalFareSuccess() {
        FareCalculationRequest request = new FareCalculationRequest(
                10L,
                "Colombo Fort",
                "Kandy City Center"
        );
        request.setDistanceKm(new BigDecimal("115.00"));
        request.setDurationMinutes(290);

        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> {
            Fare f = inv.getArgument(0);
            f.setId(200L);
            return f;
        });

        FareResponse response = fareService.calculateFinalFare(request, "token");

        assertNotNull(response);
        assertEquals(10L, response.getRideId());
        assertEquals("FINALIZED", response.getStatus());
        assertTrue(response.getFareReference().startsWith("FARE-FIN-"));
        // 150 + (115 * 60 = 6900) + (290 * 5 = 1450) = 8500.00
        assertEquals(new BigDecimal("8500.00"), response.getTotalFare());
        verify(fareRepository).save(any(Fare.class));
    }

    @Test
    @DisplayName("Should fetch ride details from Ride Service when locations not provided in calculation request")
    void testCalculateFinalFareFetchesFromRideService() {
        FareCalculationRequest request = new FareCalculationRequest(10L);

        RideDto rideDto = new RideDto();
        rideDto.setId(10L);
        rideDto.setPickupLocation("Colombo Fort");
        rideDto.setDestinationLocation("Galle Face Green");

        when(rideServiceClient.getRideById(10L, "token")).thenReturn(rideDto);
        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> {
            Fare f = inv.getArgument(0);
            f.setId(201L);
            return f;
        });

        FareResponse response = fareService.calculateFinalFare(request, "token");

        assertNotNull(response);
        assertEquals("Colombo Fort", response.getPickupLocation());
        assertEquals("Galle Face Green", response.getDestinationLocation());
        verify(rideServiceClient).getRideById(10L, "token");
    }

    @Test
    @DisplayName("Should verify money precision and half-up rounding in fare calculations")
    void testFareMoneyPrecisionAndRounding() {
        FareEstimateRequest request = new FareEstimateRequest(
                "Point A",
                "Point B",
                new BigDecimal("7.3333"),
                17
        );

        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> inv.getArgument(0));

        FareResponse response = fareService.estimateFare(request);

        // Distance rounded to 2 decimals: 7.33
        assertEquals(new BigDecimal("7.33"), response.getDistanceKm());
        // 7.33 * 60.00 = 439.80
        assertEquals(new BigDecimal("439.80"), response.getDistanceAmount());
        // 17 * 5.00 = 85.00
        assertEquals(new BigDecimal("85.00"), response.getTimeAmount());
        // 150.00 + 439.80 + 85.00 = 674.80
        assertEquals(new BigDecimal("674.80"), response.getTotalFare());
        assertEquals(2, response.getTotalFare().scale());
    }

    @Test
    @DisplayName("Should retrieve fare by ID")
    void testGetFareByIdSuccess() {
        when(fareRepository.findById(1L)).thenReturn(Optional.of(sampleFare));

        FareResponse response = fareService.getFareById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("FARE-EST-12345678", response.getFareReference());
    }

    @Test
    @DisplayName("Should throw FareNotFoundException when fare ID does not exist")
    void testGetFareByIdNotFound() {
        when(fareRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(FareNotFoundException.class, () -> fareService.getFareById(999L));
    }

    @Test
    @DisplayName("Should retrieve fare by ride ID")
    void testGetFareByRideIdSuccess() {
        when(fareRepository.findTopByRideIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(sampleFare));

        FareResponse response = fareService.getFareByRideId(10L);

        assertNotNull(response);
        assertEquals(10L, response.getRideId());
    }

    @Test
    @DisplayName("Should throw FareNotFoundException when no fare exists for ride ID")
    void testGetFareByRideIdNotFound() {
        when(fareRepository.findTopByRideIdOrderByCreatedAtDesc(999L)).thenReturn(Optional.empty());

        assertThrows(FareNotFoundException.class, () -> fareService.getFareByRideId(999L));
    }
}
