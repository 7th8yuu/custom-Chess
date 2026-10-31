package com.customchess.color

/**
 * Màu hạt giống (seed) cho bảng màu Material 3 của từng bàn cờ.
 * Bốn màu ảnh được chọn bằng [Dominant.seed] trên chính bức ảnh (có test kiểm tra), riêng FIRE được nâng độ rực
 * lên một chút vì màu trung bình của bầu trời hoàng hôn bị pha xám.
 */
object PhotoSeeds {
    const val LACQUER = 0xFFD9AE4B.toInt()
    const val SLATE = 0xFF4D6A82.toInt()
    const val GRAPHITE = 0xFF8C857C.toInt()

    const val SKY = 0xFF6CC4F4.toInt()
    const val DUSK = 0xFF3484CC.toInt()
    const val FIRE = 0xFFE7785F.toInt()
    const val ROSE = 0xFFCC749C.toInt()
}
