package com.ridelink.account.service;

import com.ridelink.account.dto.StatusUpdateRequest;
import com.ridelink.account.dto.UpdateUserRequest;
import com.ridelink.account.dto.UserResponse;
import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.exception.UserNotFoundException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UserPrincipal selfPrincipal;
    private UserPrincipal adminPrincipal;
    private UserPrincipal otherPrincipal;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setFirstName("Alice");
        sampleUser.setLastName("Silva");
        sampleUser.setEmail("alice@example.com");
        sampleUser.setPasswordHash("$2a$10$hashed");
        sampleUser.setPhone("+94770000000");
        sampleUser.setRole(Role.PASSENGER);
        sampleUser.setStatus(AccountStatus.ACTIVE);

        selfPrincipal = new UserPrincipal(10L, "alice@example.com", Role.PASSENGER);
        adminPrincipal = new UserPrincipal(99L, "admin@ridelink.com", Role.ADMIN);
        otherPrincipal = new UserPrincipal(20L, "bob@example.com", Role.PASSENGER);
    }

    @Test
    @DisplayName("Should allow user to view their own profile")
    void testGetUserByIdSelf() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserById(10L, selfPrincipal);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Alice", response.getFirstName());
    }

    @Test
    @DisplayName("Should allow ADMIN to view any profile")
    void testGetUserByIdAdmin() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserById(10L, adminPrincipal);

        assertNotNull(response);
        assertEquals(10L, response.getId());
    }

    @Test
    @DisplayName("Should reject non-admin viewing another user's profile")
    void testGetUserByIdForbidden() {
        assertThrows(AccessDeniedException.class, () -> userService.getUserById(10L, otherPrincipal));
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user does not exist")
    void testGetUserNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserById(10L, selfPrincipal));
    }

    @Test
    @DisplayName("Should allow user to update their own profile")
    void testUpdateUserSelf() {
        UpdateUserRequest updateReq = new UpdateUserRequest("Alice Updated", "Silva New", "+94771111111");

        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse response = userService.updateUser(10L, updateReq, selfPrincipal);

        assertNotNull(response);
        assertEquals("Alice Updated", sampleUser.getFirstName());
        assertEquals("+94771111111", sampleUser.getPhone());
    }

    @Test
    @DisplayName("Should reject non-admin updating another user's profile")
    void testUpdateUserForbidden() {
        UpdateUserRequest updateReq = new UpdateUserRequest("Hacked", "User", "+94771111111");

        assertThrows(AccessDeniedException.class, () -> userService.updateUser(10L, updateReq, otherPrincipal));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update account status")
    void testUpdateStatus() {
        StatusUpdateRequest statusReq = new StatusUpdateRequest(AccountStatus.INACTIVE);

        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse response = userService.updateStatus(10L, statusReq);

        assertNotNull(response);
        assertEquals(AccountStatus.INACTIVE, sampleUser.getStatus());
    }
}
