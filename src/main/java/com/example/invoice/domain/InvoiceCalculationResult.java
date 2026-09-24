package com.example.invoice.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result containing line calculations and invoice totals. */
public record InvoiceCalculationResult(
        InvoiceMetadata metadata,
        List<CalculatedLine> lines,
        BigDecimal totalNet,
        BigDecimal totalVat,
        BigDecimal totalGross,
        Map<BigDecimal, VatTotals> totalsByVatRate,
        int validLineCount,
        List<String> warnings) {

        public InvoiceCalculationResult {
                metadata = Objects.requireNonNull(metadata, "metadata");
                lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
                totalsByVatRate = Map.copyOf(Objects.requireNonNull(totalsByVatRate, "totalsByVatRate"));
                warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
}
