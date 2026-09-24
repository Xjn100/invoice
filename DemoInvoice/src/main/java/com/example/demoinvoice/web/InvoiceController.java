package com.example.demoinvoice.web;

import com.example.demoinvoice.service.InvoiceDemoService;
import com.example.invoice.domain.CalculatedLine;
import com.example.invoice.domain.InvoiceCalculationResult;
import com.example.invoice.domain.VatTotals;
import com.example.invoice.exception.InvoiceCalculationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class InvoiceController {
    private final InvoiceDemoService service;

    public InvoiceController(InvoiceDemoService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home(Model model) {
        populateDefaults(model);
        return "index";
    }

    @PostMapping("/calculate")
    public String calculate(@RequestParam("file") MultipartFile file,
                            @RequestParam String invoiceId,
                            @RequestParam LocalDate invoiceDate,
                            @RequestParam String currency,
                            @RequestParam String sellerTaxId,
                            @RequestParam(defaultValue = "false") boolean priceIncludesVat,
                            @RequestParam(defaultValue = "HALF_UP") RoundingMode roundingMode,
                            @RequestParam(defaultValue = "0") int moneyScale,
                            @RequestParam String taxPolicyVersion,
                            @RequestParam String allowedVatRates,
                            @RequestParam(defaultValue = ",") char delimiter,
                            Model model) {
        InvoiceCalculationResult result = service.calculate(file, invoiceId, invoiceDate, currency,
                sellerTaxId, priceIncludesVat, roundingMode, moneyScale, taxPolicyVersion,
                allowedVatRates, delimiter);
        model.addAttribute("result", result);
        model.addAttribute("outputCsv", service.writeCsv(result, delimiter));
        model.addAttribute("fileName", file.getOriginalFilename());
        model.addAttribute("rates", result.totalsByVatRate().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new RateRow(entry.getKey().stripTrailingZeros().toPlainString(),
                entry.getValue().vat().toPlainString(),
                progressPercent(entry.getValue(), result)))
            .toList());
        return "result";
    }

    @ExceptionHandler(Exception.class)
    public String handleError(Exception exception, Model model) {
        populateDefaults(model);
        String message = exception instanceof InvoiceCalculationException invoiceException
                ? invoiceException.errorCode() + ": " + invoiceException.getMessage()
                : "Unable to process the invoice: " + exception.getMessage();
        model.addAttribute("error", message);
        return "index";
    }

    private void populateDefaults(Model model) {
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("roundingModes", RoundingMode.values());
    }

    private int progressPercent(VatTotals totals, InvoiceCalculationResult result) {
        if (result.totalGross().signum() == 0) {
            return 0;
        }
        return totals.gross().multiply(java.math.BigDecimal.valueOf(100))
                .divide(result.totalGross(), 0, RoundingMode.HALF_UP).intValue();
    }

    public record RateRow(String label, String vat, int progress) {
    }
}
