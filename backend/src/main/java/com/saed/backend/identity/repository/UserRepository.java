package com.saed.backend.identity.repository;

import com.saed.backend.identity.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    void updateFailedAttempts(Long userId, int attempts);
    void updateLastLogin(Long userId);
    void lockUser(Long userId);
}
