package com.saed.backend.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Universal recursive sanitizer that scrubs sensitive credentials, keys, hashes,
 * tokens, and secrets from audit payloads before persistence.
 * Ensures output is always valid JSON Object/Array or null (compliant with Oracle IS JSON).
 */
@Component
public class AuditSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "pass", "contrasena", "contrasenia", "pwd", "hash", "passwordhash",
            "token", "accesstoken", "refreshtoken", "jwt", "bearer",
            "secret", "eventssecret", "wompisignedsecret", "webhooksecret", "apisecret", "apikey",
            "signature", "checksum", "cvv", "cvc", "cardnumber", "pan", "pin",
            "authorization", "cookie", "set-cookie", "credentials"
    );

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            ".*(password|contrase|token|secret|signature|checksum|cvv|apikey|credential|authorization).*",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper;

    public AuditSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sanitizes any object (DTO, Map, String, List, Exception, Primitive) into a valid JSON string.
     * Complies with Oracle XE IS JSON check constraint (ensures top-level object or array).
     */
    public String sanitizeToJson(Object payload) {
        if (payload == null) {
            return null;
        }

        try {
            Object sanitizedObject = sanitizeObject(payload);
            if (sanitizedObject == null) {
                return null;
            }

            if (sanitizedObject instanceof String str) {
                String trimmed = str.trim();
                if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    try {
                        objectMapper.readTree(trimmed);
                        return trimmed;
                    } catch (Exception ignored) {
                    }
                }
                return objectMapper.writeValueAsString(Map.of("message", trimmed));
            }

            if (isPrimitiveOrWrapper(sanitizedObject.getClass())) {
                return objectMapper.writeValueAsString(Map.of("value", sanitizedObject));
            }

            return objectMapper.writeValueAsString(sanitizedObject);
        } catch (Exception e) {
            return "{\"_sanitization_fallback\":\"" + payload.getClass().getSimpleName() + "\"}";
        }
    }

    /**
     * Recursively sanitizes data structures.
     */
    public Object sanitizeObject(Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    Object parsed = objectMapper.readValue(trimmed, Object.class);
                    return sanitizeObject(parsed);
                } catch (Exception ignored) {
                }
            }
            return str;
        }

        if (object instanceof Map<?, ?> map) {
            Map<String, Object> sanitizedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (isSensitiveKey(key)) {
                    sanitizedMap.put(key, "[PROTECTED]");
                } else {
                    sanitizedMap.put(key, sanitizeObject(entry.getValue()));
                }
            }
            return sanitizedMap;
        }

        if (object instanceof Collection<?> col) {
            List<Object> sanitizedList = new ArrayList<>();
            for (Object item : col) {
                sanitizedList.add(sanitizeObject(item));
            }
            return sanitizedList;
        }

        if (object.getClass().isArray()) {
            Object[] array = (Object[]) object;
            List<Object> sanitizedList = new ArrayList<>();
            for (Object item : array) {
                sanitizedList.add(sanitizeObject(item));
            }
            return sanitizedList;
        }

        if (isPrimitiveOrWrapper(object.getClass())) {
            return object;
        }

        // POJO / DTO: Convert to Map and sanitize
        try {
            Map<String, Object> map = objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
            return sanitizeObject(map);
        } catch (Exception e) {
            return object.toString();
        }
    }

    public boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase().replace("_", "").replace("-", "");
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        return SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz.equals(String.class) ||
                clazz.equals(Number.class) ||
                clazz.equals(Integer.class) ||
                clazz.equals(Long.class) ||
                clazz.equals(Double.class) ||
                clazz.equals(Float.class) ||
                clazz.equals(Boolean.class) ||
                clazz.equals(Character.class) ||
                clazz.equals(Byte.class) ||
                clazz.equals(Short.class) ||
                clazz.isEnum();
    }
}
