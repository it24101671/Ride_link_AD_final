package com.ridelink.payment.service;

import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.dto.PaymentRequest;
import com.ridelink.payment.dto.PaymentResponse;
import com.ridelink.payment.dto.RideDto;
import com.ridelink.payment.entity.Fare;
import com.ridelink.payment.entity.Payment;
import com.ridelink.payment.entity.PaymentMethod;
import com.ridelink.payment.entity.PaymentStatus;
import com.ridelink.payment.exception.DuplicatePaymentException;
import com.ridelink.payment.exception.InvalidPaymentStateException;
import com.ridelink.payment.exception.PaymentNotFoundException;
import com.ridelink.payment.exception.RideNotFoundException;
import com.ridelink.payment.exception.RideServiceUnavailableException;
import com.ridelink.payment.repository.FareRepository;
import com.ridelink.payment.repository.PaymentRepository;
import com.ridelink.payment.security.UserPrincipal;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private FareRepository fareRepository;

    @Mock
    private RideServiceClient rideServiceClient;

    @InjectMocks
    private PaymentService paymentService;

    private UserPrincipal passengerPrincipal;
    private UserPrincipal otherPassengerPrincipal;
    private UserPrincipal driverPrincipal;
    private UserPrincipal adminPrincipal;
    private RideDto completedRide;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        passengerPrincipal = new UserPrincipal(101L, "passenger@example.com", "PASSENGER");
        otherPassengerPrincipal = new UserPrincipal(202L, "other@example.com", "PASSENGER");
        driverPrincipal = new UserPrincipal(303L, "driver@example.com", "DRIVER");
        adminPrincipal = new UserPrincipal(999L, "admin@ridelink.com", "ADMIN");

        completedRide = new RideDto();
        completedRide.setId(1L);
        completedRide.setPassengerId(101L);
        completedRide.setDriverId(303L);
        completedRide.setPickupLocation("Colombo Fort");
        completedRide.setDestinationLocation("Kandy City Center");
        completedRide.setStatus("COMPLETED");
        completedRide.setFareAmount(new BigDecimal("550.00"));
        completedRide.setFareReference("FARE-FIN-12345");

        samplePayment = new Payment();
        samplePayment.setId(10L);
        samplePayment.setRideId(1L);
        samplePayment.setPassengerId(101L);
        samplePayment.setAmount(new BigDecimal("550.00"));
        samplePayment.setCurrency("LKR");
        samplePayment.setPaymentMethod(PaymentMethod.CARD);
        samplePayment.setStatus(PaymentStatus.SUCCESS);
        samplePayment.setTransactionReference("TXN-1234567890ABCDEF");
        samplePayment.setPaymentReference("CARD-TOKEN-4242");
        samplePayment.setPaidAt(LocalDateTime.now());
        samplePayment.setCreatedAt(LocalDateTime.now());
        samplePayment.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should successfully process simulated payment for a COMPLETED ride")
    void testCreatePaymentSuccess() {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD, new BigDecimal("550.00"), "tok_visa_valid", false);

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(completedRide);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getRideId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("550.00"), response.getAmount());
        assertEquals("LKR", response.getCurrency());
        assertEquals(PaymentMethod.CARD, response.getPaymentMethod());
        assertNotNull(response.getTransactionReference());
        assertTrue(response.getTransactionReference().startsWith("TXN-"));
        assertNotNull(response.getPaidAt());
        assertNull(response.getFailureReason());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should simulate payment failure when simulateFailure flag or fail token is provided")
    void testCreatePaymentFailed() {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD, new BigDecimal("550.00"), "tok_fail", true);

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(completedRide);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertNotNull(response.getFailureReason());
        assertTrue(response.getFailureReason().contains("declined"));
        assertNull(response.getPaidAt());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should prevent duplicate payments when a successful payment already exists for the ride")
    void testCreatePaymentDuplicatePrevention() {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.of(samplePayment));

        assertThrows(DuplicatePaymentException.class, () ->
                paymentService.createPayment(request, passengerPrincipal, "token")
        );
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should reject payment when Ride Service reports ride not found")
    void testCreatePaymentRideNotFound() {
        PaymentRequest request = new PaymentRequest(999L, PaymentMethod.CARD);

        when(paymentRepository.findByRideIdAndStatus(999L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(999L, "token")).thenThrow(new RideNotFoundException("Ride not found with id: 999"));

        assertThrows(RideNotFoundException.class, () ->
                paymentService.createPayment(request, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should reject payment when ride is not in COMPLETED state")
    void testCreatePaymentRideNotCompleted() {
        completedRide.setStatus("IN_PROGRESS");
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(completedRide);

        assertThrows(InvalidPaymentStateException.class, () ->
                paymentService.createPayment(request, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should deny payment creation when another passenger attempts to pay")
    void testCreatePaymentUnauthorizedUser() {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(completedRide);

        assertThrows(AccessDeniedException.class, () ->
                paymentService.createPayment(request, otherPassengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Admin should be authorized to create payment on behalf of a ride")
    void testCreatePaymentAdminSuccess() {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CASH, new BigDecimal("550.00"));

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(completedRide);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(102L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request, adminPrincipal, "token");

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
    }

    @Test
    @DisplayName("Should handle Ride Service unavailable error gracefully")
    void testCreatePaymentRideServiceUnavailable() {
        PaymentRequest request = new PaymentRequest(1L, PaymentMethod.CARD);

        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(rideServiceClient.getRideById(1L, "token"))
                .thenThrow(new RideServiceUnavailableException("Ride Service is currently unavailable. Please try again later."));

        assertThrows(RideServiceUnavailableException.class, () ->
                paymentService.createPayment(request, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should retrieve payment by ID for the authorized passenger")
    void testGetPaymentByIdSuccess() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));

        PaymentResponse response = paymentService.getPaymentById(10L, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(1L, response.getRideId());
    }

    @Test
    @DisplayName("Should deny access when another passenger attempts to retrieve private payment")
    void testGetPaymentByIdForbidden() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(completedRide);

        assertThrows(AccessDeniedException.class, () ->
                paymentService.getPaymentById(10L, otherPassengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment ID does not exist")
    void testGetPaymentByIdNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () ->
                paymentService.getPaymentById(999L, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should retrieve payment by ride ID")
    void testGetPaymentByRideIdSuccess() {
        when(paymentRepository.findTopByRideIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(samplePayment));

        PaymentResponse response = paymentService.getPaymentByRideId(1L, passengerPrincipal, "token");

        assertNotNull(response);
        assertEquals(1L, response.getRideId());
    }
}
