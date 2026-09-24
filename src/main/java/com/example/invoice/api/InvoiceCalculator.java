package com.example.invoice.api;

import com.example.invoice.csv.CsvParser;
import com.example.invoice.domain.CalculatedLine;
import com.example.invoice.domain.CsvProcessingOptions;
import com.example.invoice.domain.InvoiceCalculationResult;
import com.example.invoice.domain.InvoiceLine;
import com.example.invoice.domain.InvoiceMetadata;
import com.example.invoice.domain.VatTotals;
import com.example.invoice.exception.ErrorCode;
import com.example.invoice.exception.InvoiceCalculationException;
import com.example.invoice.service.VatCalculator;

import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe facade for calculating invoice VAT from CSV input. */
public final class InvoiceCalculator {
    private final CsvParser parser;
    private final VatCalculator vatCalculator;

    /** Creates a stateless calculator. */
    public InvoiceCalculator() {
        this(new CsvParser(), new VatCalculator());
    }

    InvoiceCalculator(CsvParser parser, VatCalculator vatCalculator) {
        this.parser = parser;
        this.vatCalculator = vatCalculator;
    }

    /** Parses and calculates an invoice, failing before returning partial results. */
    public InvoiceCalculationResult calculate(InvoiceMetadata metadata, Reader input,
                                              CsvProcessingOptions options) {
        if (metadata == null) {
            throw InvoiceCalculationException.metadata("metadata is required");
        }
        InvoiceMetadata checkedMetadata = metadata.validate();
        CsvProcessingOptions checkedOptions = options == null ? CsvProcessingOptions.defaults() : options.validate();
        List<InvoiceLine> lines = parser.parse(input, checkedOptions);
        List<CalculatedLine> calculated = new ArrayList<>();
        Map<BigDecimal, MutableTotals> grouped = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        for (InvoiceLine line : lines) {
            if (!containsRate(checkedMetadata.allowedVatRates(), line.vatRate())) {
                throw InvoiceCalculationException.input(ErrorCode.INVALID_VAT_RATE,
                        "VAT rate is not allowed: " + line.vatRate(), line.lineNumber(), "vatRate");
            }
            CalculatedLine result = vatCalculator.calculate(line, checkedMetadata, checkedOptions.declaredTotalTolerance());
            if (result.comparisonStatus() == CalculatedLine.ComparisonStatus.MISMATCH) {
                String message = "declaredLineTotal differs from calculated lineGross at line " + line.lineNumber();
                if (checkedOptions.declaredTotalMismatchIsError()
                        && line.declaredLineTotal().subtract(result.lineGross()).abs().compareTo(checkedOptions.declaredTotalTolerance()) > 0) {
                    throw InvoiceCalculationException.input(ErrorCode.DECLARED_TOTAL_MISMATCH, message,
                            line.lineNumber(), "declaredLineTotal");
                }
                warnings.add(message);
            }
            calculated.add(result);
            grouped.computeIfAbsent(rateKey(line.vatRate()), ignored -> new MutableTotals()).add(result);
        }
        BigDecimal totalNet = zero(checkedMetadata).add(BigDecimal.ZERO);
        BigDecimal totalVat = zero(checkedMetadata);
        BigDecimal totalGross = zero(checkedMetadata);
        Map<BigDecimal, VatTotals> totalsByRate = new HashMap<>();
        for (Map.Entry<BigDecimal, MutableTotals> entry : grouped.entrySet()) {
            VatTotals totals = entry.getValue().toValue(checkedMetadata);
            totalsByRate.put(entry.getKey(), totals);
            totalNet = totalNet.add(totals.net());
            totalVat = totalVat.add(totals.vat());
            totalGross = totalGross.add(totals.gross());
        }
        return new InvoiceCalculationResult(checkedMetadata, calculated, totalNet, totalVat, totalGross,
                totalsByRate, calculated.size(), warnings);
    }

    /** Calculates an invoice from UTF-8 input bytes without closing the stream. */
    public InvoiceCalculationResult calculate(InvoiceMetadata metadata, InputStream input,
                                              CsvProcessingOptions options) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        return calculate(metadata, new InputStreamReader(input, StandardCharsets.UTF_8), options);
    }

    private boolean containsRate(List<BigDecimal> rates, BigDecimal target) {
        return rates.stream().anyMatch(rate -> rate.compareTo(target) == 0);
    }

    private BigDecimal rateKey(BigDecimal rate) {
        BigDecimal normalized = rate.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private BigDecimal zero(InvoiceMetadata metadata) {
        return BigDecimal.ZERO.setScale(metadata.moneyScale());
    }

    private static final class MutableTotals {
        private BigDecimal net = BigDecimal.ZERO;
        private BigDecimal vat = BigDecimal.ZERO;
        private BigDecimal gross = BigDecimal.ZERO;

        void add(CalculatedLine line) {
            net = net.add(line.lineNet());
            vat = vat.add(line.lineVat());
            gross = gross.add(line.lineGross());
        }

        VatTotals toValue(InvoiceMetadata metadata) {
            int scale = metadata.moneyScale();
            RoundingModeHolder rounding = new RoundingModeHolder(metadata.roundingMode());
            return new VatTotals(net.setScale(scale, rounding.mode), vat.setScale(scale, rounding.mode), gross.setScale(scale, rounding.mode));
        }
    }

    private record RoundingModeHolder(java.math.RoundingMode mode) { }
}
