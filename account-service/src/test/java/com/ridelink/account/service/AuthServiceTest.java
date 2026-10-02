package com.ridelink.account.service;

import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.LoginResponse;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.dto.UserResponse;
import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.exception.AccountInactiveException;
import com.ridelink.account.exception.EmailAlreadyExistsException;
import com.ridelink.account.exception.InvalidCredentialsException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setFirstName("John");
        sampleUser.setLastName("Doe");
        sampleUser.setEmail("john.doe@example.com");
        sampleUser.setPasswordHash("$2a$10$hashedPasswordMock");
        sampleUser.setPhone("+94771234567");
        sampleUser.setRole(Role.PASSENGER);
        sampleUser.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should register valid passenger with hashed password and active status")
    void testRegisterValidPassenger() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john.doe@example.com", "Password@123", "+94771234567", Role.PASSENGER
        );

        when(userRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("$2a$10$hashedPasswordMock");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("john.doe@example.com", response.getEmail());
        assertEquals(Role.PASSENGER, response.getRole());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("$2a$10$hashedPasswordMock", saved.getPasswordHash(), "Password must be hashed");
        assertNotEquals("Password@123", saved.getPasswordHash(), "Plain password must never be stored");
    }

    @Test
    @DisplayName("Should register valid driver")
    void testRegisterValidDriver() {
        RegisterRequest request = new RegisterRequest(
                "Driver", "Kamal", "kamal.driver@example.com", "SecurePass1", "+94719876543", Role.DRIVER
        );

        when(userRepository.existsByEmailIgnoreCase("kamal.driver@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$driverHash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertEquals(Role.DRIVER, response.getRole());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());
    }

    @Test
    @DisplayName("Should reject registration with duplicate email")
    void testRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john.doe@example.com", "Password@123", "+94771234567", Role.PASSENGER
        );

        when(userRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should authenticate valid credentials and issue JWT")
    void testLoginValidCredentials() {
        LoginRequest request = new LoginRequest("john.doe@example.com", "Password@123");

        when(userRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password@123", sampleUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getRole())).thenReturn("valid.jwt.token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("valid.jwt.token", response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals(Role.PASSENGER, response.getRole());
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void testRejectWrongPassword() {
        LoginRequest request = new LoginRequest("john.doe@example.com", "WrongPassword");

        when(userRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", sampleUser.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject login for inactive account")
    void testRejectInactiveAccount() {
        sampleUser.setStatus(AccountStatus.INACTIVE);
        LoginRequest request = new LoginRequest("john.doe@example.com", "Password@123");

        when(userRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password@123", sampleUser.getPasswordHash())).thenReturn(true);

        assertThrows(AccountInactiveException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any(), any(), any());
    }
}
