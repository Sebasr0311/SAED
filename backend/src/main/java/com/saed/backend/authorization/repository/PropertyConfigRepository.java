package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.PropertyConfigDTO;

import java.util.List;
import java.util.Optional;

public interface PropertyConfigRepository {
    List<PropertyConfigDTO> findByPropertyId(Long propertyId);
    Optional<PropertyConfigDTO> findByPropertyIdAndKey(Long propertyId, String clave);
    void saveOrUpdate(Long propertyId, String clave, String valor, String descripcion);
    void delete(Long propertyId, String clave);
}
