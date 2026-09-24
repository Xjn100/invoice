# Requirements Traceability

| Requirement | Implementation | Test/evidence |
|---|---|---|
| Java 17 Maven library | `pom.xml` | `mvn verify` |
| Mandatory metadata | `InvoiceMetadata.validate` | `validatesMetadataAndVatPolicy` |
| CSV quoted parsing | `CsvParser` state machine | `parsesQuotedVietnameseTextAndSemicolonDelimiter` |
| Multiple VAT rates | `InvoiceCalculator.totalsByVatRate` | `calculatesMultipleVatRatesPerLineAndInvoice` |
| Price excluding VAT | `VatCalculator` | `calculatesMultipleVatRatesPerLineAndInvoice` |
| Price including VAT | `VatCalculator` reverse calculation | `calculatesPricesThatAlreadyIncludeVat` |
| Per-line rounding | `VatCalculator` | `roundsEachLineBeforeAggregatingAndSupportsPercentNotation` |
| Declared total comparison | `CsvProcessingOptions` and result status | `comparesDeclaredTotalAndWritesStableOutput`, `treatsToleranceAsMatchAndCanFailOnLargeMismatch` |
| Stable CSV output | `CsvResultWriter` | `comparesDeclaredTotalAndWritesStableOutput` |
| Fail-fast validation | `InvoiceCalculationException` | invalid numbers, headers, duplicate lines and VAT tests |
| Thread-safe calculator | stateless `InvoiceCalculator` | `calculatorCanBeSharedAcrossThreads` |
| Immutable results | record defensive copies | `preservesInvariantsAndResultCollectionsAreImmutable` |
| Documentation | README and docs folder | reviewed artifacts |
