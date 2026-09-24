package com.example.invoice.domain;

import java.math.BigDecimal;

/** Immutable parser, validation and output settings. */
public record CsvProcessingOptions(
        char delimiter,
        BigDecimal declaredTotalTolerance,
        boolean declaredTotalMismatchIsError,
        int maxRows,
        long maxInputCharacters,
        boolean allowZeroQuantity) {

    /** Default options for VND invoice processing. */
    public static CsvProcessingOptions defaults() {
        return new CsvProcessingOptions(',', BigDecimal.ZERO, false, 100_000, 10_000_000L, false);
    }

    /** Validates options before processing. */
    public CsvProcessingOptions validate() {
        if (delimiter == '"' || delimiter == '\r' || delimiter == '\n') {
            throw new IllegalArgumentException("delimiter is invalid");
        }
        if (declaredTotalTolerance == null || declaredTotalTolerance.signum() < 0) {
            throw new IllegalArgumentException("declaredTotalTolerance must be non-negative");
        }
        if (maxRows <= 0 || maxInputCharacters <= 0) {
            throw new IllegalArgumentException("limits must be positive");
        }
        return this;
    }
}
