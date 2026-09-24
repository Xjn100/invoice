# Prompt phát triển thư viện tính VAT từ CSV bằng Java

Bạn là một software architect và Java engineer cấp expert, có kinh nghiệm triển khai nghiệp vụ hóa đơn, thuế VAT, thư viện dùng chung và xử lý dữ liệu tài chính. Hãy phát triển một thư viện Java dùng chung để đọc dữ liệu mặt hàng từ CSV, tính VAT theo từng dòng và toàn bộ hóa đơn, sau đó xuất kết quả ra CSV.

## 1. Mục tiêu

Xây dựng một thư viện độc lập, có thể được nhiều phòng ban và nhiều ứng dụng Java sử dụng đồng thời. Thư viện phải:

- Nhận một file CSV đầu vào chứa các dòng hàng hóa/dịch vụ.
- Nhận một đối tượng metadata bắt buộc cho mỗi lần xử lý.
- Tính tiền trước VAT, tiền VAT từng dòng, tiền sau VAT từng dòng.
- Tính tổng tiền trước VAT, tổng VAT và tổng tiền khách hàng phải thanh toán.
- Hỗ trợ nhiều mức VAT khác nhau trong cùng một hóa đơn.
- Xuất kết quả thành CSV có cấu trúc ổn định.
- Có validation, lỗi rõ ràng, không làm sai lệch dữ liệu tài chính.
- Có tài liệu sử dụng đầy đủ và ví dụ chạy được.

Không xây dựng giao diện web, REST API hoặc cơ sở dữ liệu trong phiên bản đầu tiên. Đây là thư viện Java thuần, không phụ thuộc Spring Boot.

## 2. Nguyên tắc bắt buộc

- Sử dụng Java 17 trở lên.
- Sử dụng Maven.
- Sử dụng `BigDecimal` cho số lượng, đơn giá, tiền và thuế suất; tuyệt đối không dùng `double` hoặc `float` trong phép tính tiền.
- Không dùng `double` để parse hoặc tính VAT.
- Không tự suy luận metadata bị thiếu.
- Không im lặng bỏ qua dữ liệu sai.
- Không ghi dữ liệu hàng hóa hoặc thông tin khách hàng vào log ở mức INFO/ERROR.
- API công khai phải immutable hoặc có hành vi thread-safe.
- Giữ nguyên thứ tự các dòng đầu vào trong kết quả.
- Không làm thay đổi file đầu vào.
- Không ghi đè file đầu vào.
- Không commit file build, file tạm hoặc dữ liệu hóa đơn mẫu có thông tin nhạy cảm.

## 3. Quyết định nghiệp vụ mặc định cho phiên bản 1

Hãy triển khai đúng các quyết định sau, không thay đổi nếu chưa có lý do kỹ thuật thật sự và chưa ghi rõ trong tài liệu:

### 3.1 Giá và phạm vi tính

- `unitPrice` trong CSV mặc định là đơn giá **chưa bao gồm VAT**.
- Metadata có trường `priceIncludesVat` bắt buộc để hỗ trợ cả hai chế độ:
  - `false`: giá chưa VAT. Công thức: `net = quantity * unitPrice`.
  - `true`: giá đã VAT. Tách ngược VAT theo công thức: `net = gross / (1 + vatRate)`.
- `lineTotal` không được lấy làm nguồn tính toán. Thư viện tự tính từ số lượng và đơn giá.
- Nếu cần đối chiếu tổng do hệ thống nguồn gửi vào, dùng cột tùy chọn `declaredLineTotal`; thư viện chỉ đối chiếu và trả warning/error theo cấu hình, không dùng cột này để thay thế phép tính.
- Phiên bản 1 không hỗ trợ chiết khấu, phí vận chuyển, phụ phí, tỷ giá, hàng khuyến mại, hoàn trả hoặc bù trừ. Nếu xuất hiện các trường này thì phải báo lỗi schema rõ ràng, không tự xử lý.
- Chỉ hỗ trợ số lượng không âm, đơn giá không âm và tiền tệ VND trong phiên bản 1. Metadata vẫn có trường currency để kiểm tra mở rộng; giá trị được chấp nhận trong v1 là `VND`.

