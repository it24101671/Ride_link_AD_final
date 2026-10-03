package com.ridelink.payment.repository;

import com.ridelink.payment.entity.Payment;
import com.ridelink.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionReference(String transactionReference);

    Optional<Payment> findByRideIdAndStatus(Long rideId, PaymentStatus status);

    List<Payment> findByRideIdOrderByCreatedAtDesc(Long rideId);

    Optional<Payment> findTopByRideIdOrderByCreatedAtDesc(Long rideId);

    List<Payment> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);
}
