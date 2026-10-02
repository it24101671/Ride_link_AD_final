package com.ridelink.ride.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.dto.FareEstimateDto;
import com.ridelink.ride.dto.RideAssignRequest;
import com.ridelink.ride.dto.RideCancelRequest;
import com.ridelink.ride.dto.RideCreateRequest;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RideIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private DriverServiceClient driverServiceClient;

    @MockBean
    private FareServiceClient fareServiceClient;

    private String passengerToken;
    private String otherPassengerToken;
    private String driverToken;
    private String adminToken;

    private final Long passengerUserId = 10L;
    private final Long otherPassengerUserId = 11L;
    private final Long driverUserId = 20L;
    private final Long adminUserId = 999L;

    @BeforeEach
    void setUp() {
        rideRepository.deleteAll();

        passengerToken = jwtService.generateToken(passengerUserId, "passenger10@example.com", "PASSENGER");
        otherPassengerToken = jwtService.generateToken(otherPassengerUserId, "other11@example.com", "PASSENGER");
        driverToken = jwtService.generateToken(driverUserId, "driver20@example.com", "DRIVER");
        adminToken = jwtService.generateToken(adminUserId, "admin@ridelink.com", "ADMIN");

        when(fareServiceClient.estimateFare(anyString(), anyString()))
                .thenReturn(new FareEstimateDto(new BigDecimal("450.00"), "FARE-EST-MOCK", "ESTIMATED"));

        when(fareServiceClient.finalizeFare(any(), anyString(), anyString()))
                .thenReturn(new FareEstimateDto(new BigDecimal("550.00"), "FARE-FIN-MOCK", "COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/rides - Reject ride creation without authentication with 401 Unauthorized")
    void testCreateRideUnauthorized() throws Exception {
        RideCreateRequest request = new RideCreateRequest("Colombo Fort", "Kandy");

        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("POST /api/rides - Reject ride creation with blank locations with 400 Validation Error")
    void testCreateRideValidationError() throws Exception {
        RideCreateRequest invalidRequest = new RideCreateRequest("", "");

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors.pickupLocation", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.destinationLocation", notNullValue()));
    }

    @Test
    @DisplayName("Complete Ride Lifecycle: Create -> Assign -> Accept -> Start -> Complete")
    void testFullRideLifecycle() throws Exception {
        // 1. Passenger creates a ride
        RideCreateRequest createReq = new RideCreateRequest("Colombo Fort", "Galle Face Green");
        String createResponse = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.passengerId", is(passengerUserId.intValue())))
                .andExpect(jsonPath("$.pickupLocation", is("Colombo Fort")))
                .andExpect(jsonPath("$.destinationLocation", is("Galle Face Green")))
                .andExpect(jsonPath("$.status", is("REQUESTED")))
                .andExpect(jsonPath("$.fareAmount", is(450.00)))
                .andReturn().getResponse().getContentAsString();

        Long rideId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. Passenger retrieves the ride
        mockMvc.perform(get("/api/rides/" + rideId)
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(rideId.intValue())))
                .andExpect(jsonPath("$.status", is("REQUESTED")));

        // 3. Different passenger attempts to retrieve ride -> 403 Forbidden
        mockMvc.perform(get("/api/rides/" + rideId)
                        .header("Authorization", "Bearer " + otherPassengerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));

        // 4. Assign driver
        doNothing().when(driverServiceClient).verifyDriverEligibility(any(), any());
        RideAssignRequest assignReq = new RideAssignRequest(driverUserId);

        mockMvc.perform(post("/api/rides/" + rideId + "/assign")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.driverId", is(driverUserId.intValue())))
                .andExpect(jsonPath("$.assignedAt", notNullValue()));

        // 5. Negative test: Attempt invalid state transition (start before accept) -> 400 Bad Request
        mockMvc.perform(patch("/api/rides/" + rideId + "/start")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("INVALID_STATE_TRANSITION")));

        // 6. Driver accepts ride -> ACCEPTED
        mockMvc.perform(patch("/api/rides/" + rideId + "/accept")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.acceptedAt", notNullValue()));

        // 7. Driver starts ride -> IN_PROGRESS
        mockMvc.perform(patch("/api/rides/" + rideId + "/start")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.startedAt", notNullValue()));

        // 8. Negative test: Passenger cannot cancel ride while IN_PROGRESS
        mockMvc.perform(patch("/api/rides/" + rideId + "/cancel")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("INVALID_STATE_TRANSITION")));

        // 9. Driver completes ride -> COMPLETED
        mockMvc.perform(patch("/api/rides/" + rideId + "/complete")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.fareAmount", is(550.00)));

        // 10. Negative test: Cannot cancel COMPLETED ride
        mockMvc.perform(patch("/api/rides/" + rideId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("INVALID_STATE_TRANSITION")));
    }

    @Test
    @DisplayName("Passenger should successfully cancel a REQUESTED ride")
    void testPassengerCancelRequestedRide() throws Exception {
        RideCreateRequest createReq = new RideCreateRequest("Nugegoda", "Bambalapitiya");
        String createResp = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long rideId = objectMapper.readTree(createResp).get("id").asLong();

        RideCancelRequest cancelReq = new RideCancelRequest("Passenger decided to take train");
        mockMvc.perform(patch("/api/rides/" + rideId + "/cancel")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.cancelledBy", is("PASSENGER")))
                .andExpect(jsonPath("$.cancellationReason", is("Passenger decided to take train")))
                .andExpect(jsonPath("$.cancelledAt", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/rides/my - Successfully retrieve passenger's ride history")
    void testGetMyRidesEndpoint() throws Exception {
        RideCreateRequest req1 = new RideCreateRequest("A", "B");
        RideCreateRequest req2 = new RideCreateRequest("C", "D");

        mockMvc.perform(post("/api/rides")
                .header("Authorization", "Bearer " + passengerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)));

        mockMvc.perform(post("/api/rides")
                .header("Authorization", "Bearer " + passengerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)));

        mockMvc.perform(get("/api/rides/my")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
