package com.fitnesshub.report;

import java.util.List;
import java.util.stream.Collectors;

/** Minimal RFC 4180-ish CSV writer - no external dependency needed for this simple, well-known shape of data. */
final class CsvWriter {

    private CsvWriter() {
    }

    static String toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(joinRow(header)).append("\n");
        for (List<String> row : rows) {
            sb.append(joinRow(row)).append("\n");
        }
        return sb.toString();
    }

    private static String joinRow(List<String> fields) {
        return fields.stream().map(CsvWriter::escape).collect(Collectors.joining(","));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
