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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class FareService {

    private static final Logger logger = LoggerFactory.getLogger(FareService.class);

    // Standard Fare Calculation Constants
    public static final BigDecimal BASE_FARE = new BigDecimal("150.00");
    public static final BigDecimal DISTANCE_RATE_PER_KM = new BigDecimal("60.00");
    public static final BigDecimal TIME_RATE_PER_MINUTE = new BigDecimal("5.00");
    public static final String CURRENCY = "LKR";

    private final FareRepository fareRepository;
    private final RideServiceClient rideServiceClient;

    public FareService(FareRepository fareRepository, RideServiceClient rideServiceClient) {
        this.fareRepository = fareRepository;
        this.rideServiceClient = rideServiceClient;
    }

    @Transactional
    public FareResponse estimateFare(FareEstimateRequest request) {
        String pickup = request.getPickupLocation().trim();
        String destination = request.getDestinationLocation().trim();

        BigDecimal distanceKm = request.getDistanceKm() != null && request.getDistanceKm().compareTo(BigDecimal.ZERO) > 0
                ? request.getDistanceKm().setScale(2, RoundingMode.HALF_UP)
                : calculateDeterministicDistance(pickup, destination);

        int durationMinutes = request.getDurationMinutes() != null && request.getDurationMinutes() > 0
                ? request.getDurationMinutes()
                : calculateEstimatedDuration(distanceKm);

        BigDecimal distanceAmount = distanceKm.multiply(DISTANCE_RATE_PER_KM).setScale(2, RoundingMode.HALF_UP);
        BigDecimal timeAmount = BigDecimal.valueOf(durationMinutes).multiply(TIME_RATE_PER_MINUTE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = BASE_FARE.add(distanceAmount).add(timeAmount).setScale(2, RoundingMode.HALF_UP);

        String reference = "FARE-EST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Fare fare = new Fare();
        fare.setFareReference(reference);
        fare.setPickupLocation(pickup);
        fare.setDestinationLocation(destination);
        fare.setDistanceKm(distanceKm);
        fare.setDurationMinutes(durationMinutes);
        fare.setBaseAmount(BASE_FARE);
        fare.setDistanceAmount(distanceAmount);
        fare.setTimeAmount(timeAmount);
        fare.setTotalAmount(totalAmount);
        fare.setCurrency(CURRENCY);
        fare.setStatus(FareStatus.ESTIMATED);

        Fare savedFare = fareRepository.save(fare);
        logger.info("Estimated fare calculated: ref={}, total={}", reference, totalAmount);
        return FareResponse.fromEntity(savedFare);
    }

    @Transactional
    public FareResponse calculateFinalFare(FareCalculationRequest request, String token) {
        Long rideId = request.getRideId();
        String pickup = request.getPickupLocation();
        String destination = request.getDestinationLocation();

        // If locations were not passed directly in the request, retrieve ride details from Service 3
        if (pickup == null || destination == null || pickup.isBlank() || destination.isBlank()) {
            try {
                RideDto ride = rideServiceClient.getRideById(rideId, token);
                if (ride != null) {
                    pickup = ride.getPickupLocation();
                    destination = ride.getDestinationLocation();
                }
            } catch (Exception e) {
                logger.warn("Could not fetch ride {} from Ride Service for fare finalization: {}", rideId, e.getMessage());
            }
        }

        if (pickup == null || pickup.isBlank()) {
            pickup = "Origin Location";
        }
        if (destination == null || destination.isBlank()) {
            destination = "Destination Location";
        }

        BigDecimal distanceKm = request.getDistanceKm() != null && request.getDistanceKm().compareTo(BigDecimal.ZERO) > 0
                ? request.getDistanceKm().setScale(2, RoundingMode.HALF_UP)
                : calculateDeterministicDistance(pickup, destination);

        int durationMinutes = request.getDurationMinutes() != null && request.getDurationMinutes() > 0
                ? request.getDurationMinutes()
                : calculateEstimatedDuration(distanceKm);

        BigDecimal distanceAmount = distanceKm.multiply(DISTANCE_RATE_PER_KM).setScale(2, RoundingMode.HALF_UP);
        BigDecimal timeAmount = BigDecimal.valueOf(durationMinutes).multiply(TIME_RATE_PER_MINUTE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = BASE_FARE.add(distanceAmount).add(timeAmount).setScale(2, RoundingMode.HALF_UP);

        String reference = "FARE-FIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Fare fare = new Fare();
        fare.setRideId(rideId);
        fare.setFareReference(reference);
        fare.setPickupLocation(pickup);
        fare.setDestinationLocation(destination);
        fare.setDistanceKm(distanceKm);
        fare.setDurationMinutes(durationMinutes);
        fare.setBaseAmount(BASE_FARE);
        fare.setDistanceAmount(distanceAmount);
        fare.setTimeAmount(timeAmount);
        fare.setTotalAmount(totalAmount);
        fare.setCurrency(CURRENCY);
        fare.setStatus(FareStatus.FINALIZED);

        Fare savedFare = fareRepository.save(fare);
        logger.info("Final fare calculated for ride {}: ref={}, total={}", rideId, reference, totalAmount);
        return FareResponse.fromEntity(savedFare);
    }

    @Transactional(readOnly = true)
    public FareResponse getFareById(Long id) {
        Fare fare = fareRepository.findById(id)
                .orElseThrow(() -> new FareNotFoundException("Fare not found with id: " + id));
        return FareResponse.fromEntity(fare);
    }

    @Transactional(readOnly = true)
    public FareResponse getFareByRideId(Long rideId) {
        Fare fare = fareRepository.findTopByRideIdOrderByCreatedAtDesc(rideId)
                .orElseThrow(() -> new FareNotFoundException("Fare not found for ride id: " + rideId));
        return FareResponse.fromEntity(fare);
    }

    public BigDecimal calculateDeterministicDistance(String pickup, String dest) {
        if (pickup == null || dest == null) {
            return new BigDecimal("5.00");
        }
        String pLower = pickup.toLowerCase();
        String dLower = dest.toLowerCase();

        if ((pLower.contains("fort") && dLower.contains("face")) || (pLower.contains("face") && dLower.contains("fort"))
                || (pLower.contains("fort") && dLower.contains("green")) || (pLower.contains("green") && dLower.contains("fort"))) {
            return new BigDecimal("2.50");
        }
        if ((pLower.contains("colombo") && dLower.contains("airport")) || (pLower.contains("airport") && dLower.contains("colombo"))) {
            return new BigDecimal("32.50");
        }
        if ((pLower.contains("colombo") && dLower.contains("kandy")) || (pLower.contains("kandy") && dLower.contains("colombo"))) {
            return new BigDecimal("115.00");
        }
        if ((pLower.contains("colombo") && dLower.contains("galle") && !dLower.contains("face"))
                || (pLower.contains("galle") && !pLower.contains("face") && dLower.contains("colombo"))) {
            return new BigDecimal("126.00");
        }
        if ((pLower.contains("nugegoda") && dLower.contains("bambalapitiya")) || (pLower.contains("bambalapitiya") && dLower.contains("nugegoda"))) {
            return new BigDecimal("6.80");
        }

        int hash = Math.abs((pickup.trim() + "->" + dest.trim()).hashCode());
        double km = 4.0 + (hash % 160) / 10.0;
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }

    public int calculateEstimatedDuration(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return 10;
        }
        return Math.max(5, (int) Math.round(distanceKm.doubleValue() * 2.5 + 3.0));
    }
}
