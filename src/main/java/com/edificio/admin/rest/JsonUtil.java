package com.edificio.admin.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, (com.google.gson.JsonSerializer<LocalDate>)
                    (src, type, ctx) -> src == null ? null : new com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE)))
            .registerTypeAdapter(LocalDate.class, (com.google.gson.JsonDeserializer<LocalDate>)
                    (src, type, ctx) -> src == null ? null : LocalDate.parse(src.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE))
            .registerTypeAdapter(LocalDateTime.class, (com.google.gson.JsonSerializer<LocalDateTime>)
                    (src, type, ctx) -> src == null ? null : new com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class, (com.google.gson.JsonDeserializer<LocalDateTime>)
                    (src, type, ctx) -> src == null ? null : LocalDateTime.parse(src.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /** Extrae un valor entero de un query string (ej: "contrato=5" → 5). Retorna null si no existe o no es numérico. */
    public static Integer extraerInt(String query, String paramName) {
        String val = extraerValor(query, paramName);
        if (val == null) return null;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return null; }
    }

    /** Extrae un valor string de un query string (ej: "documento=123" → "123"). Retorna null si no existe. */
    public static String extraerValor(String query, String paramName) {
        if (query == null || paramName == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && paramName.equals(pair.substring(0, eq))) {
                try { return java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"); }
                catch (java.io.UnsupportedEncodingException e) { return pair.substring(eq + 1); }
            }
        }
        return null;
    }
}
