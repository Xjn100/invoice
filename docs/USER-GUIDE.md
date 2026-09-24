# User Guide

## Metadata bắt buộc

`InvoiceMetadata` gồm:

- `schemaVersion`: v1 là `1`.
- `invoiceId`: mã hóa đơn/chứng từ, tối đa 100 ký tự.
- `invoiceDate`: ngày lập.
- `currency`: v1 chỉ `VND`.
- `sellerTaxId`: mã số thuế bên bán.
- `priceIncludesVat`: giá CSV đã gồm VAT hay chưa.
- `roundingMode`: quy tắc làm tròn, thường `HALF_UP`.
- `moneyScale`: v1 thường là `0` với VND.
- `taxPolicyVersion`: phiên bản chính sách thuế.
- `allowedVatRates`: danh sách rate caller phê duyệt.

Các trường buyer/source/correlation/timezone là tùy chọn và được bảo toàn trong metadata kết quả.

## Quy trình

1. Tạo metadata đầy đủ.
2. Mở `Reader` UTF-8 cho CSV.
3. Gọi `InvoiceCalculator.calculate`.
4. Đọc line results và totals.
5. Gọi `CsvResultWriter.write` nếu cần CSV output.
6. Bắt `InvoiceCalculationException`, hiển thị `errorCode`, `csvLine`, `column`.

## Maven dependency

Sau khi publish artifact nội bộ:

```xml
<dependency>
  <groupId>com.example.invoice</groupId>
  <artifactId>invoice-vat-library</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Dùng đa luồng

Có thể tạo một `InvoiceCalculator` dùng chung. Không sửa object metadata/options sau khi truyền vào; các collection public là immutable. Mỗi lần gọi nên có Reader/Writer riêng.

## Compatibility

Schema version hiện là `1`. Khi thay đổi công thức, cột hoặc semantics, tăng `schemaVersion` và `taxPolicyVersion`; không âm thầm thay đổi kết quả của version cũ.
