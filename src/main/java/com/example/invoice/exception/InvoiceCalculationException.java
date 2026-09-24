package com.example.invoice.exception;

/** Checked exception for invalid invoice input or policy data. */
public class InvoiceCalculationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;
    private final long csvLine;
    private final String column;

    public InvoiceCalculationException(ErrorCode errorCode, String message, long csvLine, String column) {
        super(message);
        this.errorCode = errorCode;
        this.csvLine = csvLine;
        this.column = column;
    }

    public ErrorCode errorCode() { return errorCode; }
    public long csvLine() { return csvLine; }
    public String column() { return column; }

    public static InvoiceCalculationException metadata(String message) {
        return new InvoiceCalculationException(ErrorCode.INVALID_METADATA, message, -1, null);
    }

    public static InvoiceCalculationException policy(ErrorCode code, String message) {
        return new InvoiceCalculationException(code, message, -1, null);
    }
    public static InvoiceCalculationException input(ErrorCode code, String message, long line, String column) {
        return new InvoiceCalculationException(code, message, line, column);
    }
}
