# Nhật ký thay đổi

Tệp này cũng được dùng làm ghi chú của bản phát hành `latest` (xem `.github/workflows/android.yml`).

## Cập nhật chi tiết: Đợt 1 – 3 (gộp vào `main` bằng merge commit `cdac739`)

APK mới nằm ở release `latest` (CustomChess-latest.apk). Dưới đây là toàn bộ thay đổi, ghi theo từng đợt.

---

### Đợt 1: Trong ván cờ

**Vẽ trên bàn cờ (mũi tên, tô ô)**
- Kiểu vẽ tùy chỉnh nhập bằng JSON, các trường:
  - `name`: tên kiểu
  - `colors`: từ 1 đến 6 màu, định dạng `#RGB`, `#RRGGBB` hoặc `#AARRGGBB`
  - `arrowOpacity`: độ đậm mũi tên, 0.1 đến 1
  - `arrowWidth`: bề dày thân mũi tên theo cạnh ô, 0.05 đến 0.35
  - `squareOpacity`: độ đậm ô tô, 0.1 đến 1
  - `squareStyle`: `"fill"` (tô đầy) hoặc `"ring"` (viền vòng), mặc định `fill`
- Số ngoài khoảng cho phép được kẹp lại về khoảng đó. Màu thừa quá 6 bị bỏ. JSON sai cú pháp hoặc màu không hợp lệ thì hiện thông báo lỗi tiếng Việt, không lưu.

**Đi trước (premove)**: Cài đặt → Ván cờ, mặc định **tắt**
- Khi chưa tới lượt, chọn quân của mình rồi chọn ô đích để đặt nước đi trước. Nước này hiện nền xanh kèm mũi tên trên bàn cờ.
- Khi đối thủ đi xong, app chờ **200 ms** (để hiệu ứng nước của đối thủ chạy xong) rồi mới đi.
- Nước đi trước chỉ được đi khi vẫn hợp lệ ở thế cờ mới. Nếu không hợp lệ, hoặc ván đã kết thúc, hoặc có nước khác chen vào, nó bị bỏ và không đi gì cả.

**Xác nhận nước đi**: Cài đặt → Ván cờ, mặc định **tắt**
- Khi bật, đi quân xong sẽ hiện thanh xác nhận: nút "Đi <nước đi>" (ví dụ "Đi Nf3") và nút "Hủy". Bàn cờ hiển thị trước thế cờ sau nước đó. Bấm "Đi" để đi thật, "Hủy" để chọn lại.

**Nút đầu hàng gọn**: mặc định **bật**
- Nút đầu hàng thu thành biểu tượng lá cờ vẽ bằng Canvas, nhỏ hơn nút cũ.

**Bảng nước đi**
- Dải nước đi có nút mở/đóng. Khi mở sẽ ra bảng nước đi đầy đủ.

**Hiệu ứng** (đều tắt được khi tắt hiệu ứng trong cài đặt)
- Chuyển trạng thái (đến lượt, chiếu, kết thúc) dùng crossfade.
- Đồng hồ **nhấp nháy khi còn dưới 20 giây** ở bên đang đến lượt.
- Ô vua bị chiếu có quầng sáng nhấp nháy.

---

### Đợt 2: Sảnh Lichess

- **Bốn tab**: Xếp cặp nhanh, Phòng chờ, Cờ qua thư, Thách đấu. Phía trên có công tắc **Giao hữu / Xếp hạng**, áp dụng cho cả bốn tab. Chế độ Giao hữu không đổi điểm.
- **Xếp cặp nhanh**: lưới thời gian 1+0, 2+1, 3+0, 3+2, 5+0, 5+3, 10+0, 10+5, 15+10, 30+0, 30+20.
- **Phòng chờ**: tạo phòng của chính mình trên Lichess. Tự nhập phút (1–180) và giây cộng thêm (0–180), chọn màu quân, nhập khoảng điểm đối thủ min/max trong 0–4000 (giá trị sai báo lỗi, không gửi đi). Lichess không có API công khai để xem phòng chờ của người khác, nên app không có danh sách đó.
- **Cờ qua thư (correspondence)**: chọn số ngày mỗi nước 1, 2, 3, 5, 7, 10 hoặc 14. Tìm xong trả về ngay, không phải đứng chờ. Thoát ván kiểu này không hỏi xác nhận đầu hàng và quay về màn hình trước.
- **Ván đang chơi**: danh sách các ván Lichess đang diễn ra (kể cả cờ qua thư), bấm để mở lại. Ván nào có xếp hạng hay giao hữu đều có nhãn.
- **Giới hạn cần biết**: tìm ván tự động (seek) của Board API chỉ dùng được từ **Rapid trở lên**.
  - Các ô Bullet và Blitz bị làm mờ.
  - Blitz vẫn chơi được qua thách đấu trực tiếp.
  - Bullet hiện chưa hỗ trợ.
  - Cách phân loại tốc độ: ước tính = phút × 60 + 40 × số giây cộng thêm. Dưới 180 là Bullet, dưới 480 là Blitz, dưới 1500 là Rapid, còn lại là Classical.

