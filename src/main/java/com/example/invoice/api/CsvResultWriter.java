package com.example.invoice.api;

import com.example.invoice.domain.CalculatedLine;
import com.example.invoice.domain.CsvProcessingOptions;
import com.example.invoice.domain.InvoiceCalculationResult;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes calculated invoice lines as a stable CSV schema. */
public final class CsvResultWriter {
    /** Writes the result rows and does not append TOTAL pseudo-rows. */
    public void write(InvoiceCalculationResult result, Writer output, CsvProcessingOptions options) {
        if (result == null || output == null) {
            throw new IllegalArgumentException("result and output are required");
        }
        CsvProcessingOptions checked = options == null ? CsvProcessingOptions.defaults() : options.validate();
        try {
            writeRow(output, checked.delimiter(), List.of("lineNumber", "itemCode", "itemName", "quantity", "unitPrice",
                    "vatRate", "lineNet", "lineVat", "lineGross", "declaredLineTotal", "comparisonStatus"));
            for (CalculatedLine line : result.lines()) {
                writeRow(output, checked.delimiter(), List.of(String.valueOf(line.lineNumber()), line.itemCode(), line.itemName(),
                        line.quantity().toPlainString(), line.unitPrice().toPlainString(), line.vatRate().toPlainString(),
                        line.lineNet().toPlainString(), line.lineVat().toPlainString(), line.lineGross().toPlainString(),
                        line.declaredLineTotal() == null ? "" : line.declaredLineTotal().toPlainString(),
                        line.comparisonStatus().name()));
            }
            output.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write invoice CSV", ex);
        }
    }

    /** Writes UTF-8 CSV bytes without closing the supplied stream. */
    public void write(InvoiceCalculationResult result, OutputStream output, CsvProcessingOptions options) {
        if (output == null) {
            throw new IllegalArgumentException("output is required");
        }
        write(result, new OutputStreamWriter(output, StandardCharsets.UTF_8), options);
    }

    private void writeRow(Writer output, char delimiter, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.write(delimiter);
            }
            String value = values.get(index);
            boolean quote = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
            if (quote) {
                output.write('"');
                output.write(value.replace("\"", "\"\""));
                output.write('"');
            } else {
                output.write(value);
            }
        }
        output.write('\n');
    }
}
