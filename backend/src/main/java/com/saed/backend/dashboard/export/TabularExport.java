package com.saed.backend.dashboard.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a header plus rows of typed cells as RFC 4180 CSV (UTF-8 BOM, CRLF) or as a minimal Office Open
 * XML workbook (F11-04). Cells are {@link Number} (numeric cell), date/time or text; text is neutralized
 * against formula injection by {@link SpreadsheetCells}. The XLSX uses inline-string cells only, so no
 * cell is ever a formula, and the output is deterministic for identical input.
 */
public final class TabularExport {

    private static final String UTF8_BOM = "﻿";
    private static final String CRLF = "\r\n";
    /** Fixed entry time (2000-01-01T00:00Z) keeps the archive bytes, and their SHA-256, deterministic. */
    private static final long ZIP_ENTRY_TIME = 946_684_800_000L;
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    private TabularExport() {
    }

    public static byte[] csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder(UTF8_BOM);
        appendCsvLine(sb, List.copyOf(headers));
        for (List<Object> row : rows) {
            appendCsvLine(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvLine(StringBuilder sb, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object value = cells.get(i);
            String raw = SpreadsheetCells.isNumeric(value)
                    ? SpreadsheetCells.number((Number) value)
                    : SpreadsheetCells.text(value);
            sb.append(quoteCsv(raw));
        }
        sb.append(CRLF);
    }

    private static String quoteCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static byte[] xlsx(String sheetName, List<String> headers, List<List<Object>> rows) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", XML_DECL
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            entry(zip, "_rels/.rels", XML_DECL + "<Relationships xmlns=\"" + REL_NS + "\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + DOC_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            entry(zip, "xl/workbook.xml", XML_DECL + "<workbook xmlns=\"" + MAIN_NS + "\" xmlns:r=\"" + DOC_REL + "\">"
                    + "<sheets><sheet name=\"" + xml(sheetName(sheetName)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                    + "</workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", XML_DECL + "<Relationships xmlns=\"" + REL_NS + "\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + DOC_REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"" + DOC_REL + "/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            entry(zip, "xl/styles.xml", XML_DECL + "<styleSheet xmlns=\"" + MAIN_NS + "\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
                    + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "</styleSheet>");
            entry(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
        } catch (IOException e) {
            throw new UncheckedIOException("No fue posible generar el archivo XLSX.", e);
        }
        return bytes.toByteArray();
    }

    private static String sheet(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder(XML_DECL);
        sb.append("<worksheet xmlns=\"").append(MAIN_NS).append("\"><sheetData>");
        appendRow(sb, 1, List.copyOf(headers), true);
        int r = 2;
        for (List<Object> row : rows) {
            appendRow(sb, r++, row, false);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, int rowNumber, List<?> cells, boolean header) {
        sb.append("<row r=\"").append(rowNumber).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            Object value = cells.get(c);
            String ref = column(c) + rowNumber;
            if (value == null) {
                continue;
            }
            if (SpreadsheetCells.isNumeric(value)) {
                sb.append("<c r=\"").append(ref).append("\"><v>")
                  .append(SpreadsheetCells.number((Number) value)).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(header ? " s=\"1\"" : "")
                  .append("><is><t xml:space=\"preserve\">").append(xml(SpreadsheetCells.text(value)))
                  .append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    static String column(int index) {
        StringBuilder name = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            name.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return name.toString();
    }

    private static String sheetName(String name) {
        String cleaned = (name == null || name.isBlank() ? "Reporte" : name).replaceAll("[\\[\\]:*?/\\\\]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** XML-escapes text and drops characters that XML 1.0 forbids (they would corrupt the workbook). */
    static String xml(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            switch (cp) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    boolean allowed = cp == 0x9 || cp == 0xA || cp == 0xD
                            || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD)
                            || (cp >= 0x10000 && cp <= 0x10FFFF);
                    if (allowed) {
                        sb.appendCodePoint(cp);
                    }
                }
            }
        });
        return sb.toString();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ZIP_ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