---

### Đợt 3: Thách đấu

**Hộp thư lời mời + popup**
- Lời mời thách đấu (Lichess và Wi-Fi) gom vào một hộp thư. Có popup nổi khi có lời mời mới, với các nút Chấp nhận, Từ chối, Để sau.
- Popup không hiện khi đang trong một ván cờ.

**Thách người chơi Lichess theo tên**
- Nhập tên người chơi (2–20 ký tự, chữ, số, `_`, `-`, bắt đầu bằng chữ hoặc số), chọn thời gian, có/không xếp hạng và màu quân. Hỗ trợ cả cờ qua thư.
- Bên gửi có thể hủy lời thách của mình. Thông báo khi đối phương từ chối hoặc hủy.

**Nhận thách đấu Lichess nền**
- Cài đặt → Thách đấu → "Nhận lời thách đấu Lichess", mặc định **bật**. App giữ một luồng sự kiện Lichess ở nền, mất kết nối thì tự nối lại sau 15 giây.
- **Thay đổi hành vi**: ván bắt đầu ở nơi khác (web, app khác) không còn tự mở trong app nữa. Chúng xuất hiện trong mục "Ván đang chơi".

**Thách đấu qua Wi-Fi (LAN)**
- Cài đặt → Thách đấu → "Nhận lời thách đấu qua Wi-Fi", mặc định **tắt**.
- Khi bật, máy mở cổng TCP **8767** để nhận lời thách và phát thông báo UDP (cổng **8766**) cứ 2 giây một lần. Chỉ chạy khi bật cài đặt và app đang mở.
- Máy khác trên cùng Wi-Fi thấy máy đang nhận thách và có nút "Thách" kèm hộp thoại chọn màu và thời gian.
- Giao thức thêm 2 lệnh: `CHALLENGE <màu 0/1/2> <giây gốc> <giây cộng>` và `DECLINE`. Có kiểm tra giá trị (màu 0–2, thời gian gốc tối đa 10800 giây, giây cộng tối đa 180).
- Mỗi lúc chỉ giữ **một** lời thách đang chờ, lời thách khác đến cùng lúc sẽ nhận `DECLINE`. Kết nối im lặng quá **20 giây** thì bị đóng. Bên gửi chờ tối đa **45 giây** và có nút hủy.
- Danh sách máy quét Wi-Fi đã lọc bỏ địa chỉ của chính máy mình.

---

### Kiểm thử và ngôn ngữ
- Test mới: `LobbyTest`, `LichessParseTest`, thêm ca cho `ProtocolTest` (Challenge, Decline, cờ listening trong announce).
- Bản dịch tiếng Anh, Trung, Tây Ban Nha: **930 khóa**, khớp nhau (có test `LanguageFilesTest` kiểm tra).
- README thêm các mục: Sảnh Lichess, Thách đấu, Trong ván cờ.
- CI (GitHub Actions) xanh trên mọi commit trước khi merge.

### Chưa kiểm chứng
- Chưa chạy thử trên thiết bị thật hay với server Lichess thật. Mọi thứ mới qua build và test tự động.
- Quyền token cho các endpoint thách đấu Lichess (`board:play`) theo tài liệu thì đủ, nhưng chưa thử trực tiếp. Nếu lời thách bị từ chối với lỗi quyền, đăng nhập Lichess lại với đủ quyền.
- Việc Board API chỉ cho seek từ Rapid trở lên là suy ra từ tài liệu và mã nguồn Lichess, chưa thử trực tiếp.
