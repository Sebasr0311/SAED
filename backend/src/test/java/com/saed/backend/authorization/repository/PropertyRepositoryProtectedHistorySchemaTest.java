package com.saed.backend.authorization.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the property-deletion history checks against schema drift: a predicate that names a column the table does
 * not have fails every deletion request with ORA-00904, so each column used by a PROTECTED_HISTORY predicate must
 * exist on its table according to the migration DDL.
 */
class PropertyRepositoryProtectedHistorySchemaTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+\"?([A-Z0-9_]+)\"?\\s*\\((.*?)\\)\\s*(?:PCTFREE|TABLESPACE|;|\\n/)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CREATE_COLUMN = Pattern.compile(
            "(?m)^\\s*\\(?\\s*\"?([A-Z][A-Z0-9_]*)\"?\\s+(?:NUMBER|VARCHAR2|VARCHAR|NVARCHAR2|CHAR|DATE|TIMESTAMP|CLOB|BLOB|RAW|FLOAT|INTEGER)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_ADD_LIST = Pattern.compile(
            "ALTER\\s+TABLE\\s+\"?([A-Z0-9_]+)\"?\\s+ADD\\s*\\((.*?)\\)\\s*['\\s]*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ALTER_ADD_ONE = Pattern.compile(
            "ALTER\\s+TABLE\\s+\"?([A-Z0-9_]+)\"?\\s+ADD\\s+\"?([A-Z][A-Z0-9_]*)\"?\\s+"
                    + "(?:NUMBER|VARCHAR2|VARCHAR|CHAR|DATE|TIMESTAMP|CLOB|BLOB|RAW|FLOAT|INTEGER|AS)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_COLUMN = Pattern.compile(
            "(?:^|,)\\s*\"?([A-Z][A-Z0-9_]*)\"?\\s+(?:NUMBER|VARCHAR2|VARCHAR|CHAR|DATE|TIMESTAMP|CLOB|BLOB|RAW|FLOAT|INTEGER)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREDICATE_COLUMN = Pattern.compile("\\b([A-Z][A-Z0-9_]*)\\s*(?:=|IN\\b)");
    private static final String UNITS_OF_PROPERTY_COLUMNS = "(SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)";

    private static Map<String, Set<String>> schemaColumns() throws IOException {
        Map<String, Set<String>> columns = new HashMap<>();
        List<Path> files;
        try (Stream<Path> listing = Files.list(Path.of("..", "database", "migrations"))) {
            files = listing.filter(p -> p.getFileName().toString().matches("V\\d.*__.*\\.sql")).toList();
        }
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.ISO_8859_1);
            Matcher create = CREATE_TABLE.matcher(text);
            while (create.find()) {
                Matcher col = CREATE_COLUMN.matcher(create.group(2));
                while (col.find()) {
                    columns.computeIfAbsent(create.group(1).toUpperCase(Locale.ROOT), k -> new HashSet<>())
                            .add(col.group(1).toUpperCase(Locale.ROOT));
                }
            }
            Matcher list = ALTER_ADD_LIST.matcher(text);
            while (list.find()) {
                Matcher col = LIST_COLUMN.matcher(list.group(2));
                while (col.find()) {
                    columns.computeIfAbsent(list.group(1).toUpperCase(Locale.ROOT), k -> new HashSet<>())
                            .add(col.group(1).toUpperCase(Locale.ROOT));
                }
            }
            Matcher one = ALTER_ADD_ONE.matcher(text);
            while (one.find()) {
                columns.computeIfAbsent(one.group(1).toUpperCase(Locale.ROOT), k -> new HashSet<>())
                        .add(one.group(2).toUpperCase(Locale.ROOT));
            }
        }
        return columns;
    }

    /** Column names the predicate compares on the protected table itself (the shared unit subquery is checked separately). */
    private static Set<String> predicateColumns(String predicate) {
        String own = predicate.replace(UNITS_OF_PROPERTY_COLUMNS, "(?)");
        Set<String> used = new HashSet<>();
        Matcher m = PREDICATE_COLUMN.matcher(own.toUpperCase(Locale.ROOT));
        while (m.find()) {
            used.add(m.group(1));
        }
        return used;
    }

    @Test
    void everyColumnUsedByAProtectedHistoryPredicateExistsOnItsTable() throws IOException {
        Map<String, Set<String>> schema = schemaColumns();
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> entry : PropertyRepositoryImpl.protectedHistoryPredicates().entrySet()) {
            Set<String> known = schema.get(entry.getKey());
            assertTrue(known != null && !known.isEmpty(), "No migration DDL found for " + entry.getKey());
            for (String column : predicateColumns(entry.getValue())) {
                if (!known.contains(column)) {
                    offenders.add(entry.getKey() + "." + column);
                }
            }
        }
        assertEquals(List.of(), offenders, "Predicate columns missing from the table DDL");
    }

    @Test
    void theUnitSubqueryColumnsExistOnUnidades() throws IOException {
        Set<String> unidades = schemaColumns().get("UNIDADES");
        assertTrue(unidades.contains("ID_UNIDAD") && unidades.contains("ID_PROPIEDAD"));
    }

    @Test
    void carteraIsScopedByUnitOnly() throws IOException {
        String predicate = PropertyRepositoryImpl.protectedHistoryPredicates().get("CARTERA");
        assertFalse(predicate.replace(UNITS_OF_PROPERTY_COLUMNS, "").toUpperCase(Locale.ROOT).contains("ID_PROPIEDAD"),
                "CARTERA has no ID_PROPIEDAD column: " + predicate);
        assertFalse(schemaColumns().get("CARTERA").contains("ID_PROPIEDAD"));
        assertTrue(predicate.contains("ID_UNIDAD IN"), predicate);
    }
}
