# Design

## Kiến trúc

- `CsvParser`: đọc CSV có quoted fields, kiểm tra header và chuyển thành `InvoiceLine`.
- `VatCalculator`: tính tiền một dòng bằng `BigDecimal`.
- `InvoiceCalculator`: facade stateless, validate metadata/chính sách, aggregate totals.
- `CsvResultWriter`: chỉ serialize kết quả, không tính toán lại.
- `domain`: các record immutable.
- `exception`: mã lỗi ổn định, dòng CSV và tên cột.

Parser tự dùng state machine nhỏ để hỗ trợ dấu phẩy trong text, quote kép và CRLF mà không dùng `String.split`. Reader không hỗ trợ mark/reset được bọc bằng `BufferedReader`.

## Công thức

Giá chưa VAT:

```text
net = quantity * unitPrice
vat = round(net * vatRate / 100, moneyScale, roundingMode)
gross = round(net + vat, moneyScale, roundingMode)
```

Giá đã VAT:

```text
gross = round(quantity * unitPrice, moneyScale, roundingMode)
net = round(gross / (1 + vatRate / 100), moneyScale, roundingMode)
vat = round(gross - net, moneyScale, roundingMode)
```

Các dòng được làm tròn độc lập trước khi aggregate. Do đó tổng VAT là tổng `lineVat`, không phải áp một rate chung vào tổng net.

## Immutability và concurrency

Public result và metadata dùng record, defensive copy cho list/map. Calculator không có state thay đổi nên có thể dùng chung giữa các thread. Writer không lưu state giữa các lần gọi.

## Xử lý lỗi

Mặc định fail-fast. Mỗi lỗi có `ErrorCode`, số dòng CSV và cột liên quan. Không ghi dữ liệu hóa đơn vào log. `declaredLineTotal` được đối chiếu theo tolerance; có thể cấu hình mismatch là error.

## Giới hạn v1

Chỉ hỗ trợ VND và các rate được caller cho phép. Chưa hỗ trợ discount, phụ phí, tỷ giá, hàng trả lại hoặc nhiều hóa đơn trong một file. Những nghiệp vụ này cần mở rộng schema và policy version thay vì suy đoán trong thư viện.
