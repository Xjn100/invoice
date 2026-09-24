package com.example.invoice.domain;

import java.math.BigDecimal;

/** Calculated values for one invoice line. */
public record CalculatedLine(int lineNumber, String itemCode, String itemName,
                             BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal vatRate, BigDecimal lineNet,
                             BigDecimal lineVat, BigDecimal lineGross,
                             BigDecimal declaredLineTotal, ComparisonStatus comparisonStatus) {
    /** Status of the optional source total comparison. */
    public enum ComparisonStatus { NOT_PROVIDED, MATCH, MISMATCH }
}
