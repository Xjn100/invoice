# DemoInvoice

Ứng dụng web mẫu để kiểm thử thư viện `invoice-vat-library` bằng giao diện trình duyệt.

## Chức năng

- Upload file CSV đầu vào.
- Nhập metadata hóa đơn.
- Chọn giá đã/chưa bao gồm VAT.
- Chọn rounding mode, money scale, VAT rates và delimiter.
- Hiển thị tổng trước VAT, tổng VAT, tổng khách hàng thanh toán.
- Hiển thị kết quả theo từng dòng và theo từng mức VAT.
- Tải CSV output.

## Chuẩn bị thư viện invoice

Demo dùng dependency Maven `com.example.invoice:invoice-vat-library:1.0.0-SNAPSHOT`. Cài thư viện local trước:

```powershell
Push-Location ..\invoice
mvn clean install
Pop-Location
```

## Chạy demo

```powershell
cd D:\DemoInvoice
mvn spring-boot:run
```

Mở http://localhost:8081

Hoặc đóng gói:

```powershell
mvn clean package
java -jar target\demo-invoice-1.0.0-SNAPSHOT.jar
```

## CSV mẫu

Tạo file `invoice-input.csv`:

```csv
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal
1,SP001,"Bút bi xanh",2,10000,8,21600
2,SP002,"Sổ tay, khổ A5",1,50000,10,55000
3,SP003,"Sách giáo khoa",1,30000,0,30000
```

Sau đó upload file trên màn hình, nhập metadata mặc định và bấm **Tính hóa đơn**.

Tài liệu tích hợp chi tiết nằm tại `D:\invoice\docs\CUSTOMER-INTEGRATION-SPEC.md`.
