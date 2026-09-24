package com.example.invoice.domain;

import com.example.invoice.exception.InvoiceCalculationException;
import com.example.invoice.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable invoice context and tax policy supplied by the caller. */
public record InvoiceMetadata(
        String schemaVersion,
        String invoiceId,
        LocalDate invoiceDate,
        String currency,
        String sellerTaxId,
        boolean priceIncludesVat,
        RoundingMode roundingMode,
        int moneyScale,
        String taxPolicyVersion,
        List<BigDecimal> allowedVatRates,
        String buyerTaxId,
        String buyerName,
        String sourceSystem,
        String correlationId,
        String timezone) {

    public InvoiceMetadata {
        allowedVatRates = allowedVatRates == null ? null : List.copyOf(allowedVatRates);
    }

    /** Creates metadata without optional descriptive fields. */
    public InvoiceMetadata(String schemaVersion, String invoiceId, LocalDate invoiceDate,
                           String currency, String sellerTaxId, boolean priceIncludesVat,
                           RoundingMode roundingMode, int moneyScale, String taxPolicyVersion,
                           List<BigDecimal> allowedVatRates) {
        this(schemaVersion, invoiceId, invoiceDate, currency, sellerTaxId, priceIncludesVat,
                roundingMode, moneyScale, taxPolicyVersion, allowedVatRates,
                null, null, null, null, null);
    }

    /** Validates the metadata contract and returns this immutable value. */
    public InvoiceMetadata validate() {
        requireText(schemaVersion, "schemaVersion");
        if (!"1".equals(schemaVersion.trim())) {
            throw InvoiceCalculationException.policy(ErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                    "Unsupported schemaVersion: " + schemaVersion);
        }
        requireText(invoiceId, "invoiceId");
        if (invoiceId.length() > 100) {
            throw invalid("invoiceId must be at most 100 characters");
        }
        Objects.requireNonNull(invoiceDate, "invoiceDate");
        requireText(currency, "currency");
        if (!"VND".equalsIgnoreCase(currency.trim())) {
            throw InvoiceCalculationException.policy(ErrorCode.UNSUPPORTED_CURRENCY,
                    "currency must be VND");
        }
        requireText(sellerTaxId, "sellerTaxId");
        Objects.requireNonNull(roundingMode, "roundingMode");
        if (moneyScale < 0 || moneyScale > 4) {
            throw invalid("moneyScale must be between 0 and 4");
        }
        requireText(taxPolicyVersion, "taxPolicyVersion");
        if (allowedVatRates == null || allowedVatRates.isEmpty()) {
            throw invalid("allowedVatRates must not be empty");
        }
        for (BigDecimal rate : allowedVatRates) {
            if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw invalid("allowedVatRates must contain values from 0 to 100");
            }
        }
        return this;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + " is required");
        }
    }

    private static InvoiceCalculationException invalid(String message) {
        return InvoiceCalculationException.metadata(message);
    }
}