### 3.2 Thuế VAT

- Mỗi dòng có một `vatRate` riêng.
- Cho phép các mức `0`, `5`, `8`, `10` phần trăm trong phiên bản 1.
- Chấp nhận biểu diễn CSV là `0`, `5`, `8`, `10`, `0%`, `5%`, `8%`, `10%`; chuẩn hóa nội bộ thành `BigDecimal` phần trăm `0`, `5`, `8`, `10`.
- Không chấp nhận mức VAT âm, lớn hơn 100 hoặc không nằm trong danh sách chính sách thuế được cấu hình.
- `0%` là mức thuế hợp lệ, không phải lỗi.
- Tính VAT theo từng dòng trước, sau đó cộng các giá trị VAT đã được làm tròn của từng dòng.
- Với giá chưa VAT: `lineNet = quantity * unitPrice`; `lineVat = lineNet * vatRate / 100`; `lineGross = lineNet + lineVat`.
- Với giá đã VAT: `lineGross = quantity * unitPrice`; `lineNet = lineGross / (1 + vatRate / 100)`; `lineVat = lineGross - lineNet`.
- Tổng phải trả là tổng `lineGross` của các dòng, không phải tính lại bằng một VAT suất chung.
- Kết quả phải có tổng theo từng mức VAT để phục vụ đối chiếu hóa đơn.

### 3.3 Làm tròn

- Tiền VND được làm tròn đến 0 chữ số thập phân.
- Dùng `RoundingMode.HALF_UP` mặc định.
- Làm tròn `lineNet`, `lineVat` và `lineGross` theo đúng thứ tự đã nêu trong tài liệu.
- Metadata bắt buộc cho phép người gọi truyền `roundingMode` và `moneyScale`; giá trị mặc định lần lượt là `HALF_UP` và `0`, nhưng API vẫn phải yêu cầu metadata đầy đủ.
- Không dùng `RoundingMode.UNNECESSARY` cho phép tính tiền nếu có thể phát sinh số lẻ.
- Phải có test cho các trường hợp 0.5, số lẻ phát sinh khi tách ngược VAT và nhiều dòng có cùng/mức VAT khác nhau.

## 4. Metadata bắt buộc

Thiết kế một immutable class hoặc record tên `InvoiceMetadata`. Người dùng bắt buộc phải truyền object này vào mỗi lần gọi API xử lý. Không cho phép `null` và không tự tạo metadata mặc định ngầm.

Các trường bắt buộc:

| Trường | Kiểu | Quy tắc |
|---|---|---|
| `schemaVersion` | `String` | Bắt buộc, v1 dùng `"1"` |
| `invoiceId` | `String` | Bắt buộc, không rỗng, tối đa 100 ký tự |
| `invoiceDate` | `LocalDate` | Bắt buộc |
| `currency` | `String` | Bắt buộc, v1 chỉ nhận `VND` |
| `sellerTaxId` | `String` | Bắt buộc, mã số thuế bên bán |
| `priceIncludesVat` | `boolean` | Bắt buộc, không có giá trị ngầm định trong request |
| `roundingMode` | `RoundingMode` | Bắt buộc |
| `moneyScale` | `int` | Bắt buộc, v1 phải là `0`, không âm, tối đa 4 |
| `taxPolicyVersion` | `String` | Bắt buộc, ví dụ `VN-2026-01` |
| `allowedVatRates` | danh sách `BigDecimal` | Bắt buộc, không rỗng, v1 mặc định đề xuất `[0, 5, 8, 10]` nhưng caller phải truyền rõ |

Các trường mở rộng tùy chọn:

- `buyerTaxId`
- `buyerName`
- `sourceSystem`
- `correlationId`
- `timezone`

