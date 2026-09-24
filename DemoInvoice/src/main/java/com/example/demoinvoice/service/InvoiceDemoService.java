package com.example.demoinvoice.service;

import com.example.invoice.api.CsvResultWriter;
import com.example.invoice.api.InvoiceCalculator;
import com.example.invoice.domain.CsvProcessingOptions;
import com.example.invoice.domain.InvoiceCalculationResult;
import com.example.invoice.domain.InvoiceMetadata;
import com.example.invoice.exception.InvoiceCalculationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceDemoService {
    private final InvoiceCalculator calculator = new InvoiceCalculator();
    private final CsvResultWriter resultWriter = new CsvResultWriter();

    public InvoiceCalculationResult calculate(MultipartFile file, String invoiceId,
                                              LocalDate invoiceDate, String currency,
                                              String sellerTaxId, boolean priceIncludesVat,
                                              RoundingMode roundingMode, int moneyScale,
                                              String taxPolicyVersion, String allowedVatRates,
                                              char delimiter) {
        if (file == null || file.isEmpty()) {
            throw InvoiceCalculationException.metadata("A non-empty CSV file is required");
        }
        InvoiceMetadata metadata = new InvoiceMetadata(
                "1", invoiceId, invoiceDate, currency, sellerTaxId, priceIncludesVat,
                roundingMode, moneyScale, taxPolicyVersion, parseRates(allowedVatRates));
        CsvProcessingOptions options = new CsvProcessingOptions(
                delimiter, BigDecimal.ZERO, false, 100_000, 10_000_000L, false);
        try (InputStream input = file.getInputStream()) {
            return calculator.calculate(metadata, input, options);
        } catch (IOException ex) {
            throw InvoiceCalculationException.metadata("Unable to read uploaded CSV: " + ex.getMessage());
        }
    }

    public byte[] writeCsv(InvoiceCalculationResult result, char delimiter) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resultWriter.write(result, output, new CsvProcessingOptions(
                delimiter, BigDecimal.ZERO, false, 100_000, 10_000_000L, false));
        return output.toString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
    }

    private List<BigDecimal> parseRates(String value) {
        if (value == null || value.isBlank()) {
            throw InvoiceCalculationException.metadata("allowedVatRates is required");
        }
        try {
            return List.of(value.split(",")).stream()
                    .map(String::trim)
                    .map(rate -> rate.endsWith("%") ? rate.substring(0, rate.length() - 1).trim() : rate)
                    .map(BigDecimal::new)
                    .toList();
        } catch (NumberFormatException ex) {
            throw InvoiceCalculationException.metadata("allowedVatRates must be comma-separated numbers");
        }
    }
}
