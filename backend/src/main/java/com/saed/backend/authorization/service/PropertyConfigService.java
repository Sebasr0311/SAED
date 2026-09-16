package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyConfigDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PropertyConfigService {
    List<PropertyConfigDTO> findAll(Long propertyId);
    Optional<PropertyConfigDTO> findByKey(Long propertyId, String key);
    String getValue(Long propertyId, String key, String defaultValue);
    int getIntValue(Long propertyId, String key, int defaultValue);
    boolean getBooleanValue(Long propertyId, String key, boolean defaultValue);
    void saveOrUpdate(Long propertyId, String key, String value, String descripcion);
    void saveBatch(Long propertyId, Map<String, String> configs);
}
