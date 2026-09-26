


package com.ridelink.account.dto;

import com.ridelink.account.entity.Role;

public class LoginResponse {

    private String token;
    private Long userId;
    private Role role;

    public LoginResponse() {
    }

    public LoginResponse(String token, Long userId, Role role) {
        this.token = token;
        this.userId = userId;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
