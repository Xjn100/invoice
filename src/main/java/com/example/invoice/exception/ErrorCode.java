package com.example.invoice.exception;

/** Stable error identifiers exposed to library consumers. */
public enum ErrorCode {
    INVALID_METADATA,
    INVALID_HEADER,
    MISSING_REQUIRED_COLUMN,
    INVALID_FIELD_VALUE,
    INVALID_NUMBER_FORMAT,
    INVALID_VAT_RATE,
    DUPLICATE_LINE_NUMBER,
    UNSUPPORTED_CURRENCY,
    UNSUPPORTED_SCHEMA_VERSION,
    CSV_PARSE_ERROR,
    INPUT_TOO_LARGE,
    DECLARED_TOTAL_MISMATCH
}
