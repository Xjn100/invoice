# CSV Schema

## Input

Header chuẩn:

```text
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal
```

`declaredLineTotal` là tùy chọn. Các cột khác bắt buộc. Header không phân biệt hoa thường; dấu phân cách mặc định là `,`, có thể dùng `;` qua `CsvProcessingOptions`.

- Encoding: UTF-8 ở tầng file/caller.
- Số thập phân: dấu chấm, không có dấu phân cách hàng nghìn.
- Text có dấu phẩy phải đặt trong dấu ngoặc kép theo chuẩn CSV.
- `lineNumber` là số nguyên dương, không trùng.
- `quantity` mặc định lớn hơn 0; có thể cho phép 0 trong options.
- `unitPrice` không âm.
- `vatRate` nhận `0`, `5`, `8`, `10` hoặc dạng có `%`.

## Output

```text
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,lineNet,lineVat,lineGross,declaredLineTotal,comparisonStatus
```

`comparisonStatus` là `NOT_PROVIDED`, `MATCH` hoặc `MISMATCH`. Tổng hóa đơn không trộn vào CSV dòng; lấy từ `InvoiceCalculationResult`:

- `totalNet`
- `totalVat`
- `totalGross`
- `totalsByVatRate`
- `warnings`

## Ví dụ

Input:

```csv
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal
1,A1,"Bút, mực",2,10000,8,21600
2,A2,Sách,1,50000,0,50000
```

Với giá chưa VAT, output dòng đầu có `lineNet=20000`, `lineVat=1600`, `lineGross=21600`.
