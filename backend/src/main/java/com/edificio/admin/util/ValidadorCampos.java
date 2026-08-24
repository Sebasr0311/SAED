package com.edificio.admin.util;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.regex.Pattern;

/**
 * Utilidades de validación y restricción de entrada para formularios JavaFX.
 *
 * <p>Uso típico en {@code initialize()}:
 * <pre>{@code
 *   ValidadorCampos.soloNombres(txtNombres, 100);
 *   ValidadorCampos.soloDigitos(txtPiso, 3);
 *   ValidadorCampos.soloDecimalPositivo(txtValorMensual);
 * }</pre>
 *
 * <p>La normalización y validación de email/nombre se aplica en tiempo de guardado,
 * no durante la edición, para no interrumpir la escritura.
 */
public final class ValidadorCampos {

    // ── Patrones de validación ───────────────────────────────────────────────

    /**
     * Letras (mayúsculas/minúsculas, incluyendo acentos y ñ), espacios,
     * guiones (-) y apóstrofes ('). Pensado para nombres y apellidos.
     */
    private static final Pattern P_NOMBRES =
            Pattern.compile("[A-Za-zÁÉÍÓÚáéíóúÜüÑñÀÈÌÒÙàèìòùÂÊÎÔÛâêîôûÃÕãõÄËÏÖÚäëïöÇç '\\-]*");

    /** Solo dígitos 0-9. */
    private static final Pattern P_DIGITOS = Pattern.compile("[0-9]*");

    /**
     * Alfanumérico: letras (con acentos / ñ), dígitos y guión.
     * Pensado para documentos que admiten letras (CE, Pasaporte, placa).
     */
    private static final Pattern P_ALFANUMERICO =
            Pattern.compile("[A-Za-z0-9ÁÉÍÓÚáéíóúÜüÑñÀÈÌÒÙàèìòùÂÊÎÔÛâêîôûÄËÏÖäëïöÇç\\-]*");

    /** Teléfono: dígitos, signo + y guión. */
    private static final Pattern P_TELEFONO = Pattern.compile("[0-9+\\-]*");

    /**
     * Número decimal positivo: hasta 10 dígitos enteros y hasta 2 decimales.
     * Permite el punto como separador decimal.
     * Acepta la cadena vacía (campo borrado).
     */
    private static final Pattern P_DECIMAL = Pattern.compile("\\d{0,10}(\\.\\d{0,2})?");

