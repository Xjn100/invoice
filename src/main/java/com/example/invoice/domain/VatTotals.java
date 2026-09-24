package com.example.invoice.domain;

import java.math.BigDecimal;

/** Aggregated amounts for one VAT rate. */
public record VatTotals(BigDecimal net, BigDecimal vat, BigDecimal gross) {
}
