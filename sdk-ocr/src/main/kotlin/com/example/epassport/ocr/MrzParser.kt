package com.example.epassport.ocr

/**
 * 読み取った MRZ 原文から NFC 読み取りキー生成に必要なデータを抽出するパーサー。
 */
object MrzParser {

    /**
     * パース後の MRZ 抽出結果データモデル。
     */
    data class ParsedMrz(
        val documentNumber: String, // 旅券番号
        val dateOfBirth: String,    // 生年月日 (YYMMDD)
        val dateOfExpiry: String    // 有効期限 (YYMMDD)
    )

    /**
     * パスポートMRZ（2行、各44文字）からデータを抽出します。
     *
     * ICAO Doc 9303 Part 4 (TD3 Passport) Line 2 のフィールドレイアウト（1-based）:
     * | Pos(1-based) | Kotlin substring (0-based) | Field                     |
     * |-------------|---------------------------|---------------------------|
     * | 1–9         | [0, 9)                    | Document Number           |
     * | 10          | [9, 10)                   | Check digit (doc number)  |
     * | 11–13       | [10, 13)                  | Nationality               |
     * | 14–19       | [13, 19)                  | Date of Birth (YYMMDD)    |
     * | 20          | [19, 20)                  | Check digit (DOB)         |
     * | 21–27       | [20, 27)                  | Optional data             |
     * | 28          | [27, 28)                  | Check digit (optional)    |
     * | 29–34       | [28, 34)                  | Date of Expiry (YYMMDD)   | ← CORRECT
     * | 35          | [34, 35)                  | Check digit (expiry)      |
     * | 36–42       | [35, 42)                  | Optional data 2           |
     * | 43          | [42, 43)                  | Composite check digit     |
     */
    fun parse(mrzRawText: String): ParsedMrz {
        val lines = mrzRawText.split("\n")
        require(lines.size >= 2) { "Invalid MRZ format: Must contain at least 2 lines" }

        val line2 = lines[1]
        require(line2.length >= 34) {
            "Invalid MRZ Line 2 length: expected >= 34 chars, got ${line2.length}"
        }

        val documentNumber = line2.substring(0, 9).replace("<", "")
        val dateOfBirth    = line2.substring(13, 19)  // pos 14–19 (1-based)
        val dateOfExpiry   = line2.substring(28, 34)  // pos 29–34 (1-based) ← ICAO correct

        return ParsedMrz(
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            dateOfExpiry = dateOfExpiry
        )
    }
}
