package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyConfigDTO;
import com.saed.backend.authorization.repository.PropertyConfigRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.PlanLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PropertyConfigServiceImpl implements PropertyConfigService {

    private static final Logger log = LoggerFactory.getLogger(PropertyConfigServiceImpl.class);

    public static final String KEY_LIMITE_CONVIVIENTES = "LIMITE_CONVIVIENTES_POR_UNIDAD";
    public static final String KEY_TOLERANCIA_MORA = "TOLERANCIA_MORA_DIAS";
    public static final String KEY_HABILITA_QR = "HABILITA_QR";
    public static final String KEY_HABILITA_LPR = "HABILITA_LPR";
    public static final String KEY_PERMITE_MASCOTAS = "PERMITE_MASCOTAS";
    public static final String KEY_VALOR_EXPENSA = "VALOR_EXPENSA_DEFECTO";
    public static final String KEY_FORMATO_NOTIFICACION = "FORMATO_NOTIFICACION";
    public static final String KEY_TIEMPO_MAXIMO_DOMICILIO = "TIEMPO_MAXIMO_DOMICILIO_MINUTOS";
    public static final String KEY_TIEMPO_MAXIMO_VISITA = "TIEMPO_MAXIMO_VISITA_MINUTOS";

    private static final Map<String, ConfigDefinition> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(KEY_LIMITE_CONVIVIENTES, new ConfigDefinition(
                "4", "Límite máximo de convivientes permitidos por unidad habitacional"));
        DEFAULTS.put(KEY_TOLERANCIA_MORA, new ConfigDefinition(
                "30", "Días de gracia antes de aplicar recargos o restricción por mora"));
        DEFAULTS.put(KEY_HABILITA_QR, new ConfigDefinition(
                "true", "Habilita la emisión y validación de códigos QR para visitantes"));
        DEFAULTS.put(KEY_HABILITA_LPR, new ConfigDefinition(
                "false", "Habilita reconocimiento óptico de matrículas vehiculares (LPR)"));
        DEFAULTS.put(KEY_PERMITE_MASCOTAS, new ConfigDefinition(
                "true", "Permite el registro de mascotas en unidades de la copropiedad"));
        DEFAULTS.put(KEY_VALOR_EXPENSA, new ConfigDefinition(
                "0", "Valor de expensa común de administración por defecto para nuevas unidades"));
        DEFAULTS.put(KEY_FORMATO_NOTIFICACION, new ConfigDefinition(
                "EMAIL", "Canal preferente de notificaciones (EMAIL, SMS, INTERNO)"));
        DEFAULTS.put(KEY_TIEMPO_MAXIMO_DOMICILIO, new ConfigDefinition(
                "30", "Tiempo máximo de permanencia para domiciliarios y repartidores en minutos"));
        DEFAULTS.put(KEY_TIEMPO_MAXIMO_VISITA, new ConfigDefinition(
                "240", "Tiempo máximo de permanencia para visitas en minutos"));
    }

    private final PropertyConfigRepository repository;
    private final PlanLimitService planLimitService;

    public PropertyConfigServiceImpl(PropertyConfigRepository repository, PlanLimitService planLimitService) {
        this.repository = repository;
        this.planLimitService = planLimitService;
    }

    private void validatePropertyAccess(Long propertyId) {
        if (propertyId == null) {
            throw new IllegalArgumentException("ID de propiedad requerido");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) return;

        String role = ctx.getRoleCode();
        String scope = ctx.getRoleScope();

        if ("SUPERADMIN".equals(role) || "GLOBAL".equals(scope)) {
            return;
        }

        if ("ORGANIZACION".equals(scope) || "ADMIN_ORGANIZACION".equals(role)) {
            Long userOrgId = ctx.getOrganizationId();
            if (userOrgId == null) {
                throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada: organización no establecida");
            }
            Long propOrgId = planLimitService.getOrganizationIdForProperty(propertyId);
            if (propOrgId == null || !userOrgId.equals(propOrgId)) {
                throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada");
            }
            return;
        }

        Long callerPropId = ctx.getPropertyId();
        if (callerPropId == null || !callerPropId.equals(propertyId)) {
            throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada");
        }
    }

    private void validateValue(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("El valor para la configuración '" + key + "' no puede ser vacío");
        }
        String cleanKey = key.trim().toUpperCase();
        String cleanVal = value.trim();

        if (KEY_LIMITE_CONVIVIENTES.equals(cleanKey)) {
            try {
                int parsed = Integer.parseInt(cleanVal);
                if (parsed <= 0) {
                    throw new IllegalArgumentException("El límite de convivientes debe ser un número entero mayor a cero");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El límite de convivientes debe ser un número entero válido");
            }
        } else if (KEY_TOLERANCIA_MORA.equals(cleanKey)) {
            try {
                int parsed = Integer.parseInt(cleanVal);
                if (parsed < 0) {
                    throw new IllegalArgumentException("La tolerancia de mora debe ser un número entero mayor o igual a cero");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La tolerancia de mora debe ser un número entero válido");
            }
        } else if (KEY_HABILITA_QR.equals(cleanKey) || KEY_HABILITA_LPR.equals(cleanKey) || KEY_PERMITE_MASCOTAS.equals(cleanKey)) {
            if (!cleanVal.equalsIgnoreCase("true") && !cleanVal.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("La configuración '" + key + "' debe ser 'true' o 'false'");
            }
        } else if (KEY_VALOR_EXPENSA.equals(cleanKey)) {
            try {
                double parsed = Double.parseDouble(cleanVal);
                if (parsed < 0) {
                    throw new IllegalArgumentException("El valor de expensa no puede ser negativo");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El valor de expensa debe ser un número válido");
            }
        } else if (KEY_TIEMPO_MAXIMO_DOMICILIO.equals(cleanKey) || KEY_TIEMPO_MAXIMO_VISITA.equals(cleanKey)) {
            try {
                int parsed = Integer.parseInt(cleanVal);
                if (parsed <= 0) {
                    throw new IllegalArgumentException("El tiempo máximo debe ser un número entero mayor a cero");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El tiempo máximo debe ser un número entero válido");
            }
        }
    }

    @Override
    public List<PropertyConfigDTO> findAll(Long propertyId) {
        validatePropertyAccess(propertyId);
        List<PropertyConfigDTO> persisted = repository.findByPropertyId(propertyId);
        Map<String, PropertyConfigDTO> merged = new LinkedHashMap<>();

        // Start with known system defaults
        for (Map.Entry<String, ConfigDefinition> entry : DEFAULTS.entrySet()) {
            PropertyConfigDTO dto = new PropertyConfigDTO();
            dto.setIdPropiedad(propertyId);
            dto.setClave(entry.getKey());
            dto.setValor(entry.getValue().defaultValue);
            dto.setDescripcion(entry.getValue().description);
            merged.put(entry.getKey(), dto);
        }

        // Overlay with persisted database values
        for (PropertyConfigDTO p : persisted) {
            merged.put(p.getClave().toUpperCase(), p);
        }

        return new ArrayList<>(merged.values());
    }

    @Override
    public Optional<PropertyConfigDTO> findByKey(Long propertyId, String key) {
        validatePropertyAccess(propertyId);
        String cleanKey = key.trim().toUpperCase();
        Optional<PropertyConfigDTO> persisted = repository.findByPropertyIdAndKey(propertyId, cleanKey);
        if (persisted.isPresent()) {
            return persisted;
        }
        ConfigDefinition def = DEFAULTS.get(cleanKey);
        if (def != null) {
            PropertyConfigDTO dto = new PropertyConfigDTO();
            dto.setIdPropiedad(propertyId);
            dto.setClave(cleanKey);
            dto.setValor(def.defaultValue);
            dto.setDescripcion(def.description);
            return Optional.of(dto);
        }
        return Optional.empty();
    }

    @Override
    public String getValue(Long propertyId, String key, String defaultValue) {
        return findByKey(propertyId, key).map(PropertyConfigDTO::getValor).orElse(defaultValue);
    }

    @Override
    public int getIntValue(Long propertyId, String key, int defaultValue) {
        String val = getValue(propertyId, key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                log.warn("Config valor no numérico '{}' para clave {}. Fallback {}", val, key, defaultValue);
            }
        }
        return defaultValue;
    }

    @Override
    public boolean getBooleanValue(Long propertyId, String key, boolean defaultValue) {
        String val = getValue(propertyId, key, null);
        if (val != null) {
            return "true".equalsIgnoreCase(val.trim());
        }
        return defaultValue;
    }

    @Override
    @Transactional
    public void saveOrUpdate(Long propertyId, String key, String value, String descripcion) {
        validatePropertyAccess(propertyId);
        String cleanKey = key.trim().toUpperCase();
        validateValue(cleanKey, value);
        String desc = descripcion;
        if (desc == null && DEFAULTS.containsKey(cleanKey)) {
            desc = DEFAULTS.get(cleanKey).description;
        }
        repository.saveOrUpdate(propertyId, cleanKey, value.trim(), desc);
    }

    @Override
    @Transactional
    public void saveBatch(Long propertyId, Map<String, String> configs) {
        validatePropertyAccess(propertyId);
        if (configs == null || configs.isEmpty()) return;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            saveOrUpdate(propertyId, entry.getKey(), entry.getValue(), null);
        }
    }

    private static class ConfigDefinition {
        final String defaultValue;
        final String description;

        ConfigDefinition(String defaultValue, String description) {
            this.defaultValue = defaultValue;
            this.description = description;
        }
    }
}
