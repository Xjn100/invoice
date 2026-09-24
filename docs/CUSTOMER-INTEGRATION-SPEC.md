# Customer Integration Specification

Tài liệu này hướng dẫn đội tích hợp sử dụng `invoice-vat-library` từ đầu đến cuối: chuẩn bị CSV, tạo metadata, gọi thư viện, đọc kết quả và xử lý lỗi.

## 1. Phạm vi

Thư viện nhận **một hóa đơn trong một file CSV** và trả kết quả tính toán cho hóa đơn đó.

Thư viện không tự đọc database, không tự upload/download file, không có REST API và không có command-line executable. Ứng dụng của khách hàng chịu trách nhiệm mở file, truyền metadata và lưu file kết quả.

Luồng tích hợp:

```text
CSV input + InvoiceMetadata + CsvProcessingOptions
                 |
                 v
        InvoiceCalculator.calculate(...)
                 |
                 v
InvoiceCalculationResult: line results + invoice totals
                 |
                 v
        CsvResultWriter.write(...)
                 |
                 v
CSV output + summary từ InvoiceCalculationResult
```

## 2. Yêu cầu môi trường

- JDK 17 trở lên.
- Maven 3.8 trở lên.
- Encoding file: UTF-8.
- Tiền tệ phiên bản 1: VND.

## 3. Thêm thư viện vào ứng dụng

### 3.1 Dùng artifact Maven nội bộ

Sau khi artifact được publish vào Maven Repository của công ty:

```xml
<dependency>
  <groupId>com.example.invoice</groupId>
  <artifactId>invoice-vat-library</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 3.2 Dùng JAR cục bộ để thử nghiệm

Build thư viện:

```bash
mvn clean verify
```

JAR được tạo tại:

```text
target/invoice-vat-library-1.0.0-SNAPSHOT.jar
```

Khuyến nghị publish vào Maven Repository thay vì copy JAR thủ công. Nếu bắt buộc dùng JAR, ứng dụng phải đưa JAR vào classpath và tự quản lý version.

## 4. Tạo file CSV đầu vào

Tạo file `invoice-input.csv` với encoding UTF-8 và header chính xác:

```csv
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal
1,SP001,"Bút bi xanh",2,10000,8,21600
2,SP002,"Sổ tay, khổ A5",1,50000,10,55000
3,SP003,"Sách giáo khoa",1,30000,0,30000
```

### Ý nghĩa cột

| Cột | Bắt buộc | Ví dụ | Ý nghĩa |
|---|---:|---|---|
| `lineNumber` | Có | `1` | Số thứ tự dòng, số nguyên dương, không trùng |
| `itemCode` | Có | `SP001` | Mã hàng, tối đa 100 ký tự |
| `itemName` | Có | `Bút bi xanh` | Tên hàng, tối đa 500 ký tự |
| `quantity` | Có | `2` | Số lượng, mặc định phải lớn hơn 0 |
| `unitPrice` | Có | `10000` | Đơn giá; không dùng dấu phân cách hàng nghìn |
| `vatRate` | Có | `8` hoặc `8%` | Thuế suất phần trăm của dòng |
| `declaredLineTotal` | Không | `21600` | Tổng do hệ thống nguồn gửi để đối chiếu |

Quy tắc CSV:

- Dấu phân cách mặc định là dấu phẩy `,`.
- Có thể dùng dấu chấm phẩy `;` qua `CsvProcessingOptions`.
- Số thập phân dùng dấu chấm, ví dụ `1.5`.
- Không dùng `1,000` hoặc `1 000` cho số tiền.
- Nếu tên hàng có dấu phẩy, đặt toàn bộ tên trong dấu ngoặc kép.
- Không để chuỗi rỗng ở cột bắt buộc.
- Không đưa thêm cột ngoài schema đã công bố.
- Một file chỉ chứa một hóa đơn.

## 5. Quy tắc giá và VAT

### 5.1 Giá chưa bao gồm VAT

Đây là chế độ khuyến nghị mặc định. Metadata đặt `priceIncludesVat = false`.

Ví dụ dòng:

```text
quantity = 2
unitPrice = 10,000
vatRate = 8%
```

Kết quả:

```text
lineNet   = 2 x 10,000 = 20,000
lineVat   = 20,000 x 8% = 1,600
lineGross = 20,000 + 1,600 = 21,600
```

### 5.2 Giá đã bao gồm VAT

Metadata đặt `priceIncludesVat = true`.

Ví dụ:

```text
quantity = 1
unitPrice = 110
vatRate = 10%
```

Kết quả:

```text
lineGross = 110
lineNet   = 110 / 1.10 = 100
lineVat   = 110 - 100 = 10
```

### 5.3 Thuế suất

Phiên bản 1 cho phép cấu hình các mức `0`, `5`, `8`, `10`. Thuế suất được kiểm tra theo `allowedVatRates` trong metadata; nếu CSV chứa rate không nằm trong danh sách này, toàn bộ lần xử lý bị từ chối.

VAT được tính và làm tròn theo từng dòng trước khi cộng tổng. Không tính một VAT rate chung cho toàn bộ hóa đơn.

## 6. Tạo metadata

Metadata bắt buộc phải được tạo cho mỗi lần xử lý:

```java
InvoiceMetadata metadata = new InvoiceMetadata(
    "1",                              // schemaVersion
    "INV-2026-0001",                 // invoiceId
    LocalDate.of(2026, 9, 24),        // invoiceDate
    "VND",                           // currency
    "0123456789",                    // sellerTaxId
    false,                            // priceIncludesVat
    RoundingMode.HALF_UP,             // roundingMode
    0,                                // moneyScale
    "VN-2026-01",                    // taxPolicyVersion
    List.of(
        BigDecimal.ZERO,
        BigDecimal.valueOf(5),
        BigDecimal.valueOf(8),
        BigDecimal.TEN));
