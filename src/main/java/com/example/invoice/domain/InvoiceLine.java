package com.example.invoice.domain;

import java.math.BigDecimal;

/** Validated input line from the invoice CSV. */
public record InvoiceLine(int lineNumber, String itemCode, String itemName,
                          BigDecimal quantity, BigDecimal unitPrice,
                          BigDecimal vatRate, BigDecimal declaredLineTotal) {
}
