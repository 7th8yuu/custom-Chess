# Custom Chess

Ứng dụng cờ vua cho Android, viết bằng Kotlin và Jetpack Compose. Bàn cờ "sơn mài", bộ quân vẽ riêng bằng vector, chơi được cả khi không có mạng.

## Chế độ chơi

| Chế độ | Cần gì |
| --- | --- |
| **Chơi với máy** | Không cần mạng. Engine tự viết (alpha-beta), 5 cấp độ. |
| **Chơi với AI qua API** | API key của **Gemini** (dễ lấy nhất, có gói miễn phí), **OpenAI** hoặc **Claude** (Sonnet 5.5, effort thấp). Key chỉ lưu trên máy. |
| **Chơi với bạn bè** | **Wi-Fi** (một máy mở phòng, máy kia tự tìm thấy hoặc nhập IP), **Bluetooth** (RFCOMM), hoặc **chung một máy**. Không cần Internet. |
| **Đấu online** | Ghép trận với người chơi thật qua **Lichess** (Board API). Cần đăng nhập Lichess hoặc Google. |

Đầy đủ luật: nhập thành, bắt tốt qua đường, phong cấp, chiếu hết, hòa cờ, thiếu quân, luật 50 nước, lặp thế 3 lần, đồng hồ, xin hòa, đầu hàng, đi lại, kéo thả quân.

### Tài khoản

- **Đăng nhập Lichess** (khuyên dùng): một lần bấm, Lichess cấp quyền chơi cờ cho ứng dụng và ứng dụng tự nhận, không cần dán token. Dùng được mọi chế độ, kể cả đấu online, và hiện điểm xếp hạng của bạn.
- **Đăng nhập Google**: dùng được mọi chế độ. Muốn đấu online thì nối thêm tài khoản Lichess (một lần bấm trong màn Đấu online).
- **Khách**: dùng được mọi chế độ offline và chơi với AI bằng API key của riêng bạn. Không đấu online được; đăng nhập Lichess lúc nào cũng nâng cấp được.

### Giao diện

**Cài đặt → Giao diện** có xem trước trực tiếp:

- **Bàn cờ ảnh**: 4 ảnh (Trời xanh, Chiều tím, Hoàng hôn, Mây hồng) làm mặt bàn cờ, cùng 3 bàn cờ cổ điển.
- **Màu Material 3 theo ảnh**: chọn bàn cờ nào thì toàn bộ ứng dụng trượt sang bảng màu sinh từ màu chủ đạo của ảnh đó (tắt được).
- **Sáng / tối / theo hệ thống**.
- **8 bộ quân**: Sơn mài, Hoàng gia, Gỗ, Bạc & Vàng, Ngọc, Theo ảnh (quân đổi màu theo ảnh), Neon, Nét vẽ.

### Về Chess.com

Chess.com chỉ có API đọc dữ liệu công khai (hồ sơ, ván đã chơi), **không cho ứng dụng ngoài đi quân hay ghép trận**. Vì vậy phần đấu online dùng Lichess, nơi cho phép ứng dụng bên thứ ba chơi miễn phí.

## Trong ván cờ

Các công tắc nằm ở Cài đặt, nhóm "Ván cờ":

- **Đi trước (premove)**: chọn nước ngay khi chưa tới lượt, quân của bạn được tô xanh kèm mũi tên; khi đối thủ đi xong, nếu nước đó còn hợp lệ thì ván tự đi (chạm lại để hủy). Không dùng khi chơi chung một máy.
- **Xác nhận nước đi**: sau khi chọn nước hiện nút "Đi" và "Hủy", chạm bàn cờ cũng là hủy.
- **Nút đầu hàng gọn**: nút Đầu hàng thu thành biểu tượng cờ nhỏ ở cuối hàng nút.
- **Bảng nước đi**: bấm vào dải nước đi dưới bàn cờ để mở bảng số nước, Trắng, Đen; nước vừa đi được tô nổi.
- Hiệu ứng mới (tắt cùng công tắc Hiệu ứng): vua bị chiếu nhấp nháy đỏ, đồng hồ còn dưới 20 giây đập nhịp, thanh xác nhận trượt vào, dòng trạng thái mờ dần khi đổi.

## Vẽ trên bàn cờ

