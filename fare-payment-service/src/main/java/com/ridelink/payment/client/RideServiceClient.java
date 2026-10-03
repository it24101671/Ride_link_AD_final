package com.ridelink.payment.client;

import com.ridelink.payment.dto.RideDto;
import com.ridelink.payment.exception.RideNotFoundException;
import com.ridelink.payment.exception.RideServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class RideServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(RideServiceClient.class);

    private final RestTemplate restTemplate;
    private final String rideServiceUrl;

    public RideServiceClient(
            RestTemplate restTemplate,
            @Value("${ridelink.ride-service.url:http://localhost:8083}") String rideServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.rideServiceUrl = rideServiceUrl;
    }

    public RideDto getRideById(Long rideId, String token) {
        String url = rideServiceUrl + "/api/rides/" + rideId;

        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            logger.info("Calling Ride Service for ride details: {}", url);
            ResponseEntity<RideDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RideDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("Ride with id {} not found in Ride Service", rideId);
            throw new RideNotFoundException("Ride not found with id: " + rideId);
        } catch (HttpClientErrorException.Forbidden e) {
            logger.warn("Forbidden access to ride {}: {}", rideId, e.getMessage());
            throw new org.springframework.security.access.AccessDeniedException("Access denied to ride ID: " + rideId);
        } catch (HttpClientErrorException.Unauthorized e) {
            logger.warn("Unauthorized call to Ride Service: {}", e.getMessage());
            throw new RideServiceUnavailableException("Unauthorized communication with Ride Service");
        } catch (ResourceAccessException e) {
            logger.error("Ride Service is unreachable at {}: {}", url, e.getMessage());
            throw new RideServiceUnavailableException("Ride Service is currently unavailable. Please try again later.");
        } catch (HttpServerErrorException e) {
            logger.error("Ride Service returned server error ({}): {}", e.getStatusCode(), e.getMessage());
            throw new RideServiceUnavailableException("Ride Service encountered an error. Please try again later.");
        } catch (Exception e) {
            logger.error("Error communicating with Ride Service: {}", e.getMessage());
            throw new RideServiceUnavailableException("Failed to retrieve ride details from Ride Service: " + e.getMessage());
        }
    }
}
