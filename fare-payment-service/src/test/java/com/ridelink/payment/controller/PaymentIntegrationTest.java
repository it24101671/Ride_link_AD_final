package com.ridelink.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.dto.FareCalculationRequest;
import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.PaymentRequest;
import com.ridelink.payment.dto.RideDto;
import com.ridelink.payment.entity.PaymentMethod;
import com.ridelink.payment.repository.FareRepository;
import com.ridelink.payment.repository.PaymentRepository;
import com.ridelink.payment.security.JwtService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FareRepository fareRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private RideServiceClient rideServiceClient;

    private String passengerToken;
    private String otherPassengerToken;
    private String driverToken;
    private String adminToken;

    private final Long passengerUserId = 101L;
    private final Long otherPassengerUserId = 202L;
    private final Long driverUserId = 303L;
    private final Long adminUserId = 999L;

    private RideDto completedRide;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        fareRepository.deleteAll();

        passengerToken = jwtService.generateToken(passengerUserId, "passenger101@example.com", "PASSENGER");
        otherPassengerToken = jwtService.generateToken(otherPassengerUserId, "other202@example.com", "PASSENGER");
        driverToken = jwtService.generateToken(driverUserId, "driver303@example.com", "DRIVER");
        adminToken = jwtService.generateToken(adminUserId, "admin@ridelink.com", "ADMIN");

        completedRide = new RideDto();
        completedRide.setId(1L);
        completedRide.setPassengerId(passengerUserId);
        completedRide.setDriverId(driverUserId);
        completedRide.setPickupLocation("Colombo Fort");
        completedRide.setDestinationLocation("Kandy City Center");
        completedRide.setStatus("COMPLETED");
        completedRide.setFareAmount(new BigDecimal("550.00"));
        completedRide.setFareReference("FARE-FIN-MOCK");

        when(rideServiceClient.getRideById(eq(1L), any())).thenReturn(completedRide);
    }

    @Test
    @DisplayName("POST /api/fares/estimate - Calculate fare estimate without authentication")
    void testEstimateFareEndpoint() throws Exception {
        FareEstimateRequest request = new FareEstimateRequest("Colombo Fort", "Kandy City Center");

        mockMvc.perform(post("/api/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFare", is(150.00)))
                .andExpect(jsonPath("$.distanceKm", is(115.00)))
                .andExpect(jsonPath("$.totalFare", notNullValue()))
                .andExpect(jsonPath("$.fareReference", startsWith("FARE-EST-")))
                .andExpect(jsonPath("$.status", is("ESTIMATED")));
    }

    @Test
    @DisplayName("POST /api/fare/estimate - Alternative route for fare estimate compatibility")
    void testAlternativeFareEstimateRoute() throws Exception {
        FareEstimateRequest request = new FareEstimateRequest("Colombo Fort", "Galle Face Green");

        mockMvc.perform(post("/api/fare/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ESTIMATED")))
                .andExpect(jsonPath("$.distanceKm", is(2.50)))
                .andExpect(jsonPath("$.totalFare", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/fares/calculate - Calculate and persist final fare")
    void testCalculateFinalFareEndpoint() throws Exception {
        FareCalculationRequest request = new FareCalculationRequest(1L, "Colombo Fort", "Kandy City Center");
        request.setDistanceKm(new BigDecimal("115.00"));
        request.setDurationMinutes(290);

        mockMvc.perform(post("/api/fares/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId", is(1)))
                .andExpect(jsonPath("$.status", is("FINALIZED")))
                .andExpect(jsonPath("$.totalFare", is(8500.00)))
                .andExpect(jsonPath("$.fareReference", startsWith("FARE-FIN-")));
    }

    @Test
    @DisplayName("POST /api/fares/estimate - Validation error on blank locations (400)")
    void testEstimateFareValidationError() throws Exception {
        FareEstimateRequest request = new FareEstimateRequest("", "");

        mockMvc.perform(post("/api/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors.pickupLocation", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.destinationLocation", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/payments - Reject payment creation without authentication (401)")
    void testCreatePaymentUnauthorized() throws Exception {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("POST /api/payments - Reject payment creation with missing rideId (400)")
    void testCreatePaymentValidationError() throws Exception {
        PaymentRequest request = new PaymentRequest(null, null);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors.rideId", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.paymentMethod", notNullValue()));
    }

    @Test
    @DisplayName("Full Payment and Receipt Workflow: Create Payment -> Get Payment -> Get Receipt -> Duplicate Prevented")
    void testFullPaymentAndReceiptWorkflow() throws Exception {
        // 1. Finalize fare first
        FareCalculationRequest fareReq = new FareCalculationRequest(1L, "Colombo Fort", "Kandy City Center");
        fareReq.setDistanceKm(new BigDecimal("10.00"));
        fareReq.setDurationMinutes(25);

        mockMvc.perform(post("/api/fares/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fareReq)))
                .andExpect(status().isOk());

        // 2. Passenger pays for the ride
        PaymentRequest payReq = new PaymentRequest(1L, PaymentMethod.CARD, new BigDecimal("875.00"), "tok_visa_4242", false);

        String paymentResp = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.rideId", is(1)))
                .andExpect(jsonPath("$.amount", is(875.00)))
                .andExpect(jsonPath("$.currency", is("LKR")))
                .andExpect(jsonPath("$.paymentMethod", is("CARD")))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.transactionReference", startsWith("TXN-")))
                .andExpect(jsonPath("$.paidAt", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        Long paymentId = objectMapper.readTree(paymentResp).get("id").asLong();

        // 3. Retrieve payment by ID
        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(paymentId.intValue())))
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        // 4. Retrieve payment by ride ID
        mockMvc.perform(get("/api/payments/ride/1")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId", is(1)))
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        // 5. Retrieve receipt by payment ID
        mockMvc.perform(get("/api/receipts/" + paymentId)
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber", startsWith("REC-")))
                .andExpect(jsonPath("$.paymentId", is(paymentId.intValue())))
                .andExpect(jsonPath("$.totalAmount", is(875.00)))
                .andExpect(jsonPath("$.paymentStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.paidAt", notNullValue()));

        // 6. Retrieve receipt by ride ID
        mockMvc.perform(get("/api/receipts/ride/1")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", is(paymentId.intValue())))
                .andExpect(jsonPath("$.rideId", is(1)));

        // 7. Duplicate payment attempt -> 409 Conflict
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("DUPLICATE_PAYMENT")));
    }

    @Test
    @DisplayName("POST /api/payments - Deny payment when another passenger attempts to pay (403)")
    void testCreatePaymentForbiddenForOtherPassenger() throws Exception {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + otherPassengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("POST /api/payments - Reject payment when ride is not in COMPLETED state (400)")
    void testCreatePaymentFailsWhenRideNotCompleted() throws Exception {
        completedRide.setStatus("IN_PROGRESS");
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("INVALID_PAYMENT_STATE")));
    }

    @Test
    @DisplayName("GET /api/payments/{id} - Non-existent payment returns 404")
    void testGetPaymentNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/9999")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("PAYMENT_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/receipts/{paymentId} - Non-existent receipt returns 404")
    void testGetReceiptNotFound() throws Exception {
        mockMvc.perform(get("/api/receipts/9999")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("PAYMENT_NOT_FOUND")));
    }
}
