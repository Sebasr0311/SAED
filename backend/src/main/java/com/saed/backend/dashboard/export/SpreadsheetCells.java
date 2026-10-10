package com.saed.backend.dashboard.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Cell rendering rules shared by the CSV and XLSX writers (F11-04).
 *
 * <p>Text cells whose first character is a spreadsheet formula trigger ({@code = + - @}, tab or carriage
 * return) are prefixed with an apostrophe so that Excel, LibreOffice or Google Sheets never evaluate them
 * (CSV/formula injection, OWASP). Numbers are emitted as plain numbers and are never prefixed; dates are
 * ISO-8601 text so that they read the same in every locale.
 */
public final class SpreadsheetCells {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SpreadsheetCells() {
    }

    /** Prefixes text that a spreadsheet would interpret as a formula. */
    public static String neutralize(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + text;
        }
        return text;
    }

    /** True when the value is written as a numeric cell. */
    public static boolean isNumeric(Object value) {
        return value instanceof Number;
    }

    /** Plain (non-scientific) representation of a numeric value. */
    public static String number(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    /** Text representation of a non-numeric value, neutralized against formula injection. */
    public static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE);
        }
        return neutralize(String.valueOf(value));
    }
}
