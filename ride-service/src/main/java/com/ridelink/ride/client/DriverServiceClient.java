package com.ridelink.ride.client;

import com.ridelink.ride.dto.DriverDetailsDto;
import com.ridelink.ride.dto.EligibleDriverDto;
import com.ridelink.ride.exception.DriverNotFoundException;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.DriverUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class DriverServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(DriverServiceClient.class);

    private final RestTemplate restTemplate;
    private final String driverServiceUrl;

    public DriverServiceClient(
            RestTemplate restTemplate,
            @Value("${ridelink.driver-service.url:http://localhost:8082}") String driverServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.driverServiceUrl = driverServiceUrl;
    }

    public List<EligibleDriverDto> findEligibleDrivers(String area) {
        String url = driverServiceUrl + "/api/drivers/available";
        if (area != null && !area.isBlank()) {
            url += "?area=" + area.trim();
        }

        try {
            logger.info("Calling Driver Service for available drivers at: {}", url);
            ResponseEntity<List<EligibleDriverDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<EligibleDriverDto>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Collections.emptyList();
        } catch (ResourceAccessException e) {
            logger.error("Driver Service is unreachable at {}: {}", url, e.getMessage());
            throw new DriverServiceUnavailableException("Driver Service is currently unavailable. Please try again later.");
        } catch (HttpServerErrorException e) {
            logger.error("Driver Service returned server error ({}): {}", e.getStatusCode(), e.getMessage());
            throw new DriverServiceUnavailableException("Driver Service encountered an error. Please try again later.");
        } catch (Exception e) {
            logger.error("Error communicating with Driver Service: {}", e.getMessage());
            throw new DriverServiceUnavailableException("Failed to query available drivers: " + e.getMessage());
        }
    }

    public DriverDetailsDto getDriverById(Long driverId, String token) {
        String url = driverServiceUrl + "/api/drivers/" + driverId;

        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            logger.info("Calling Driver Service for driver details: {}", url);
            ResponseEntity<DriverDetailsDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    DriverDetailsDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("Driver with id {} not found in Driver Service", driverId);
            throw new DriverNotFoundException("Driver not found with id: " + driverId);
        } catch (HttpClientErrorException e) {
            logger.error("Client error from Driver Service: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new DriverUnavailableException("Driver service client error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Driver Service is unreachable at {}: {}", url, e.getMessage());
            throw new DriverServiceUnavailableException("Driver Service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            logger.error("Error calling Driver Service: {}", e.getMessage());
            throw new DriverServiceUnavailableException("Failed to verify driver: " + e.getMessage());
        }
    }

    public void verifyDriverEligibility(Long driverId, String token) {
        DriverDetailsDto driver = getDriverById(driverId, token);
        if (driver == null) {
            throw new DriverNotFoundException("Driver not found with id: " + driverId);
        }

        if (!"ACTIVE".equalsIgnoreCase(driver.getStatus())) {
            throw new DriverUnavailableException("Driver with id " + driverId + " is currently INACTIVE");
        }

        if (!"AVAILABLE".equalsIgnoreCase(driver.getAvailability())) {
            throw new DriverUnavailableException("Driver with id " + driverId + " is currently UNAVAILABLE");
        }
    }
}
