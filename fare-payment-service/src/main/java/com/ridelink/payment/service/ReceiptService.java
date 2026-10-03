package com.ridelink.payment.service;

import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.dto.RideDto;
import com.ridelink.payment.entity.Fare;
import com.ridelink.payment.entity.Payment;
import com.ridelink.payment.entity.PaymentStatus;
import com.ridelink.payment.exception.InvalidPaymentStateException;
import com.ridelink.payment.exception.PaymentNotFoundException;
import com.ridelink.payment.exception.ReceiptNotFoundException;
import com.ridelink.payment.repository.FareRepository;
import com.ridelink.payment.repository.PaymentRepository;
import com.ridelink.payment.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class ReceiptService {

    private static final Logger logger = LoggerFactory.getLogger(ReceiptService.class);

    private final PaymentRepository paymentRepository;
    private final FareRepository fareRepository;
    private final RideServiceClient rideServiceClient;

    public ReceiptService(PaymentRepository paymentRepository,
                          FareRepository fareRepository,
                          RideServiceClient rideServiceClient) {
        this.paymentRepository = paymentRepository;
        this.fareRepository = fareRepository;
        this.rideServiceClient = rideServiceClient;
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptByPaymentId(Long paymentId, UserPrincipal currentUser, String token) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

        return buildReceipt(payment, currentUser, token);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptByRideId(Long rideId, UserPrincipal currentUser, String token) {
        Payment payment = paymentRepository.findByRideIdAndStatus(rideId, PaymentStatus.SUCCESS)
                .orElseThrow(() -> new ReceiptNotFoundException("No successful payment receipt found for ride id: " + rideId));

        return buildReceipt(payment, currentUser, token);
    }

    private ReceiptResponse buildReceipt(Payment payment, UserPrincipal currentUser, String token) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidPaymentStateException("Receipt can only be generated for successful payments. Current status: " + payment.getStatus());
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isPassenger = currentUser.getUserId().equals(payment.getPassengerId());

        RideDto ride = null;
        try {
            ride = rideServiceClient.getRideById(payment.getRideId(), token);
        } catch (Exception e) {
            logger.debug("Could not retrieve ride from Ride Service: {}", e.getMessage());
        }

        boolean isDriver = ride != null && currentUser.getUserId().equals(ride.getDriverId());

        if (!isAdmin && !isPassenger && !isDriver) {
            throw new AccessDeniedException("Access denied: You are not authorized to view this receipt");
        }

        // Retrieve linked Fare
        Optional<Fare> fareOpt = payment.getFareId() != null
                ? fareRepository.findById(payment.getFareId())
                : fareRepository.findTopByRideIdOrderByCreatedAtDesc(payment.getRideId());

        String pickup = fareOpt.map(Fare::getPickupLocation)
                .orElse(ride != null ? ride.getPickupLocation() : "Colombo");
        String destination = fareOpt.map(Fare::getDestinationLocation)
                .orElse(ride != null ? ride.getDestinationLocation() : "Destination");
        BigDecimal baseAmount = fareOpt.map(Fare::getBaseAmount).orElse(FareService.BASE_FARE);
        BigDecimal distanceKm = fareOpt.map(Fare::getDistanceKm).orElse(new BigDecimal("5.00"));
        BigDecimal distanceAmount = fareOpt.map(Fare::getDistanceAmount).orElse(new BigDecimal("300.00"));
        int durationMinutes = fareOpt.map(Fare::getDurationMinutes).orElse(15);
        BigDecimal timeAmount = fareOpt.map(Fare::getTimeAmount).orElse(new BigDecimal("75.00"));
        BigDecimal totalAmount = payment.getAmount();

        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now();
        String receiptNumber = "REC-" + payment.getId() + "-" + paidAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        ReceiptResponse receipt = new ReceiptResponse();
        receipt.setReceiptNumber(receiptNumber);
        receipt.setPaymentId(payment.getId());
        receipt.setTransactionReference(payment.getTransactionReference());
        receipt.setRideId(payment.getRideId());
        receipt.setPassengerId(payment.getPassengerId());
        receipt.setPickupLocation(pickup);
        receipt.setDestinationLocation(destination);
        receipt.setBaseAmount(baseAmount);
        receipt.setDistanceKm(distanceKm);
        receipt.setDistanceAmount(distanceAmount);
        receipt.setDurationMinutes(durationMinutes);
        receipt.setTimeAmount(timeAmount);
        receipt.setTotalAmount(totalAmount);
        receipt.setCurrency(payment.getCurrency());
        receipt.setPaymentMethod(payment.getPaymentMethod());
        receipt.setPaymentStatus(payment.getStatus());
        receipt.setPaidAt(paidAt);
        receipt.setIssuedAt(LocalDateTime.now());

        return receipt;
    }
}