    /**
     * Email RFC simplificado (validación en tiempo de guardado, no durante escritura).
     * Insensible a mayúsculas — aplicar {@link #normalizarEmail} antes de validar.
     */
    private static final Pattern P_EMAIL =
            Pattern.compile("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private ValidadorCampos() { /* utilidad estática */ }

    // ── TextFormatters — aplicar en initialize() ─────────────────────────────

    /**
     * Restringe el {@link TextField} a letras (con acentos / ñ), espacios,
     * guiones y apóstrofes; máximo {@code max} caracteres.
     */
    public static void soloNombres(TextField tf, int max) {
        tf.setTextFormatter(new TextFormatter<>(change -> {
            String nuevo = change.getControlNewText();
            if (nuevo.length() <= max && P_NOMBRES.matcher(nuevo).matches()) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Restringe el {@link TextField} a caracteres alfanuméricos (letras con
     * acentos/ñ, dígitos y guión); máximo {@code max} caracteres.
     * Útil para documentos de tipo CE / Pasaporte y placas de vehículo.
     */
    public static void soloAlfanumerico(TextField tf, int max) {
        tf.setTextFormatter(new TextFormatter<>(change -> {
            String nuevo = change.getControlNewText();
            if (nuevo.length() <= max && P_ALFANUMERICO.matcher(nuevo).matches()) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Aplica el {@link TextFormatter} más adecuado al campo de número de documento
     * según el tipo de documento seleccionado:
     * <ul>
     *   <li>CC / TI → solo dígitos (máx. 20)</li>
     *   <li>CE / PP / cualquier otro tipo → alfanumérico (máx. 20)</li>
     *   <li>{@code null} (sin selección) → solo limita a 20 caracteres</li>
     * </ul>
     *
     * @param txtDoc         campo número de documento a configurar
     * @param tipoDocNombre  valor seleccionado en el ComboBox de tipo de documento
     *                       (puede ser {@code null} si aún no se ha elegido)
     */
    public static void aplicarFormatterDocumento(TextField txtDoc, String tipoDocNombre) {
        if (tipoDocNombre == null) {
            limitar(txtDoc, 20);
        } else if (tipoDocNombre.contains("(CC)") || tipoDocNombre.contains("(TI)")) {
            soloDigitos(txtDoc, 20);
        } else {
            soloAlfanumerico(txtDoc, 20);
        }
    }

    /**
     * Restringe el {@link TextField} a dígitos 0-9; máximo {@code max} caracteres.
     * Útil para campos como "piso" o "ID residente".
     */
    public static void soloDigitos(TextField tf, int max) {
        tf.setTextFormatter(new TextFormatter<>(change -> {
            String nuevo = change.getControlNewText();
            if (nuevo.length() <= max && P_DIGITOS.matcher(nuevo).matches()) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Restringe el {@link TextField} a caracteres de teléfono: dígitos, {@code +} y {@code -};
     * máximo {@code max} caracteres.
     */
    public static void soloTelefono(TextField tf, int max) {
        tf.setTextFormatter(new TextFormatter<>(change -> {
            String nuevo = change.getControlNewText();
            if (nuevo.length() <= max && P_TELEFONO.matcher(nuevo).matches()) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Restringe el {@link TextField} a números decimales positivos con hasta 2 decimales
     * (separador {@code .}).  Permite el campo vacío. No aplica límite de longitud explícito
     * porque el patrón ya lo limita (≤12 dígitos totales + punto).
     */
    public static void soloDecimalPositivo(TextField tf) {
        tf.setTextFormatter(new TextFormatter<>(change -> {
            String nuevo = change.getControlNewText();
            if (P_DECIMAL.matcher(nuevo).matches()) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Limita el {@link TextField} a {@code max} caracteres de cualquier tipo.
     * Útil para email, documento alfanumérico, código, referencia de pago, etc.
     */
    public static void limitar(TextField tf, int max) {
        tf.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= max ? change : null));
    }

    /**
     * Restringe el {@link TextField} a caracteres válidos para una dirección de email:
     * cualquier carácter imprimible no-espacio, máximo 254 caracteres.
     * La validación de formato completa (presencia de @, dominio, etc.) se realiza en
     * tiempo de guardado con {@link #validarEmail}.
     */
    public static void soloEmail(TextField tf) {
        tf.setTextFormatter(new TextFormatter<>(change -> {
            String nuevo = change.getControlNewText();
            if (nuevo.length() <= 254 && !nuevo.contains(" ") && !nuevo.contains("\t")) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Limita el {@link TextArea} a {@code max} caracteres de cualquier tipo.
     * Útil para los campos de notas/observaciones.
     */
    public static void limitar(TextArea ta, int max) {
        ta.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= max ? change : null));
    }

    // ── Normalización — aplicar en tiempo de guardado ────────────────────────

    /**
     * Elimina espacios al inicio/final y colapsa múltiples espacios internos a uno solo.
     * Devuelve cadena vacía si la entrada es {@code null}.
     */
    public static String normalizarNombre(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[ ]{2,}", " ");
    }

    /**
     * Normaliza un email: recorta espacios y convierte a minúsculas.
     * Devuelve cadena vacía si la entrada es {@code null}.
     */
    public static String normalizarEmail(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }

    /**
     * Valida el formato de un email (RFC simplificado).
     * Debe recibir la cadena ya normalizada (minúsculas, sin espacios).
     *
     * @param email cadena a validar; si es vacía se considera válida (campo opcional).
     * @return {@code true} si el email tiene formato correcto o está vacío;
     *         {@code false} si tiene contenido pero formato inválido.
     */
    public static boolean validarEmail(String email) {
        if (email == null || email.isEmpty()) return true;   // campo opcional
        return P_EMAIL.matcher(email).matches();
    }
}
