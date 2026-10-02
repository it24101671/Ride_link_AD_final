package com.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.dto.StatusUpdateRequest;
import com.ridelink.account.dto.UpdateUserRequest;
import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/register - Successfully register passenger")
    void testRegisterPassengerSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Nimal", "Perera", "nimal@example.com", "Secret@123", "+94771234567", Role.PASSENGER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("nimal@example.com")))
                .andExpect(jsonPath("$.role", is("PASSENGER")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register - Reject duplicate email with 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        User existing = new User(null, "Existing", "User", "duplicate@example.com",
                passwordEncoder.encode("Password123"), "+94770000000", Role.PASSENGER, AccountStatus.ACTIVE);
        userRepository.save(existing);

        RegisterRequest request = new RegisterRequest(
                "New", "User", "duplicate@example.com", "Password123", "+94771111111", Role.PASSENGER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("CONFLICT")));
    }

    @Test
    @DisplayName("POST /api/auth/register - Reject invalid email with 400 Bad Request")
    void testRegisterInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Invalid", "Email", "not-a-valid-email", "Password123", "+94771111111", Role.PASSENGER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - Reject blank required field with 400 Bad Request")
    void testRegisterBlankField() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "", "Perera", "valid@example.com", "Password123", "+94771111111", Role.PASSENGER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - Successfully authenticate and return JWT")
    void testLoginSuccess() throws Exception {
        User user = new User(null, "Kamal", "Silva", "kamal@example.com",
                passwordEncoder.encode("Password123"), "+94772222222", Role.DRIVER, AccountStatus.ACTIVE);
        user = userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("kamal@example.com", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.userId", is(user.getId().intValue())))
                .andExpect(jsonPath("$.role", is("DRIVER")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Reject invalid credentials with 401")
    void testLoginWrongPassword() throws Exception {
        User user = new User(null, "Kamal", "Silva", "kamal@example.com",
                passwordEncoder.encode("Password123"), "+94772222222", Role.DRIVER, AccountStatus.ACTIVE);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("kamal@example.com", "WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Reject unauthenticated access with 401")
    void testGetProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/users/{id} - Allow profile owner access with 200")
    void testGetProfileOwner() throws Exception {
        User user = new User(null, "Sunil", "Perera", "sunil@example.com",
                passwordEncoder.encode("Password123"), "+94773333333", Role.PASSENGER, AccountStatus.ACTIVE);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        mockMvc.perform(get("/api/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(user.getId().intValue())))
                .andExpect(jsonPath("$.firstName", is("Sunil")))
                .andExpect(jsonPath("$.email", is("sunil@example.com")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Reject another user accessing profile with 403 Forbidden")
    void testGetProfileForbidden() throws Exception {
        User user1 = userRepository.save(new User(null, "User1", "Test", "u1@example.com",
                passwordEncoder.encode("Pass123"), "+94771111111", Role.PASSENGER, AccountStatus.ACTIVE));
        User user2 = userRepository.save(new User(null, "User2", "Test", "u2@example.com",
                passwordEncoder.encode("Pass123"), "+94772222222", Role.PASSENGER, AccountStatus.ACTIVE));

        String tokenUser1 = jwtService.generateToken(user1.getId(), user1.getEmail(), user1.getRole());

        // User 1 tries to access User 2's profile
        mockMvc.perform(get("/api/users/" + user2.getId())
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Successfully update profile by owner")
    void testUpdateProfileOwner() throws Exception {
        User user = userRepository.save(new User(null, "Original", "Name", "update@example.com",
                passwordEncoder.encode("Pass123"), "+94774444444", Role.PASSENGER, AccountStatus.ACTIVE));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        UpdateUserRequest updateReq = new UpdateUserRequest("Modified", "NameNew", "+94775555555");

        mockMvc.perform(put("/api/users/" + user.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Modified")))
                .andExpect(jsonPath("$.lastName", is("NameNew")))
                .andExpect(jsonPath("$.phone", is("+94775555555")));
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/status - Enforce ADMIN role: Reject PASSENGER with 403")
    void testStatusUpdateNonAdminForbidden() throws Exception {
        User target = userRepository.save(new User(null, "Target", "User", "target@example.com",
                passwordEncoder.encode("Pass123"), "+94771111111", Role.DRIVER, AccountStatus.ACTIVE));
        User passenger = userRepository.save(new User(null, "Passenger", "User", "pass@example.com",
                passwordEncoder.encode("Pass123"), "+94772222222", Role.PASSENGER, AccountStatus.ACTIVE));

        String passengerToken = jwtService.generateToken(passenger.getId(), passenger.getEmail(), passenger.getRole());

        StatusUpdateRequest statusReq = new StatusUpdateRequest(AccountStatus.INACTIVE);

        mockMvc.perform(patch("/api/users/" + target.getId() + "/status")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/status - ADMIN can change status to INACTIVE, preventing login")
    void testStatusUpdateByAdminAndLoginBlock() throws Exception {
        User admin = userRepository.save(new User(null, "Admin", "User", "admin@ridelink.com",
                passwordEncoder.encode("AdminPass123"), "+94770000000", Role.ADMIN, AccountStatus.ACTIVE));
        User target = userRepository.save(new User(null, "Target", "Driver", "targetdriver@example.com",
                passwordEncoder.encode("DriverPass123"), "+94778888888", Role.DRIVER, AccountStatus.ACTIVE));

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), admin.getRole());

        StatusUpdateRequest statusReq = new StatusUpdateRequest(AccountStatus.INACTIVE);

        // ADMIN deactivates driver
        mockMvc.perform(patch("/api/users/" + target.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));

        // Deactivated driver attempts login -> 401 Unauthorized
        LoginRequest loginReq = new LoginRequest("targetdriver@example.com", "DriverPass123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}
