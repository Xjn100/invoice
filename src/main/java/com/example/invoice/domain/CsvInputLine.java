package com.example.invoice.domain;

import java.math.BigDecimal;

/** Internal parsed representation of one CSV record. */
public record CsvInputLine(int lineNumber, String itemCode, String itemName,
                           BigDecimal quantity, BigDecimal unitPrice,
                           BigDecimal vatRate, BigDecimal declaredLineTotal) {
    public InvoiceLine toInvoiceLine() {
        return new InvoiceLine(lineNumber, itemCode, itemName, quantity, unitPrice, vatRate, declaredLineTotal);
    }
}
