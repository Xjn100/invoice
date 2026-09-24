package com.example.invoice.service;

import com.example.invoice.domain.CalculatedLine;
import com.example.invoice.domain.InvoiceLine;
import com.example.invoice.domain.InvoiceMetadata;
import com.example.invoice.domain.VatTotals;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Performs monetary calculations for a single validated invoice line. */
public final class VatCalculator {
    /** Calculates one line using the metadata price and rounding policy. */
    public CalculatedLine calculate(InvoiceLine line, InvoiceMetadata metadata) {
        return calculate(line, metadata, BigDecimal.ZERO);
    }

    /** Calculates one line and applies the caller's declared-total tolerance. */
    public CalculatedLine calculate(InvoiceLine line, InvoiceMetadata metadata, BigDecimal tolerance) {
        int scale = metadata.moneyScale();
        RoundingMode rounding = metadata.roundingMode();
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal rateFactor = line.vatRate().divide(hundred, 12, rounding);
        BigDecimal net;
        BigDecimal vat;
        BigDecimal gross;
        if (metadata.priceIncludesVat()) {
            gross = line.quantity().multiply(line.unitPrice()).setScale(scale, rounding);
            net = gross.divide(BigDecimal.ONE.add(rateFactor), scale, rounding);
            vat = gross.subtract(net).setScale(scale, rounding);
        } else {
            net = line.quantity().multiply(line.unitPrice()).setScale(scale, rounding);
            vat = net.multiply(rateFactor).setScale(scale, rounding);
            gross = net.add(vat).setScale(scale, rounding);
        }
        CalculatedLine.ComparisonStatus comparison = compare(line.declaredLineTotal(), gross, tolerance);
        return new CalculatedLine(line.lineNumber(), line.itemCode(), line.itemName(), line.quantity(),
                line.unitPrice(), line.vatRate(), net, vat, gross, line.declaredLineTotal(), comparison);
    }

    /** Creates a VAT-rate aggregate from one calculated line. */
    public VatTotals totals(CalculatedLine line) {
        return new VatTotals(line.lineNet(), line.lineVat(), line.lineGross());
    }

    private CalculatedLine.ComparisonStatus compare(BigDecimal declared, BigDecimal calculated, BigDecimal tolerance) {
        if (declared == null) {
            return CalculatedLine.ComparisonStatus.NOT_PROVIDED;
        }
        return declared.subtract(calculated).abs().compareTo(tolerance) <= 0
                ? CalculatedLine.ComparisonStatus.MATCH : CalculatedLine.ComparisonStatus.MISMATCH;
    }
}
