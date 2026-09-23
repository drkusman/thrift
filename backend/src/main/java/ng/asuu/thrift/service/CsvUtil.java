package ng.asuu.thrift.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal quote/comma-aware CSV tokenizer - no external dependency needed for the small, one-time imports. */
public class CsvUtil {

    public static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        if (text.startsWith("﻿")) text = text.substring(1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else inQuotes = false;
                } else field.append(c);
            } else if (c == '"') inQuotes = true;
            else if (c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row.toArray(new String[0]));
                row = new ArrayList<>();
            } else field.append(c);
        }
        row.add(field.toString());
        if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row.toArray(new String[0]));
        return rows;
    }

    /** Parses with a header row and returns each data row as a lower-cased-header -> value map. */
    public static List<Map<String, String>> parseCsvWithHeader(String text) {
        List<String[]> rows = parseCsv(text);
        if (rows.isEmpty()) return List.of();
        String[] header = rows.get(0);
        List<Map<String, String>> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                map.put(header[c].trim().toLowerCase(), c < row.length ? row[c].trim() : "");
            }
            out.add(map);
        }
        return out;
    }
}
