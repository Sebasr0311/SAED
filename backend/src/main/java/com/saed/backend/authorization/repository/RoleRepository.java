package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.RoleDTO;
import java.util.Optional;

public interface RoleRepository {
    Optional<RoleDTO> findById(Long id);
}