Không dùng tên khách hàng, địa chỉ hoặc mã số thuế người mua làm điều kiện bắt buộc của phép tính vì thư viện có thể được dùng cho nhiều loại chứng từ nội bộ. Tuy nhiên, nếu metadata mở rộng được truyền vào thì phải bảo toàn trong đối tượng kết quả hoặc phần summary, không đưa tùy tiện vào log.

Nếu metadata không hợp lệ, ném exception nghiệp vụ có mã lỗi ổn định, ví dụ `INVALID_METADATA`.

## 5. CSV đầu vào

### Header chuẩn

CSV đầu vào có header UTF-8, thứ tự cột chuẩn như sau:

```text
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,declaredLineTotal
```

Quy tắc:

- `lineNumber`, `itemCode`, `itemName`, `quantity`, `unitPrice`, `vatRate` là bắt buộc.
- `declaredLineTotal` là tùy chọn, chỉ dùng để đối chiếu.
- Header không phân biệt hoa thường nhưng tên cột phải khớp sau khi trim.
- Dấu phân cách mặc định là dấu phẩy `,`, cho phép cấu hình `;`.
- Encoding mặc định là UTF-8.
- Hỗ trợ trường text có dấu phẩy hoặc dấu ngoặc kép theo chuẩn CSV.
- Hỗ trợ dòng trống ở cuối file nhưng không chấp nhận dòng dữ liệu thiếu cột.
- `lineNumber` là số nguyên dương và không được trùng.
- `itemCode` không được rỗng và tối đa 100 ký tự.
- `itemName` không được rỗng và tối đa 500 ký tự.
- `quantity` phải là số dương hoặc bằng 0 theo cấu hình; mặc định phải lớn hơn 0.
- `unitPrice` phải không âm.
- Số thập phân trong CSV dùng dấu chấm `.`; không chấp nhận dấu phân cách hàng nghìn.
- Không tự hiểu chuỗi rỗng như số 0.
- Không chấp nhận số âm trong v1.

Phải xây dựng parser CSV chuẩn hoặc sử dụng thư viện CSV ổn định, không tách chuỗi thủ công bằng `String.split(",")`.

## 6. CSV đầu ra

CSV đầu ra phải bảo toàn các trường nhận diện dòng và bổ sung kết quả:

```text
lineNumber,itemCode,itemName,quantity,unitPrice,vatRate,lineNet,lineVat,lineGross,declaredLineTotal,comparisonStatus
```

Sau phần dữ liệu dòng, chọn một trong hai thiết kế sau và ghi rõ trong tài liệu. Ưu tiên thiết kế tách summary thành object kết quả và file summary riêng để CSV dòng luôn có schema đồng nhất:

1. API trả `InvoiceCalculationResult` gồm danh sách dòng, summary và totalsByVatRate; caller tự chọn writer; hoặc
2. API ghi thêm một file summary riêng.

Không trộn các dòng `TOTAL` vào CSV dòng nếu điều đó làm CSV không còn schema bảng ổn định.

`InvoiceCalculationResult` tối thiểu phải có:

- metadata đã chuẩn hóa;
- danh sách kết quả theo dòng;
- `totalNet`;
- `totalVat`;
- `totalGross` là tổng khách hàng phải thanh toán;
- tổng net/VAT/gross theo từng mức VAT;
- số dòng hợp lệ;
- danh sách warning;
- thông tin đối chiếu `declaredLineTotal` nếu có.

## 7. API công khai đề xuất

Thiết kế API đơn giản, dễ dùng và không gắn cứng với filesystem. Tối thiểu phải hỗ trợ `InputStream`/`Reader` và `OutputStream`/`Writer`, đồng thời có overload dùng `Path` nếu hợp lý.

API gợi ý:

```java
InvoiceCalculationResult calculate(
    InvoiceMetadata metadata,
    Reader input,
    CsvProcessingOptions options
);

void writeResult(
    InvoiceCalculationResult result,
    Writer output,
    CsvProcessingOptions options
);
```

Có thể cung cấp facade:

