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

Chạy test: `./gradlew testDebugUnitTest`.