```

Ý nghĩa quan trọng:

- `schemaVersion`: phiên bản schema thư viện, hiện là `"1"`.
- `invoiceId`: mã duy nhất của hóa đơn/chứng từ.
- `invoiceDate`: ngày lập hóa đơn.
- `currency`: hiện chỉ nhận `VND`.
- `sellerTaxId`: mã số thuế bên bán.
- `priceIncludesVat`: xác định `unitPrice` đã gồm VAT hay chưa.
- `roundingMode`: khuyến nghị `HALF_UP`.
- `moneyScale`: với VND thường là `0`.
- `taxPolicyVersion`: phiên bản chính sách thuế mà hệ thống nghiệp vụ phê duyệt.
- `allowedVatRates`: danh sách thuế suất được phép trong file.

Không truyền `null` metadata. Không để thư viện tự đoán giá đã gồm VAT, tiền tệ hoặc chính sách thuế.

## 7. Gọi thư viện bằng file Path

Ví dụ hoàn chỉnh đọc `invoice-input.csv`, tính toán và ghi `invoice-output.csv`:

```java
import com.example.invoice.api.CsvResultWriter;
import com.example.invoice.api.InvoiceCalculator;
import com.example.invoice.domain.CsvProcessingOptions;
import com.example.invoice.domain.InvoiceCalculationResult;
import com.example.invoice.domain.InvoiceMetadata;

import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class InvoiceExample {
    public static void main(String[] args) throws Exception {
        Path inputPath = Path.of("invoice-input.csv");
        Path outputPath = Path.of("invoice-output.csv");

        InvoiceMetadata metadata = new InvoiceMetadata(
                "1", "INV-2026-0001", LocalDate.of(2026, 9, 24),
                "VND", "0123456789", false, RoundingMode.HALF_UP, 0,
                "VN-2026-01", List.of(
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(5),
                        BigDecimal.valueOf(8),
                        BigDecimal.TEN));

        CsvProcessingOptions options = CsvProcessingOptions.defaults();
        InvoiceCalculator calculator = new InvoiceCalculator();
        InvoiceCalculationResult result;

        try (Reader input = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            result = calculator.calculate(metadata, input, options);
        }

        try (Writer output = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            new CsvResultWriter().write(result, output, options);
        }

        System.out.println("Invoice: " + result.metadata().invoiceId());
        System.out.println("Total before VAT: " + result.totalNet());
        System.out.println("Total VAT: " + result.totalVat());
        System.out.println("Customer payment: " + result.totalGross());
        System.out.println("Warnings: " + result.warnings().size());
    }
}
```

Lưu ý: thư viện không tự đóng `Reader`/`Writer`; ứng dụng gọi thư viện phải quản lý lifecycle bằng `try-with-resources` như ví dụ trên.

## 8. Gọi thư viện bằng stream

Nếu ứng dụng nhận file từ HTTP, object storage hoặc message queue, không cần ghi file tạm:

```java
try (InputStream input = uploadedFileInputStream;
     OutputStream output = targetOutputStream) {
    InvoiceCalculationResult result = new InvoiceCalculator()
            .calculate(metadata, input, CsvProcessingOptions.defaults());
    new CsvResultWriter().write(result, output, CsvProcessingOptions.defaults());
}
```

Các overload `InputStream` và `OutputStream` dùng UTF-8.

## 9. Đọc kết quả

### 9.1 Kết quả tổng hóa đơn

```java
BigDecimal beforeVat = result.totalNet();
BigDecimal vat = result.totalVat();
BigDecimal customerPayment = result.totalGross();