```java
InvoiceCalculator calculator = new InvoiceCalculator();
InvoiceCalculationResult result = calculator.calculate(metadata, reader, options);
calculator.writeResult(result, writer, options);
```

`CsvProcessingOptions` nên chứa delimiter, encoding liên quan đến tầng I/O, validation mode, sai số đối chiếu và giới hạn số dòng. API phải có Javadoc đầy đủ.

Tách các trách nhiệm sau thành các class riêng:

- parser CSV;
- validator;
- VAT calculator;
- result model;
- result writer;
- exception/error model.

Không để parser CSV tự chứa logic VAT và không để writer tự tính lại số tiền.

## 8. Xử lý lỗi

Mặc định xử lý theo fail-fast: nếu có lỗi cấu trúc hoặc lỗi dữ liệu, không trả kết quả tính tiền một phần.

Thiết kế exception có:

- `errorCode` ổn định;
- số dòng CSV;
- tên cột;
- giá trị lỗi đã được che/mask nếu là dữ liệu nhạy cảm;
- thông điệp rõ ràng cho người tích hợp.

Tối thiểu có các mã:

- `INVALID_METADATA`
- `INVALID_HEADER`
- `MISSING_REQUIRED_COLUMN`
- `INVALID_FIELD_VALUE`
- `INVALID_NUMBER_FORMAT`
- `INVALID_VAT_RATE`
- `DUPLICATE_LINE_NUMBER`
- `UNSUPPORTED_CURRENCY`
- `UNSUPPORTED_SCHEMA_VERSION`
- `CSV_PARSE_ERROR`
- `INPUT_TOO_LARGE`
- `DECLARED_TOTAL_MISMATCH`

`DECLARED_TOTAL_MISMATCH` mặc định là warning nếu `declaredLineTotal` có sai lệch nhỏ hơn hoặc bằng tolerance cấu hình; nếu vượt tolerance thì là error. Không so sánh tiền bằng `equals` của `BigDecimal` khi khác scale; dùng giá trị số sau chuẩn hóa.

Giới hạn mặc định:

- tối đa 100.000 dòng mỗi file;
- tối đa 10 MB nếu API nhận file/path;
- tối đa 500 ký tự cho một field text;
- giới hạn phải cấu hình được.

## 9. Cấu trúc Maven mong muốn

Tạo một project Maven độc lập trong thư mục `invoice` với cấu trúc rõ ràng, ví dụ:

```text
invoice/
  pom.xml
  README.md
  docs/
    DESIGN.md
    CSV-SCHEMA.md
    USER-GUIDE.md
    REQUIREMENTS-TRACEABILITY.md
  src/main/java/.../invoice/
    api/
    csv/
    domain/
    exception/
    service/
  src/test/java/.../invoice/
```

Không thêm Spring Boot runtime dependency. Có thể dùng một thư viện CSV phổ biến, ổn định và có license phù hợp; nếu chọn thư viện ngoài thì giải thích trong `DESIGN.md`. Nếu có thể đáp ứng tốt bằng JDK thì ưu tiên giảm dependency.

## 10. Kiểm thử bắt buộc

Viết test tự động bằng JUnit 5 và AssertJ hoặc thư viện assertion tương đương. Tối thiểu phải có:

- một dòng VAT 0%, 5%, 8%, 10%;
- nhiều mức VAT trong cùng hóa đơn;
- số lượng bằng 0 và kiểm tra chính sách;
- đơn giá bằng 0;
- giá chưa VAT;
- giá đã VAT và tách ngược VAT;
- tổng VAT theo từng dòng và tổng hóa đơn;
- tổng tiền khách hàng phải trả;
- làm tròn HALF_UP;
- làm tròn theo moneyScale khác nhau;
- trường hợp `9.5`, `10.5` và các giá trị biên;
- CSV có dấu phẩy trong tên hàng được quote;
- CSV dùng delimiter `;`;
- UTF-8 và tiếng Việt;
- header thiếu hoặc sai;
- thiếu field bắt buộc;
- số không hợp lệ, số âm, VAT không được phép;
- dòng trùng `lineNumber`;
- metadata null hoặc thiếu từng trường;
- declared total khớp và không khớp;
- file rỗng;
- file chỉ có header;
- vượt giới hạn số dòng/kích thước;
- giữ nguyên thứ tự đầu vào;
- kết quả bất biến sau khi tính;
- dùng đồng thời nhiều thread trên cùng instance calculator;
- kiểm tra không dùng `double`/`float` trong source tính toán tiền.

