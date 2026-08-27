package com.saed.backend.identity.repository;

import com.saed.backend.identity.dto.AuthData;
import com.saed.backend.identity.dto.AuthUserDTO;

import java.util.Optional;

public interface AuthRepository {
    Optional<AuthData> getAuthData(String username);
    AuthUserDTO getUserProfile(Long userId);
    void registerLoginFailure(Long userId, String ipAddress);
    void registerLoginSuccess(Long userId, String ipAddress);
}