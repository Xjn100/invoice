package com.example.invoice;

import com.example.invoice.api.CsvResultWriter;
import com.example.invoice.api.InvoiceCalculator;
import com.example.invoice.domain.CalculatedLine;
import com.example.invoice.domain.CsvProcessingOptions;
import com.example.invoice.domain.InvoiceCalculationResult;
import com.example.invoice.domain.InvoiceMetadata;
import com.example.invoice.exception.ErrorCode;
import com.example.invoice.exception.InvoiceCalculationException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceLibraryTest {
    private final InvoiceCalculator calculator = new InvoiceCalculator();

    @Test
    void calculatesMultipleVatRatesPerLineAndInvoice() {
        String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal\n"
                + "1,A1,Notebook,2,100000,10,220000\n"
                + "2,A2,Pen,3,50000,5,157500\n"
                + "3,A3,Book,1,20000,0,20000\n";

        InvoiceCalculationResult result = calculator.calculate(metadata(false), new StringReader(csv), null);

        assertThat(result.lines()).extracting(CalculatedLine::lineGross)
                .containsExactly(new BigDecimal("220000"), new BigDecimal("157500"), new BigDecimal("20000"));
        assertThat(result.totalNet()).isEqualByComparingTo("370000");
        assertThat(result.totalVat()).isEqualByComparingTo("27500");
        assertThat(result.totalGross()).isEqualByComparingTo("397500");
        assertThat(result.totalsByVatRate().get(new BigDecimal("10")).vat()).isEqualByComparingTo("20000");
        assertThat(result.totalsByVatRate().get(new BigDecimal("5")).vat()).isEqualByComparingTo("7500");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void calculatesPricesThatAlreadyIncludeVat() {
        String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A1,Tax included,1,110,10\n";
        InvoiceCalculationResult result = calculator.calculate(metadata(true), new StringReader(csv), null);

        assertThat(result.lines().get(0).lineNet()).isEqualByComparingTo("100");
        assertThat(result.lines().get(0).lineVat()).isEqualByComparingTo("10");
        assertThat(result.totalGross()).isEqualByComparingTo("110");
    }

    @Test
    void roundsEachLineBeforeAggregatingAndSupportsPercentNotation() {
        String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n"
                + "1,A1,First,1,5,10%\n"
                + "2,A2,Second,1,5,10%\n";
        InvoiceCalculationResult result = calculator.calculate(metadata(false), new StringReader(csv), null);

        assertThat(result.lines()).extracting(CalculatedLine::lineVat)
                .containsExactly(new BigDecimal("1"), new BigDecimal("1"));
        assertThat(result.totalVat()).isEqualByComparingTo("2");
        assertThat(result.totalGross()).isEqualByComparingTo("12");
    }

    @Test
    void parsesQuotedVietnameseTextAndSemicolonDelimiter() {
        String csv = "lineNumber;itemCode;itemName;quantity;unitPrice;vatRate\n"
                + "1;A1;\"Bút, mực\";1;10000;8\n";
        CsvProcessingOptions options = new CsvProcessingOptions(';', BigDecimal.ZERO, false, 100, 10000, false);

        InvoiceCalculationResult result = calculator.calculate(metadata(false), new StringReader(csv), options);

        assertThat(result.lines().get(0).itemName()).isEqualTo("Bút, mực");
        assertThat(result.totalGross()).isEqualByComparingTo("10800");
    }

    @Test
    void validatesMetadataAndVatPolicy() {
        assertThatThrownBy(() -> calculator.calculate(null, new StringReader(""), null))
                .isInstanceOf(InvoiceCalculationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_METADATA);

        String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A1,Item,1,100,7\n";
        assertThatThrownBy(() -> calculator.calculate(metadata(false), new StringReader(csv), null))
                .isInstanceOf(InvoiceCalculationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_VAT_RATE);
    }

    @Test
    void failsForDuplicateLinesAndMissingColumns() {
        String duplicate = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A,One,1,1,0\n1,B,Two,1,1,0\n";
        assertThatThrownBy(() -> calculator.calculate(metadata(false), new StringReader(duplicate), null))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_LINE_NUMBER);

        String missing = "lineNumber,itemCode,itemName,quantity,unitPrice\n1,A,One,1,1\n";
        assertThatThrownBy(() -> calculator.calculate(metadata(false), new StringReader(missing), null))
                .extracting("errorCode").isEqualTo(ErrorCode.MISSING_REQUIRED_COLUMN);
    }

    @Test
    void comparesDeclaredTotalAndWritesStableOutput() {
        String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal\n1,A1,Item,1,100,10,999\n";
        InvoiceCalculationResult result = calculator.calculate(metadata(false), new StringReader(csv), null);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.lines().get(0).comparisonStatus()).isEqualTo(CalculatedLine.ComparisonStatus.MISMATCH);

        StringWriter output = new StringWriter();
        new CsvResultWriter().write(result, output, null);
        assertThat(output.toString()).contains("lineNumber,itemCode,itemName")
                .contains("1,A1,Item,1,100,10,100,10,110,999,MISMATCH");
    }

            @Test
            void treatsToleranceAsMatchAndCanFailOnLargeMismatch() {
            String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal\n1,A,Item,1,100,10,110.4\n";
            CsvProcessingOptions tolerant = new CsvProcessingOptions(',', new BigDecimal("0.5"), false, 100, 10000, false);
            InvoiceCalculationResult result = calculator.calculate(metadata(false), new StringReader(csv), tolerant);
            assertThat(result.lines().get(0).comparisonStatus()).isEqualTo(CalculatedLine.ComparisonStatus.MATCH);
            assertThat(result.warnings()).isEmpty();

            CsvProcessingOptions strict = new CsvProcessingOptions(',', BigDecimal.ZERO, true, 100, 10000, false);
            assertThatThrownBy(() -> calculator.calculate(metadata(false), new StringReader(csv), strict))
                .extracting("errorCode").isEqualTo(ErrorCode.DECLARED_TOTAL_MISMATCH);
            }

            @Test
            void supportsEmptyInvoiceOnlyWhenHeaderIsPresentAndConfiguredZeroQuantity() {
            String headerOnly = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n";
            InvoiceCalculationResult empty = calculator.calculate(metadata(false), new StringReader(headerOnly), null);
            assertThat(empty.validLineCount()).isZero();
            assertThat(empty.totalGross()).isEqualByComparingTo("0");

            String zero = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A,Item,0,100,10\n";
            CsvProcessingOptions allowZero = new CsvProcessingOptions(',', BigDecimal.ZERO, false, 100, 10000, true);
            assertThat(calculator.calculate(metadata(false), new StringReader(zero), allowZero).totalGross())
                .isEqualByComparingTo("0");
            }

            @Test
            void preservesInvariantsAndResultCollectionsAreImmutable() {
            String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A,Item,1,100,10\n2,B,Other,2,50,5\n";
            InvoiceCalculationResult result = calculator.calculate(metadata(false), new StringReader(csv), null);
            assertThat(result.totalGross()).isEqualByComparingTo(result.totalNet().add(result.totalVat()));
            assertThat(result.lines().stream().map(CalculatedLine::lineVat).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.totalVat());
            assertThatThrownBy(() -> result.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.totalsByVatRate().clear()).isInstanceOf(UnsupportedOperationException.class);
            }

            @Test
            void rejectsInvalidNumbersAndEmptyInput() {
            String invalid = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A,Item,one,100,10\n";
            assertThatThrownBy(() -> calculator.calculate(metadata(false), new StringReader(invalid), null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NUMBER_FORMAT);
            assertThatThrownBy(() -> calculator.calculate(metadata(false), new StringReader(""), null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_HEADER);
            }

    @Test
    void calculatorCanBeSharedAcrossThreads() throws Exception {
        String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n1,A1,Item,2,100,10\n";
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<BigDecimal>> tasks = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(ignored -> (Callable<BigDecimal>) () -> calculator
                            .calculate(metadata(false), new StringReader(csv), null).totalGross())
                    .toList();
            assertThat(executor.invokeAll(tasks)).allSatisfy(future -> {
                try {
                    assertThat(future.get()).isEqualByComparingTo("220");
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private InvoiceMetadata metadata(boolean includesVat) {
        return new InvoiceMetadata("1", "INV-001", LocalDate.of(2026, 9, 24), "VND", "0123456789",
                includesVat, RoundingMode.HALF_UP, 0, "VN-2026-01",
                List.of(BigDecimal.ZERO, BigDecimal.valueOf(5), BigDecimal.valueOf(8), BigDecimal.TEN));
    }
}
