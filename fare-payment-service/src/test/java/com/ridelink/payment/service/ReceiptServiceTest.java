package com.ridelink.payment.service;

import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.dto.RideDto;
import com.ridelink.payment.entity.Fare;
import com.ridelink.payment.entity.FareStatus;
import com.ridelink.payment.entity.Payment;
import com.ridelink.payment.entity.PaymentMethod;
import com.ridelink.payment.entity.PaymentStatus;
import com.ridelink.payment.exception.InvalidPaymentStateException;
import com.ridelink.payment.exception.PaymentNotFoundException;
import com.ridelink.payment.exception.ReceiptNotFoundException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private FareRepository fareRepository;

    @Mock
    private RideServiceClient rideServiceClient;

    @InjectMocks
    private ReceiptService receiptService;

    private UserPrincipal passengerPrincipal;
    private UserPrincipal otherPassengerPrincipal;
    private UserPrincipal driverPrincipal;
    private UserPrincipal adminPrincipal;
    private Payment samplePayment;
    private Fare sampleFare;
    private RideDto sampleRide;

    @BeforeEach
    void setUp() {
        passengerPrincipal = new UserPrincipal(101L, "passenger@example.com", "PASSENGER");
        otherPassengerPrincipal = new UserPrincipal(202L, "other@example.com", "PASSENGER");
        driverPrincipal = new UserPrincipal(303L, "driver@example.com", "DRIVER");
        adminPrincipal = new UserPrincipal(999L, "admin@ridelink.com", "ADMIN");

        sampleFare = new Fare();
        sampleFare.setId(50L);
        sampleFare.setRideId(1L);
        sampleFare.setFareReference("FARE-FIN-1234");
        sampleFare.setPickupLocation("Colombo Fort");
        sampleFare.setDestinationLocation("Kandy City Center");
        sampleFare.setDistanceKm(new BigDecimal("115.00"));
        sampleFare.setDurationMinutes(290);
        sampleFare.setBaseAmount(new BigDecimal("150.00"));
        sampleFare.setDistanceAmount(new BigDecimal("6900.00"));
        sampleFare.setTimeAmount(new BigDecimal("1450.00"));
        sampleFare.setTotalAmount(new BigDecimal("8500.00"));
        sampleFare.setCurrency("LKR");
        sampleFare.setStatus(FareStatus.FINALIZED);
        sampleFare.setCreatedAt(LocalDateTime.now());

        samplePayment = new Payment();
        samplePayment.setId(10L);
        samplePayment.setRideId(1L);
        samplePayment.setFareId(50L);
        samplePayment.setPassengerId(101L);
        samplePayment.setAmount(new BigDecimal("8500.00"));
        samplePayment.setCurrency("LKR");
        samplePayment.setPaymentMethod(PaymentMethod.CARD);
        samplePayment.setStatus(PaymentStatus.SUCCESS);
        samplePayment.setTransactionReference("TXN-REC-123456");
        samplePayment.setPaymentReference("CARD-TOKEN-4242");
        samplePayment.setPaidAt(LocalDateTime.now());
        samplePayment.setCreatedAt(LocalDateTime.now());

        sampleRide = new RideDto();
        sampleRide.setId(1L);
        sampleRide.setPassengerId(101L);
        sampleRide.setDriverId(303L);
        sampleRide.setPickupLocation("Colombo Fort");
        sampleRide.setDestinationLocation("Kandy City Center");
        sampleRide.setStatus("COMPLETED");
    }

    @Test
    @DisplayName("Should successfully retrieve itemized receipt for successful payment")
    void testGetReceiptByPaymentIdSuccess() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));
        when(fareRepository.findById(50L)).thenReturn(Optional.of(sampleFare));

        ReceiptResponse receipt = receiptService.getReceiptByPaymentId(10L, passengerPrincipal, "token");

        assertNotNull(receipt);
        assertEquals(10L, receipt.getPaymentId());
        assertEquals(1L, receipt.getRideId());
        assertEquals(101L, receipt.getPassengerId());
        assertEquals("Colombo Fort", receipt.getPickupLocation());
        assertEquals("Kandy City Center", receipt.getDestinationLocation());
        assertEquals(new BigDecimal("150.00"), receipt.getBaseAmount());
        assertEquals(new BigDecimal("115.00"), receipt.getDistanceKm());
        assertEquals(new BigDecimal("6900.00"), receipt.getDistanceAmount());
        assertEquals(290, receipt.getDurationMinutes());
        assertEquals(new BigDecimal("1450.00"), receipt.getTimeAmount());
        assertEquals(new BigDecimal("8500.00"), receipt.getTotalAmount());
        assertEquals(PaymentStatus.SUCCESS, receipt.getPaymentStatus());
        assertTrue(receipt.getReceiptNumber().startsWith("REC-10-"));
        assertNotNull(receipt.getPaidAt());
        assertNotNull(receipt.getIssuedAt());
    }

    @Test
    @DisplayName("Should retrieve receipt by ride ID")
    void testGetReceiptByRideIdSuccess() {
        when(paymentRepository.findByRideIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(Optional.of(samplePayment));
        when(fareRepository.findById(50L)).thenReturn(Optional.of(sampleFare));

        ReceiptResponse receipt = receiptService.getReceiptByRideId(1L, passengerPrincipal, "token");

        assertNotNull(receipt);
        assertEquals(1L, receipt.getRideId());
        assertEquals(10L, receipt.getPaymentId());
    }

    @Test
    @DisplayName("Should throw ReceiptNotFoundException when no successful payment exists for ride")
    void testGetReceiptByRideIdNotFound() {
        when(paymentRepository.findByRideIdAndStatus(999L, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());

        assertThrows(ReceiptNotFoundException.class, () ->
                receiptService.getReceiptByRideId(999L, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should reject receipt generation when payment status is not SUCCESS")
    void testGetReceiptFailsWhenPaymentNotSuccess() {
        samplePayment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));

        assertThrows(InvalidPaymentStateException.class, () ->
                receiptService.getReceiptByPaymentId(10L, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment ID does not exist")
    void testGetReceiptPaymentNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () ->
                receiptService.getReceiptByPaymentId(999L, passengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Should deny access when an unauthorized user attempts to view receipt")
    void testGetReceiptUnauthorizedUser() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(sampleRide);

        assertThrows(AccessDeniedException.class, () ->
                receiptService.getReceiptByPaymentId(10L, otherPassengerPrincipal, "token")
        );
    }

    @Test
    @DisplayName("Assigned driver should be authorized to view the payment receipt")
    void testDriverCanViewReceipt() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));
        when(rideServiceClient.getRideById(1L, "token")).thenReturn(sampleRide);
        when(fareRepository.findById(50L)).thenReturn(Optional.of(sampleFare));

        ReceiptResponse receipt = receiptService.getReceiptByPaymentId(10L, driverPrincipal, "token");

        assertNotNull(receipt);
        assertEquals(10L, receipt.getPaymentId());
    }

    @Test
    @DisplayName("Admin should be authorized to view the payment receipt")
    void testAdminCanViewReceipt() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(samplePayment));
        when(fareRepository.findById(50L)).thenReturn(Optional.of(sampleFare));

        ReceiptResponse receipt = receiptService.getReceiptByPaymentId(10L, adminPrincipal, "token");

        assertNotNull(receipt);
        assertEquals(10L, receipt.getPaymentId());
    }
}