Trong ván và khi xem lại ván, giữ ngón tay trên một ô rồi kéo sang ô khác để vẽ mũi tên, giữ yên một ô để tô màu (vẽ lại đúng nét đó thì xóa,
vẽ cùng nét khác màu thì đổi màu). Dưới bàn cờ có nút Vẽ (kéo là vẽ luôn, không cần giữ, và không đi quân được trong lúc bật), bốn màu và nút Xóa.
Nét vẽ tự xóa khi có nước mới hoặc khi chạm thường vào bàn cờ, giống chess.com. Bật/tắt tính năng ở Cài đặt, mục Ván cờ.

Kiểu nét vẽ chỉnh bằng JSON ở Giao diện, mục Nét vẽ trên bàn cờ (dán JSON, tải từ link, xem mẫu, đặt lại). Mọi trường đều tùy chọn:

```json
{
  "name": "Kiểu của tôi",
  "colors": ["#2FB36D", "#E5484D", "#3B82F6", "#F2B63D"],
  "arrowOpacity": 0.85,
  "arrowWidth": 0.17,
  "squareOpacity": 0.55,
  "squareStyle": "fill"
}
```

`colors` có 1 đến 6 màu (#RGB, #RRGGBB hoặc #AARRGGBB), `squareStyle` là `fill` (tô kín ô) hoặc `ring` (vòng tròn).

## Xem lại và phân tích ván

Vào Lịch sử ván, chạm một ván để xem lại. Engine nội bộ chấm từng nước (chạy ngay trên máy, không cần mạng, không tốn phí):
nước xuất sắc (!!), nước rất hay (!), nước tốt nhất, nước tốt, thiếu chính xác, sai lầm, sai lầm nghiêm trọng, kèm độ chính xác của mỗi bên.
Có hai mức: phân tích nhanh (khoảng nửa phút) và phân tích kỹ (sâu hơn, lâu hơn nhiều).

Ván ở nơi khác (chess.com, Lichess...) xem lại được bằng cách dán PGN: Lịch sử ván, Dán PGN. Ván được lưu vào lịch sử với nhãn PGN.

Hai nhãn "xuất sắc" và "rất hay" được đặt điều kiện chặt vì engine chạy ở độ sâu thấp: "xuất sắc" là nước hy sinh quân thật sự
(mất ít nhất một quân nhẹ đổi lấy tốt) mà vẫn giữ được thế khi chưa thắng chắc; "rất hay" là nước duy nhất giữ được thế. Chỉ mang tính tham khảo.

## Ngôn ngữ

Giao diện có sẵn tiếng Việt (gốc), English, 简体中文 và Español. Vào Cài đặt, mục Ngôn ngữ để đổi (có tùy chọn theo hệ thống),
đổi xong có hiệu lực ngay và AI (phân tích, chơi với AI) cũng trả lời bằng ngôn ngữ đó.

Muốn thêm ngôn ngữ khác ngay trên máy: Cài đặt, Ngôn ngữ, Xuất file mẫu, dịch phần giá trị (nhờ AI hoặc người quen),
điền mục `_meta` (`code`, `native`, `english`) rồi Nhập file ngôn ngữ. Các câu thiếu bản dịch tự hiện tiếng Việt.

Với người phát triển: mã nguồn viết bằng tiếng Việt, bản dịch là từ điển `app/src/main/assets/i18n/<mã>.json` (khóa là câu tiếng Việt,
câu có dữ liệu chèn dùng `{0}`, `{1}`). `python3 tools/i18n/extract.py app/src/main/java ra.json` liệt kê mọi câu cần dịch,
`python3 tools/i18n/check.py <mã>` báo câu thiếu hoặc sai chỗ chèn. Bài kiểm thử `LanguageFilesTest` chạy trong CI.

## Build bằng GitHub Actions

Mỗi lần push lên `main` (hoặc bấm *Run workflow*), workflow `.github/workflows/android.yml` sẽ chạy unit test rồi `./gradlew assembleDebug`. Tải APK ở tab **Actions → lần chạy → Artifacts → CustomChess-debug-apk**.

Đẩy tag dạng `v1.0.0` thì APK còn được đính vào mục **Releases**.

Build thủ công trên máy: cài JDK 17 và Android SDK, rồi chạy `./gradlew assembleDebug`. APK nằm ở `app/build/outputs/apk/debug/`.

## Bật đăng nhập Google

Google chỉ cho đăng nhập khi biết ứng dụng của bạn, nên cần một OAuth client do chính bạn tạo (không đóng gói sẵn được). Ứng dụng có sẵn màn hướng dẫn kèm nút chép tên gói và SHA-1 (bấm *Hướng dẫn bật đăng nhập Google*), tóm tắt:

1. Vào [Google Cloud Console](https://console.cloud.google.com/), tạo project. Ở **APIs & Services → OAuth consent screen**, chọn *External*, điền tên ứng dụng, thêm email của bạn vào *Test users*.
2. **Credentials → Create credentials → OAuth client ID → Android**
   - Package name: `com.customchess.app`
   - SHA-1: `8E:F2:7C:3E:9D:FA:17:BA:EF:0C:7F:ED:ED:9B:05:CD:B5:FF:BA:02` (khóa debug cố định nằm trong `keystore/`, nên giá trị này không đổi giữa các lần build)
3. Xong. Mở ứng dụng và bấm *Đăng nhập bằng Google*; không cần build lại, không cần secret.

(Tùy chọn) Nếu tạo thêm **OAuth client ID loại Web** và lưu Client ID vào secret `GOOGLE_WEB_CLIENT_ID` của repo (**Settings → Secrets and variables → Actions**), bản build sẽ dùng Credential Manager của Android thay cho đường cổ điển. Khi build trên máy, đặt biến môi trường `GOOGLE_WEB_CLIENT_ID` hoặc điền vào `gradle.properties`.

Chưa cấu hình thì ứng dụng vẫn chạy bình thường: dùng Lichess hoặc chế độ khách.

> Ứng dụng đang ký bằng khóa debug đặt sẵn trong repo để mọi bản build có cùng chữ ký. Muốn phát hành lên Google Play, hãy tạo khóa riêng và đổi `signingConfigs` trong `app/build.gradle.kts`.

## Đấu online (Lichess)

Bấm **Đăng nhập bằng Lichess** (màn hình đầu, Cài đặt hoặc màn Đấu online): một tab trình duyệt mở ngay trong ứng dụng để bạn cấp quyền `board:play`, xong tự quay về (địa chỉ nhận kết quả `com.customchess.app://oauth`, theo chuẩn PKCE nên không cần đăng ký ứng dụng hay lưu bí mật). Token lưu trên máy và được kiểm tra lại mỗi lần mở ứng dụng; hết hạn thì ứng dụng yêu cầu đăng nhập lại. Cũng có thể dán token cá nhân tạo tại [lichess.org/account/oauth/token](https://lichess.org/account/oauth/token). Board API chỉ cho chơi từ blitz trở lên (từ 3 phút).

## Lấy API key AI

| Hãng | Nơi lấy key | Model gợi ý |
| --- | --- | --- |
| Gemini | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | `gemini-2.5-flash` |
| OpenAI | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) | `gpt-4o-mini` |
| Claude | [console.anthropic.com/settings/keys](https://console.anthropic.com/settings/keys) | `claude-sonnet-5-5` (effort `low`) |

Tên model thay đổi theo thời gian: trong **Thiết lập → API key và model**, bấm *Tải danh sách model* để chọn đúng model tài khoản của bạn đang dùng được. Với OpenAI có thể đổi địa chỉ API (mục nâng cao) để dùng DeepSeek, Groq, OpenRouter hoặc máy chủ tự dựng.

Mỗi nước đi, ứng dụng gửi cho AI thế cờ (FEN), lịch sử nước đi và **danh sách nước hợp lệ**, rồi chỉ chấp nhận câu trả lời nằm trong danh sách đó. Nếu AI lỗi, ứng dụng thử lại, rồi (nếu bật) để engine nội bộ đi thay.

## Cấu trúc mã nguồn

```
app/src/main/java/com/customchess/
  core/   luật cờ, engine, đọc nước đi (Kotlin thuần, có unit test)
  ai/     gọi API Gemini / OpenAI / Claude
  net/    giao thức, Wi-Fi, Bluetooth, Lichess
  data/   lưu cài đặt, đăng nhập Google
  vm/     ViewModel điều phối ván cờ, đồng hồ, kết nối
  ui/     giao diện Compose
```
## prompt để tạo quân cờ ở file prompt.md
Chạy test: `./gradlew testDebugUnitTest`.
