package com.saed.backend.identity.repository;

import com.saed.backend.identity.dto.AuthData;
import java.util.Optional;

public interface AuthRepository {
    Optional<AuthData> getAuthData(String email);
    void registerLoginFailure(Long userId, String ipAddress);
    void registerLoginSuccess(Long userId, String ipAddress);
}
