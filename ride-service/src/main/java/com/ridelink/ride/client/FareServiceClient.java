package com.ridelink.ride.client;

import com.ridelink.ride.dto.FareEstimateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Clean Interservice Boundary for Service 4: Fare & Payment Service.
 *
 * Expected Contract:
 * - Endpoint: POST http://localhost:8084/api/fares/estimate
 * - Request Body: { "pickupLocation": "...", "destinationLocation": "..." }
 * - Response Body: { "estimatedFare": 450.00, "fareReference": "FARE-...", "status": "ESTIMATED" }
 *
 * If Service 4 is not running or not yet implemented by Member 4, this client logs the
 * interface attempt and safely falls back to a calculated default fare to prevent breaking
 * the ride management workflow.
 */
@Component
public class FareServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(FareServiceClient.class);

    private final RestTemplate restTemplate;
    private final String fareServiceUrl;

    public FareServiceClient(
            RestTemplate restTemplate,
            @Value("${ridelink.fare-service.url:http://localhost:8084}") String fareServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.fareServiceUrl = fareServiceUrl;
    }

    public FareEstimateDto estimateFare(String pickupLocation, String destinationLocation) {
        String url = fareServiceUrl + "/api/fares/estimate";
        Map<String, String> requestBody = Map.of(
                "pickupLocation", pickupLocation,
                "destinationLocation", destinationLocation
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            logger.info("Attempting to request fare estimate from Fare Service: {}", url);
            ResponseEntity<FareEstimateDto> response = restTemplate.postForEntity(url, requestEntity, FareEstimateDto.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            logger.warn("Fare Service (Service 4) is currently unreachable at {} ({}). Using fallback calculation.", url, e.getMessage());
        }

        // Clean fallback calculation when Service 4 is not running
        BigDecimal defaultFare = new BigDecimal("450.00");
        String fallbackReference = "FARE-EST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new FareEstimateDto(defaultFare, fallbackReference, "ESTIMATED_OFFLINE");
    }

    public FareEstimateDto finalizeFare(Long rideId, String pickupLocation, String destinationLocation) {
        String url = fareServiceUrl + "/api/fares/calculate";
        Map<String, Object> requestBody = Map.of(
                "rideId", rideId,
                "pickupLocation", pickupLocation,
                "destinationLocation", destinationLocation
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            logger.info("Attempting to finalize fare with Fare Service: {}", url);
            ResponseEntity<FareEstimateDto> response = restTemplate.postForEntity(url, requestEntity, FareEstimateDto.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            logger.warn("Fare Service (Service 4) is unreachable for completion at {} ({}). Using completed estimate.", url, e.getMessage());
        }

        BigDecimal finalFare = new BigDecimal("550.00");
        String finalReference = "FARE-FIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new FareEstimateDto(finalFare, finalReference, "COMPLETED_OFFLINE");
    }
}
