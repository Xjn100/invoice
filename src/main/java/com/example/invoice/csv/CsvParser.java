package com.example.invoice.csv;

import com.example.invoice.domain.CsvProcessingOptions;
import com.example.invoice.domain.InvoiceLine;
import com.example.invoice.exception.ErrorCode;
import com.example.invoice.exception.InvoiceCalculationException;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parses the documented invoice CSV format without relying on platform locale. */
public final class CsvParser {
    private static final List<String> REQUIRED = List.of("linenumber", "itemcode", "itemname", "quantity", "unitprice", "vatrate");
    private static final String DECLARED_TOTAL = "declaredlinetotal";

    /** Parses all invoice lines and preserves their input order. */
    public List<InvoiceLine> parse(Reader reader, CsvProcessingOptions options) {
        if (reader == null) {
            throw new IllegalArgumentException("reader is required");
        }
        CsvProcessingOptions checkedOptions = options == null ? CsvProcessingOptions.defaults() : options;
        checkedOptions.validate();
        List<List<String>> records = readRecords(reader.markSupported() ? reader : new BufferedReader(reader), checkedOptions);
        if (records.isEmpty()) {
            throw error(ErrorCode.INVALID_HEADER, "CSV must contain a header", 1, null);
        }
        Map<String, Integer> columns = indexHeader(records.get(0));
        List<InvoiceLine> result = new ArrayList<>();
        Set<Integer> lineNumbers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = records.get(index);
            if (row.size() == 1 && row.get(0).isBlank()) {
                continue;
            }
            long csvLine = index + 1L;
            if (row.size() != columns.size()) {
                throw error(ErrorCode.INVALID_FIELD_VALUE, "CSV row has an unexpected number of columns", csvLine, null);
            }
            int lineNumber = parsePositiveInt(value(row, columns, "linenumber"), csvLine, "lineNumber");
            if (!lineNumbers.add(lineNumber)) {
                throw error(ErrorCode.DUPLICATE_LINE_NUMBER, "Duplicate lineNumber: " + lineNumber, csvLine, "lineNumber");
            }
            String itemCode = requiredText(value(row, columns, "itemcode"), csvLine, "itemCode", 100);
            String itemName = requiredText(value(row, columns, "itemname"), csvLine, "itemName", 500);
            BigDecimal quantity = parseDecimal(value(row, columns, "quantity"), csvLine, "quantity");
            BigDecimal unitPrice = parseDecimal(value(row, columns, "unitprice"), csvLine, "unitPrice");
            BigDecimal vatRate = parseVatRate(value(row, columns, "vatrate"), csvLine);
            if (quantity.signum() < 0 || unitPrice.signum() < 0) {
                throw error(ErrorCode.INVALID_FIELD_VALUE, "quantity and unitPrice must not be negative", csvLine, null);
            }
            if (!checkedOptions.allowZeroQuantity() && quantity.signum() == 0) {
                throw error(ErrorCode.INVALID_FIELD_VALUE, "quantity must be greater than zero", csvLine, "quantity");
            }
            BigDecimal declared = null;
            if (columns.containsKey(DECLARED_TOTAL) && !value(row, columns, DECLARED_TOTAL).isBlank()) {
                declared = parseDecimal(value(row, columns, DECLARED_TOTAL), csvLine, "declaredLineTotal");
                if (declared.signum() < 0) {
                    throw error(ErrorCode.INVALID_FIELD_VALUE, "declaredLineTotal must not be negative", csvLine, "declaredLineTotal");
                }
            }
            result.add(new InvoiceLine(lineNumber, itemCode, itemName, quantity, unitPrice, vatRate, declared));
            if (result.size() > checkedOptions.maxRows()) {
                throw error(ErrorCode.INPUT_TOO_LARGE, "CSV contains more than the maximum row count", csvLine, null);
            }
        }
        return List.copyOf(result);
    }

    private List<List<String>> readRecords(Reader reader, CsvProcessingOptions options) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        long consumed = 0;
        try {
            int next;
            while ((next = reader.read()) != -1) {
                consumed++;
                if (consumed > options.maxInputCharacters()) {
                    throw error(ErrorCode.INPUT_TOO_LARGE, "CSV exceeds the maximum input size", records.size() + 1L, null);
                }
                char ch = (char) next;
                if (quoted) {
                    if (ch == '"') {
                        reader.mark(1);
                        int following = reader.read();
                        if (following == '"') {
                            field.append('"');
                            consumed++;
                        } else {
                            quoted = false;
                            quoteClosed = true;
                            if (following != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(ch);
                    }
                } else if (quoteClosed) {
                    if (ch == options.delimiter()) {
                        current.add(field.toString());
                        field.setLength(0);
                        quoteClosed = false;
                    } else if (ch == '\n') {
                        current.add(field.toString());
                        field.setLength(0);
                        records.add(List.copyOf(current));
                        current.clear();
                        quoteClosed = false;
                    } else if (ch == '\r') {
                        // CRLF is normalized by consuming the following LF in the next iteration.
                    } else if (!Character.isWhitespace(ch)) {
                        throw error(ErrorCode.CSV_PARSE_ERROR, "Unexpected character after quoted field", records.size() + 1L, null);
                    }
                } else if (ch == '"' && field.isEmpty()) {
                    quoted = true;
                } else if (ch == options.delimiter()) {
                    current.add(field.toString());
                    field.setLength(0);
                } else if (ch == '\n') {
                    current.add(field.toString());
                    field.setLength(0);
                    records.add(List.copyOf(current));
                    current.clear();
                } else if (ch != '\r') {
                    field.append(ch);
                }
            }
            if (quoted) {
                throw error(ErrorCode.CSV_PARSE_ERROR, "Unclosed quoted field", records.size() + 1L, null);
            }
            if (!current.isEmpty() || field.length() > 0 || quoteClosed) {
                current.add(field.toString());
                records.add(List.copyOf(current));
            }
            return records;
        } catch (IOException ex) {
            throw error(ErrorCode.CSV_PARSE_ERROR, "Unable to read CSV: " + ex.getMessage(), records.size() + 1L, null);
        }
    }

    private Map<String, Integer> indexHeader(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            String normalized = header.get(index).trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || columns.put(normalized, index) != null) {
                throw error(ErrorCode.INVALID_HEADER, "Header contains an empty or duplicate column", 1, null);
            }
        }
        for (String required : REQUIRED) {
            if (!columns.containsKey(required)) {
                throw error(ErrorCode.MISSING_REQUIRED_COLUMN, "Missing required column: " + required, 1, required);
            }
        }
        for (String column : columns.keySet()) {
            if (!REQUIRED.contains(column) && !DECLARED_TOTAL.equals(column)) {
                throw error(ErrorCode.INVALID_HEADER, "Unsupported column: " + column, 1, column);
            }
        }
        return Map.copyOf(columns);
    }

    private String value(List<String> row, Map<String, Integer> columns, String column) {
        return row.get(columns.get(column)).trim();
    }

    private String requiredText(String value, long line, String column, int maxLength) {
        if (value.isBlank() || value.length() > maxLength) {
            throw error(ErrorCode.INVALID_FIELD_VALUE, column + " is required and must be at most " + maxLength + " characters", line, column);
        }
        return value;
    }

    private BigDecimal parseDecimal(String value, long line, String column) {
        if (value.isBlank()) {
            throw error(ErrorCode.INVALID_FIELD_VALUE, column + " is required", line, column);
        }
        try {
            if (value.contains(",") || value.contains(" ")) {
                throw new NumberFormatException("locale-formatted number");
            }
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw error(ErrorCode.INVALID_NUMBER_FORMAT, "Invalid decimal for " + column, line, column);
        }
    }

    private BigDecimal parseVatRate(String value, long line) {
        String normalized = value.endsWith("%") ? value.substring(0, value.length() - 1).trim() : value;
        return parseDecimal(normalized, line, "vatRate");
    }

    private int parsePositiveInt(String value, long line, String column) {
        try {
            int number = Integer.parseInt(value);
            if (number <= 0) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException ex) {
            throw error(ErrorCode.INVALID_FIELD_VALUE, column + " must be a positive integer", line, column);
        }
    }

    private InvoiceCalculationException error(ErrorCode code, String message, long line, String column) {
        return InvoiceCalculationException.input(code, message, line, column);
    }
}