Tạo bộ dữ liệu mẫu không chứa thông tin thật. Có ít nhất một test property/invariant:

```text
totalGross = totalNet + totalVat
sum(lineVat) = totalVat
sum(lineGross) = totalGross
```

Các invariant phải tính theo quy tắc làm tròn đã công bố.

## 11. Tài liệu bắt buộc

Viết tài liệu tiếng Việt hoặc tiếng Anh rõ ràng, bao gồm:

- mục tiêu và phạm vi;
- cách build và chạy test;
- cách thêm dependency Maven;
- ví dụ Java hoàn chỉnh từ metadata + CSV input đến CSV output;
- schema đầu vào và đầu ra;
- tất cả công thức VAT;
- ví dụ giá đã VAT/chưa VAT;
- quy tắc làm tròn;
- danh sách metadata bắt buộc và ý nghĩa từng trường;
- danh sách mã lỗi và cách xử lý;
- giới hạn hiệu năng và kích thước file;
- hướng dẫn dùng trong ứng dụng đa luồng;
- chính sách tương thích phiên bản;
- cách mở rộng chính sách thuế trong tương lai;
- bảng truy vết requirement đến class và test.

Javadoc cho mọi public class, public method, public constructor và public field nếu có.

## 12. Quy trình thực hiện

Hãy làm việc theo các bước sau:

1. Khảo sát workspace hiện tại và xác định thư mục/project cần tạo.
2. Nếu đã có code liên quan trong `invoice`, đọc và tái sử dụng hợp lý; không xóa thay đổi hiện có của người dùng.
3. Tạo thiết kế ngắn trước khi code, sau đó triển khai theo từng lát nhỏ.
4. Viết test cho các quy tắc nghiệp vụ quan trọng trước hoặc đồng thời với implementation.
5. Chạy `mvn test`, `mvn verify` và kiểm tra Javadoc nếu project đã cấu hình.
6. Sửa toàn bộ lỗi compile/test thuộc phạm vi task.
7. Rà soát API public, dependency, xử lý dữ liệu nhạy cảm và thread-safety.
8. Cập nhật README và tài liệu thiết kế.
9. Cuối cùng báo cáo: file đã tạo/thay đổi, quyết định nghiệp vụ, test đã chạy, kết quả build và các giới hạn còn lại.

## 13. Tiêu chí nghiệm thu

Task chỉ được coi là hoàn thành khi:

- project build thành công bằng Java 17+ và Maven 3.8+;
- toàn bộ test tự động pass;
- kết quả được tính bằng `BigDecimal` và không có lỗi sai số nhị phân;
- metadata là bắt buộc và được validate;
- nhiều mức VAT trong cùng hóa đơn cho kết quả đúng;
- có tổng theo từng dòng, tổng VAT và tổng khách hàng phải trả;
- CSV input/output có schema được tài liệu hóa;
- lỗi có mã, dòng và cột rõ ràng;
- API không phụ thuộc Spring Boot;
- có Javadoc, README, hướng dẫn sử dụng và tài liệu quy tắc làm tròn;
- có test cho các trường hợp biên và các invariant tài chính;
- không còn TODO quan trọng hoặc phần giả lập trong code production.

Không chỉ trả lời bằng kế hoạch hoặc sinh skeleton. Hãy thực sự tạo source code, test và tài liệu trong workspace, sau đó xác nhận kết quả bằng lệnh build/test thực tế.
