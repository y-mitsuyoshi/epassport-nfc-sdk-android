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
     * ICAO Doc 9303 規格に準拠:
     * - 2行目の 1-9文字目: 旅券番号 (Document Number)
     * - 2行目の 14-19文字目: 生年月日 (Date of Birth: YYMMDD)
     * - 2行目の 22-27文字目: 有効期限 (Date of Expiry: YYMMDD)
     */
    fun parse(mrzRawText: String): ParsedMrz {
        val lines = mrzRawText.split("\n")
        require(lines.size >= 2) { "Invalid MRZ format: Must contain at least 2 lines" }
        
        val line2 = lines[1]
        
        val documentNumber = line2.substring(0, 9).replace("<", "")
        val dateOfBirth = line2.substring(13, 19)
        val dateOfExpiry = line2.substring(21, 27)

        return ParsedMrz(
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            dateOfExpiry = dateOfExpiry
        )
    }
}
