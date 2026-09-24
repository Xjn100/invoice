# Invoice VAT Library

Thư viện Java thuần dùng để đọc CSV hàng hóa, tính VAT theo từng dòng và tổng hóa đơn. Thư viện không phụ thuộc Spring Boot, database hoặc filesystem; có thể nhúng vào nhiều ứng dụng và dùng đồng thời.

## Yêu cầu

- JDK 17+
- Maven 3.8+

## Build và test

```bash
mvn clean test
mvn verify
```

## Hướng dẫn tích hợp cho khách hàng

Tài liệu hướng dẫn đầy đủ từ việc tạo file CSV đầu vào, tạo metadata, gọi thư viện bằng `Path`/stream, đọc tổng hóa đơn, ghi CSV đầu ra và xử lý lỗi nằm tại [docs/CUSTOMER-INTEGRATION-SPEC.md](docs/CUSTOMER-INTEGRATION-SPEC.md).

Thư viện là Java library, không phải command-line tool. Ứng dụng tích hợp chịu trách nhiệm mở file đầu vào và lưu file đầu ra.

## Sử dụng nhanh

```java
InvoiceMetadata metadata = new InvoiceMetadata(
    "1", "INV-001", LocalDate.of(2026, 9, 24), "VND", "0123456789",
    false, RoundingMode.HALF_UP, 0, "VN-2026-01",
    List.of(BigDecimal.ZERO, BigDecimal.valueOf(5),
            BigDecimal.valueOf(8), BigDecimal.TEN));

String csv = "lineNumber,itemCode,itemName,quantity,unitPrice,vatRate\n"
           + "1,A1,Notebook,2,100000,10\n";

InvoiceCalculator calculator = new InvoiceCalculator();
InvoiceCalculationResult result = calculator.calculate(
    metadata, new StringReader(csv), CsvProcessingOptions.defaults());

new CsvResultWriter().write(result, outputWriter, CsvProcessingOptions.defaults());
System.out.println(result.totalGross());
```

## Quy tắc chính

- `unitPrice` mặc định là giá chưa VAT; đặt `priceIncludesVat=true` nếu giá đã gồm VAT.
- VAT được tính cho từng dòng rồi cộng lại.
- VND mặc định làm tròn 0 chữ số bằng `HALF_UP`.
- Hỗ trợ VAT `0`, `5`, `8`, `10` phần trăm; chính sách thực tế phải truyền qua `allowedVatRates`.
- `declaredLineTotal` chỉ để đối chiếu, không thay thế phép tính.
- Dữ liệu lỗi làm toàn bộ request thất bại theo fail-fast.

Xem thêm [docs/USER-GUIDE.md](docs/USER-GUIDE.md), [docs/CSV-SCHEMA.md](docs/CSV-SCHEMA.md) và [docs/DESIGN.md](docs/DESIGN.md) để biết chi tiết kỹ thuật.
