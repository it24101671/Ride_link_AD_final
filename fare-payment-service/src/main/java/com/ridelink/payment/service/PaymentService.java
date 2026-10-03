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
import com.ridelink.payment.repository.FareRepository;
import com.ridelink.payment.repository.PaymentRepository;
import com.ridelink.payment.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final FareRepository fareRepository;
    private final RideServiceClient rideServiceClient;

    public PaymentService(PaymentRepository paymentRepository,
                          FareRepository fareRepository,
                          RideServiceClient rideServiceClient) {
        this.paymentRepository = paymentRepository;
        this.fareRepository = fareRepository;
        this.rideServiceClient = rideServiceClient;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, UserPrincipal currentUser, String token) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to make payment");
        }

        Long rideId = request.getRideId();

        // 1. Prevent duplicate successful payments for the same ride
        Optional<Payment> existingSuccess = paymentRepository.findByRideIdAndStatus(rideId, PaymentStatus.SUCCESS);
        if (existingSuccess.isPresent()) {
            throw new DuplicatePaymentException("Payment has already been successfully processed for ride ID: " + rideId);
        }

        // 2. Fetch and validate Ride from Service 3
        RideDto ride = rideServiceClient.getRideById(rideId, token);
        if (ride == null) {
            throw new InvalidPaymentStateException("Unable to retrieve ride details for ID: " + rideId);
        }

        // 3. Validate Ride state - must be COMPLETED
        if (!"COMPLETED".equalsIgnoreCase(ride.getStatus())) {
            throw new InvalidPaymentStateException("Cannot process payment for ride in state: " + ride.getStatus() + ". Ride must be in COMPLETED state.");
        }

        // 4. Validate Authorization - only ride passenger or ADMIN can pay
        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isPassenger = currentUser.getUserId().equals(ride.getPassengerId());

        if (!isAdmin && !isPassenger) {
            throw new AccessDeniedException("Access denied: You are not authorized to make payment for ride ID: " + rideId);
        }

        // 5. Determine payment amount
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            if (ride.getFareAmount() != null && ride.getFareAmount().compareTo(BigDecimal.ZERO) > 0) {
                amount = ride.getFareAmount();
            } else {
                Optional<Fare> fareOpt = fareRepository.findTopByRideIdOrderByCreatedAtDesc(rideId);
                amount = fareOpt.map(Fare::getTotalAmount).orElse(new BigDecimal("550.00"));
            }
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // Find linked Fare record if available
        Optional<Fare> fareOpt = fareRepository.findTopByRideIdOrderByCreatedAtDesc(rideId);
        Long fareId = fareOpt.map(Fare::getId).orElse(null);

        // 6. Simulate payment processing
        boolean shouldFail = Boolean.TRUE.equals(request.getSimulateFailure()) ||
                "tok_fail".equalsIgnoreCase(request.getPaymentToken());

        String txnRef = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String paymentRef = generateSafePaymentReference(request.getPaymentMethod(), request.getPaymentToken());

        Payment payment = new Payment();
        payment.setRideId(rideId);
        payment.setFareId(fareId);
        payment.setPassengerId(ride.getPassengerId());
        payment.setAmount(amount);
        payment.setCurrency("LKR");
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setTransactionReference(txnRef);
        payment.setPaymentReference(paymentRef);

        if (shouldFail) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Simulated payment gateway declined the transaction");
            logger.warn("Simulated payment failed for ride {}: {}", rideId, txnRef);
        } else {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            payment.setFailureReason(null);
            logger.info("Simulated payment successful for ride {}: txn={}, amount={}", rideId, txnRef, amount);
        }

        Payment savedPayment = paymentRepository.save(payment);
        return PaymentResponse.fromEntity(savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id, UserPrincipal currentUser, String token) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));

        validatePaymentAccess(payment, currentUser, token);
        return PaymentResponse.fromEntity(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByRideId(Long rideId, UserPrincipal currentUser, String token) {
        Payment payment = paymentRepository.findTopByRideIdOrderByCreatedAtDesc(rideId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for ride id: " + rideId));

        validatePaymentAccess(payment, currentUser, token);
        return PaymentResponse.fromEntity(payment);
    }

    private void validatePaymentAccess(Payment payment, UserPrincipal currentUser, String token) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isPassenger = currentUser.getUserId().equals(payment.getPassengerId());

        if (isAdmin || isPassenger) {
            return;
        }

        // Verify if user is the assigned driver for this ride
        try {
            RideDto ride = rideServiceClient.getRideById(payment.getRideId(), token);
            if (ride != null && currentUser.getUserId().equals(ride.getDriverId())) {
                return;
            }
        } catch (Exception e) {
            logger.debug("Could not verify driver access via Ride Service: {}", e.getMessage());
        }

        throw new AccessDeniedException("Access denied: You are not authorized to view this payment");
    }

    private String generateSafePaymentReference(PaymentMethod method, String token) {
        if (method == PaymentMethod.CARD) {
            return token != null && !token.isBlank()
                    ? "CARD-TOKEN-" + Math.abs(token.hashCode() % 10000)
                    : "CARD-MASKED-4242";
        } else if (method == PaymentMethod.WALLET) {
            return "WALLET-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } else {
            return "CASH-VERIFIED";
        }
    }
}