if (!customerPayment.equals(beforeVat.add(vat))) {
    throw new IllegalStateException("Invoice total invariant failed");
}
```

Các tổng theo thuế suất nằm trong `result.totalsByVatRate()`:

```java
VatTotals vat10 = result.totalsByVatRate().get(BigDecimal.TEN);
if (vat10 != null) {
    System.out.println("VAT 10%: " + vat10.vat());
}
```

### 9.2 Kết quả từng dòng

```java
for (CalculatedLine line : result.lines()) {
    System.out.printf("%s: net=%s, vat=%s, gross=%s%n",
            line.itemCode(), line.lineNet(), line.lineVat(), line.lineGross());
}
```

### 9.3 File CSV output mẫu

Với input ở trên, output có dạng:

```csv
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,lineNet,lineVat,lineGross,declaredLineTotal,comparisonStatus
1,SP001,Bút bi xanh,2,10000,8,20000,1600,21600,21600,MATCH
2,SP002,"Sổ tay, khổ A5",1,50000,10,50000,5000,55000,55000,MATCH
3,SP003,Sách giáo khoa,1,30000,0,30000,0,30000,30000,MATCH
```

Tổng trong `InvoiceCalculationResult`:

```text
totalNet   = 100000
totalVat   = 6600
totalGross = 106600
```

File CSV output chỉ chứa kết quả từng dòng. Summary hóa đơn lấy từ object `InvoiceCalculationResult`, không có dòng giả `TOTAL` trong CSV.

## 10. Đối chiếu `declaredLineTotal`

`declaredLineTotal` là giá trị hệ thống nguồn gửi để kiểm tra, không phải nguồn tính toán.

Mặc định:

```java
CsvProcessingOptions options = CsvProcessingOptions.defaults();
```

Sai lệch tạo warning. Muốn sai lệch vượt tolerance làm request thất bại:

```java
CsvProcessingOptions strictOptions = new CsvProcessingOptions(
        ',',
        BigDecimal.ZERO,
        true,
        100_000,
        10_000_000L,
        false);
```

Muốn cho phép sai lệch tối đa 1 VND:

```java
CsvProcessingOptions tolerantOptions = new CsvProcessingOptions(
        ',',
        BigDecimal.ONE,
        false,
        100_000,
        10_000_000L,
        false);
```

## 11. Xử lý lỗi

Bắt `InvoiceCalculationException` và đọc các thuộc tính lỗi:

```java
try {
    InvoiceCalculationResult result = calculator.calculate(metadata, input, options);
} catch (InvoiceCalculationException ex) {
    System.err.println("Error code: " + ex.errorCode());
    System.err.println("CSV line: " + ex.csvLine());
    System.err.println("Column: " + ex.column());
    System.err.println("Message: " + ex.getMessage());
}
```

Các lỗi phổ biến:

| Mã lỗi | Nguyên nhân thường gặp |
|---|---|
| `INVALID_METADATA` | Thiếu metadata hoặc metadata rỗng |
| `INVALID_HEADER` | Header sai hoặc có cột không hỗ trợ |
| `MISSING_REQUIRED_COLUMN` | Thiếu cột bắt buộc |
| `INVALID_NUMBER_FORMAT` | Số dùng dấu phẩy/dấu cách hoặc không phải số |
| `INVALID_VAT_RATE` | Rate không nằm trong allowed VAT rates |
| `DUPLICATE_LINE_NUMBER` | Trùng số dòng |
| `DECLARED_TOTAL_MISMATCH` | Sai lệch declared total vượt tolerance ở chế độ strict |
| `INPUT_TOO_LARGE` | Vượt giới hạn số dòng hoặc kích thước input |
| `UNSUPPORTED_CURRENCY` | Currency khác VND trong phiên bản 1 |
| `UNSUPPORTED_SCHEMA_VERSION` | Schema version không được hỗ trợ |

Mặc định fail-fast: có lỗi thì không sử dụng một phần kết quả đã tính.

## 12. Giới hạn mặc định và vận hành

- Tối đa 100.000 dòng.
- Tối đa 10.000.000 ký tự input khi dùng API stream/reader.
- Tối đa 500 ký tự cho field text theo schema.
- Một `InvoiceCalculator` có thể dùng chung giữa nhiều thread.
- Mỗi request phải dùng `Reader`/`Writer` riêng.
- Không ghi CSV chứa dữ liệu khách hàng vào log.
- Không dùng `double` hoặc `float` để xử lý số tiền ở ứng dụng gọi thư viện.

## 13. Checklist trước khi tích hợp production

- [ ] Đã xác định `unitPrice` là giá trước hay sau VAT.
- [ ] Đã truyền đúng `priceIncludesVat`.
- [ ] Đã truyền đúng `taxPolicyVersion` và `allowedVatRates`.
- [ ] CSV dùng UTF-8 và header đúng.
- [ ] Đã thống nhất quy tắc xử lý `declaredLineTotal`.
- [ ] Đã kiểm tra `totalNet + totalVat = totalGross`.
- [ ] Đã xử lý `InvoiceCalculationException` theo `errorCode`.
- [ ] Đã giới hạn kích thước upload ở tầng ứng dụng.
- [ ] Đã không ghi dữ liệu nhạy cảm vào log.
- [ ] Đã kiểm thử các mức VAT thực tế của phòng ban.
