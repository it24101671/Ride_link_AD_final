package com.ridelink.driver.repository;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.Driver;
import com.ridelink.driver.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<Driver> findByLicenseNumberIgnoreCase(String licenseNumber);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    List<Driver> findByStatusAndAvailability(DriverStatus status, AvailabilityStatus availability);

    List<Driver> findByStatusAndAvailabilityAndServiceAreaIgnoreCase(
            DriverStatus status, AvailabilityStatus availability, String serviceArea);
}
