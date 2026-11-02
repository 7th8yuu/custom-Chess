package com.customchess.data

private const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * Một bước của bài học. [expect] rỗng nghĩa là bước chỉ để đọc (nút Tiếp tục);
 * ngược lại người học phải đi một trong các nước [expect] (ký hiệu UCI như "e2e4") trên thế [fen].
 */
class LessonStep(
    val text: String,
    val fen: String,
    val expect: List<String> = emptyList(),
    val hint: String = "",
    val done: String = "Chính xác!",
)

class Lesson(
    val id: String,
    val title: String,
    val desc: String,
    val level: String,
    val steps: List<LessonStep>,
)

val LESSONS: List<Lesson> = listOf(
    Lesson(
        "basics", "Làm quen bàn cờ", "Bàn cờ, các quân và mục tiêu của ván cờ", "Cơ bản",
        listOf(
            LessonStep("Bàn cờ có 64 ô, gồm 8 hàng (đánh số 1 đến 8) và 8 cột (đánh số a đến h). Hai bên Trắng và Đen thay phiên nhau, Trắng đi trước.", START),
            LessonStep("Mỗi bên có 16 quân: 8 tốt, 2 xe, 2 mã, 2 tượng, 1 hậu và 1 vua. Quân trắng nằm ở hai hàng dưới, quân đen ở hai hàng trên.", START),
            LessonStep("Mục tiêu của ván cờ là chiếu hết vua đối phương: vua bị tấn công mà không còn cách nào thoát. Bắt được vua không phải là mục tiêu, chiếu hết mới là kết thúc.", START),
            LessonStep("Thử đi nước đầu tiên: nhấn tốt ở ô e2 rồi nhấn ô e4 để đưa tốt lên giữa bàn cờ.", START, listOf("e2e4"), "Tốt trước mặt vua trắng, đi lên hai ô."),
        ),
    ),
    Lesson(
        "pawn", "Quân Tốt", "Cách đi, ăn chéo, phong cấp và bắt tốt qua đường", "Cơ bản",
        listOf(
            LessonStep("Tốt chỉ đi thẳng về phía trước một ô. Riêng nước đầu tiên của mỗi tốt được phép đi hai ô. Hãy đưa tốt e2 lên hai ô.", "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1", listOf("e2e4"), "Nhấn e2 rồi e4."),
            LessonStep("Tốt không ăn quân đứng thẳng trước mặt mà ăn chéo về phía trước một ô. Hãy dùng tốt e4 ăn tốt đen ở d5.", "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1", listOf("e4d5"), "Tốt ăn chéo, nhấn e4 rồi d5."),
            LessonStep("Khi tốt đi đến hàng cuối, nó được phong thành quân khác, hầu như luôn là Hậu vì mạnh nhất. Hãy đưa tốt lên e8 và chọn Hậu.", "8/4P3/8/8/8/8/k7/4K3 w - - 0 1", listOf("e7e8q"), "Nhấn e7, e8 rồi chọn Hậu."),
            LessonStep("Bắt tốt qua đường: khi tốt đối phương vừa đi hai ô và đứng cạnh tốt của bạn, ngay nước kế tiếp bạn có thể ăn nó như thể nó chỉ đi một ô. Tốt đen vừa đi d7-d5, hãy ăn nó bằng tốt e5.", "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", listOf("e5d6"), "Tốt e5 đi chéo tới d6, ô mà tốt đen vừa bỏ qua."),
        ),
    ),
    Lesson(
        "knight", "Quân Mã", "Nước đi hình chữ L và khả năng nhảy qua quân khác", "Cơ bản",
        listOf(
            LessonStep("Mã đi hình chữ L: hai ô theo một hướng rồi một ô vuông góc. Mã là quân duy nhất được nhảy qua quân khác. Hãy đưa mã từ g1 tới f3.", "4k3/8/8/8/8/8/8/4K1N1 w - - 0 1", listOf("g1f3"), "Từ g1: một ô sang trái, hai ô lên."),
            LessonStep("Mã ăn quân ở ô nó đáp xuống. Hãy dùng mã d4 ăn tốt đen ở f5.", "4k3/8/8/5p2/3N4/8/8/4K3 w - - 0 1", listOf("d4f5"), "Từ d4: hai ô sang phải, một ô lên."),
        ),
    ),
    Lesson(
        "bishop", "Quân Tượng", "Đi chéo và hai tượng khác màu ô", "Cơ bản",
        listOf(
            LessonStep("Tượng đi chéo bao nhiêu ô tùy ý miễn không bị chặn. Mỗi tượng chỉ đi trên một màu ô suốt ván. Hãy dùng tượng c1 ăn tốt đen ở f4.", "4k3/8/8/8/5p2/8/8/2B1K3 w - - 0 1", listOf("c1f4"), "Đường chéo c1, d2, e3, f4."),
            LessonStep("Hai tượng của một bên bổ sung nhau vì đi trên hai màu ô khác nhau. Cặp tượng thường rất mạnh ở tàn cuộc.", "4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1"),
        ),
    ),
    Lesson(
        "rook", "Quân Xe", "Đi thẳng theo hàng và cột", "Cơ bản",
        listOf(
            LessonStep("Xe đi thẳng theo hàng hoặc cột. Xe mạnh nhất khi có đường dài thông thoáng. Hãy dùng xe a1 ăn xe đen ở a8.", "r3k3/8/8/8/8/8/8/R3K3 w - - 0 1", listOf("a1a8"), "Đi dọc cột a lên tận hàng 8."),
        ),
    ),
    Lesson(
        "queen", "Quân Hậu", "Quân mạnh nhất, đi như xe và tượng cộng lại", "Cơ bản",
        listOf(
            LessonStep("Hậu đi được như xe lẫn tượng: thẳng và chéo, xa tùy ý. Đó là quân mạnh nhất nên đừng vội đem ra mạo hiểm. Hãy dùng hậu d1 ăn tốt ở h5.", "4k3/8/8/7p/8/8/8/3QK3 w - - 0 1", listOf("d1h5"), "Đi chéo d1, e2, f3, g4, h5."),
        ),
    ),
    Lesson(
        "castling", "Vua và nhập thành", "Giữ vua an toàn bằng nước nhập thành", "Cơ bản",
        listOf(
            LessonStep("Vua đi một ô theo mọi hướng và không bao giờ được đi vào ô đang bị tấn công. Vua quan trọng nhất: mất vua là thua ván.", "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"),
            LessonStep("Nhập thành là nước đi đặc biệt: vua đi hai ô về phía xe, xe nhảy sang bên kia vua. Điều kiện: vua và xe chưa từng đi, giữa hai quân không có quân nào, vua không đang bị chiếu và không đi qua ô bị tấn công. Hãy nhập thành bên cánh vua (gần h1): nhấn vua e1 rồi ô g1.", "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", listOf("e1g1"), "Vua đi hai ô sang phải."),
            LessonStep("Nhập thành bên cánh hậu (gần a1): vua đi hai ô sang trái, tới c1.", "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", listOf("e1c1"), "Vua đi hai ô sang trái."),
        ),
    ),
    Lesson(
        "mate", "Chiếu và chiếu hết", "Kết thúc ván cờ bằng nước chiếu hết", "Cơ bản",
        listOf(
            LessonStep("Chiếu là khi vua bị tấn công. Người bị chiếu phải thoát ngay: di chuyển vua, chặn đường chiếu hoặc ăn quân đang chiếu. Nếu không làm được cả ba thì đó là chiếu hết và ván kết thúc.", START),
            LessonStep("Vua đen bị ba tốt của chính nó chắn lối thoát. Hãy chiếu hết bằng xe: đưa xe a1 lên a8.", "6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1", listOf("a1a8"), "Chiếu hết hàng cuối: xe lên hàng 8."),
            LessonStep("Hậu và vua phối hợp rất mạnh. Vua trắng đã chặn các ô thoát, chỉ cần hậu chiếu từ xa: đưa hậu a1 lên a8.", "7k/8/6K1/8/8/8/8/Q7 w - - 0 1", listOf("a1a8"), "Hậu chiếu dọc hàng 8."),
            LessonStep("Cẩn thận với hòa cờ (pat): nếu bên đi không còn nước hợp lệ mà vua không bị chiếu thì ván hòa. Khi đang hơn hãy luôn để đối phương còn nước đi, trừ khi bạn chiếu hết.", "k7/8/1K6/8/8/8/8/7Q w - - 0 1"),
            LessonStep("Tìm nước chiếu hết: vua đen ở a8, vua trắng ở b6 khóa các ô a7 và b7.", "k7/8/1K6/8/8/8/8/7Q w - - 0 1", listOf("h1h8"), "Hậu lên hàng 8 ở cuối cột h.", "Chiếu hết!"),
        ),
    ),
    Lesson(
        "fork", "Chiến thuật: đòn chĩa", "Một quân tấn công hai mục tiêu cùng lúc", "Trung bình",
        listOf(
            LessonStep("Đòn chĩa (fork) là khi một quân tấn công hai quân đối phương cùng lúc, đối phương chỉ cứu được một. Mã là bậc thầy của đòn này. Hãy đưa mã d5 lên c7: vừa chiếu vua vừa nhắm xe a8.", "r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1", listOf("d5c7"), "Mã c7 tấn công cả e8 và a8.", "Vua phải tránh, rồi mã ăn xe."),
            LessonStep("Tốt cũng chĩa được. Đưa tốt d3 lên d4 để cùng lúc tấn công mã c5 và xe e5.", "4k3/8/8/2n1r3/8/3P4/8/6K1 w - - 0 1", listOf("d3d4"), "Tốt d4 tấn công chéo hai bên.", "Một tốt đổi lấy một quân lớn."),
        ),
    ),
    Lesson(
        "pin", "Chiến thuật: ghim và xiên", "Quân bị kẹt không dám rời chỗ", "Trung bình",
        listOf(
            LessonStep("Ghim: một quân bị ghim vì nếu nó rời đi, quân phía sau (thường là vua) bị tấn công. Hãy ghim mã c6 vào vua đen bằng tượng: đưa tượng f1 lên b5.", "4k3/8/2n5/8/8/8/8/4KB2 w - - 0 1", listOf("f1b5"), "Đường chéo f1, e2, d3, c4, b5.", "Mã c6 không được di chuyển nữa."),
            LessonStep("Xiên: giống ghim nhưng quân đắt giá đứng trước. Hãy chiếu vua d8 bằng xe a8, vua buộc phải tránh và xe sẽ ăn hậu ở h8.", "3k3q/8/8/8/8/8/8/R3K3 w - - 0 1", listOf("a1a8"), "Xe lên a8 chiếu dọc hàng 8.", "Vua tránh ra, xe ăn hậu ở cuối hàng."),
        ),
    ),
    Lesson(
        "opening", "Nguyên tắc khai cuộc", "Ba việc cần làm ở những nước đầu", "Trung bình",
        listOf(
            LessonStep("Ba nguyên tắc khai cuộc: chiếm trung tâm bằng tốt, phát triển mã và tượng sớm, nhập thành để vua an toàn. Đừng đi một quân nhiều lần và đừng ra hậu quá sớm.", START),
            LessonStep("Chiếm trung tâm: đưa một tốt giữa lên hai ô. Hãy đi tốt e hoặc tốt d.", START, listOf("e2e4", "d2d4"), "e2-e4 hoặc d2-d4."),
            LessonStep("Phát triển quân: Trắng đã đi e4, Đen đáp e5. Hãy đưa mã ra ngoài (mã g1 hoặc b1) để cùng kiểm soát trung tâm.", "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", listOf("g1f3", "b1c3"), "Mã ra f3 hoặc c3."),
            LessonStep("Nhập thành: tượng và mã đã ra, giữa vua và xe đã trống. Hãy nhập thành cánh vua để đưa vua vào nơi an toàn.", "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("e1g1"), "Vua đi hai ô sang phải."),
        ),
    ),
)

fun lessonById(id: String): Lesson? = LESSONS.firstOrNull { it.id == id }
