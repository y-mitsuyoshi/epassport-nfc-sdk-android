package com.example.epassport.ocr

/**
 * 読み取った MRZ 原文から NFC 読み取りキー生成に必要なデータを抽出するパーサー。
 *
 * ICAO Doc 9303 の全フォーマット（TD1, TD2, TD3）を自動判定してパースします。
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
     * MRZテキストからフォーマットを自動判定してデータを抽出します。
     *
     * 対応フォーマット:
     * - TD1: 3行×30文字（身分証明書・在留カード等）
     * - TD2: 2行×36文字（公務旅券・一部の特殊旅券等）
     * - TD3: 2行×44文字（通常パスポート・査証A等）
     */
    fun parse(mrzRawText: String): ParsedMrz {
        val lines = mrzRawText.split("\n")
        require(lines.size >= 2) { "Invalid MRZ format: Must contain at least 2 lines" }

        return when {
            // TD1: 3行×30文字
            lines.size == 3 && lines.all { it.length == 30 } -> parseTd1(lines)
            // TD2: 2行×36文字
            lines.size == 2 && lines[0].length == 36 -> parseTd2(lines)
            // TD3 / MRV-A: 2行×44文字（デフォルト）
            else -> parseTd3(lines)
        }
    }

    /**
     * TD1 (3行×30文字) のパース。
     *
     * Line 1: Document code (1) + State (3) + Document Number (9) + Check digit (1) + Optional data
     *         標準: I<XXX... → DocNo at substring(5, 14)
     *         旧式: IXXX...  → DocNo at substring(4, 13)
     * Line 2: Optional (6) + DOB (6) + Check (1) + Sex (1) + DOE (6) + Check (1) + ...
     */
    private fun parseTd1(lines: List<String>): ParsedMrz {
        val line1 = lines[0]
        val line2 = lines[1]
        require(line1.length >= 13) { "Invalid TD1 Line 1 length: ${line1.length}" }
        require(line2.length >= 14) { "Invalid TD1 Line 2 length: ${line2.length}" }

        // 文書番号: 標準 I<XXX... は index 5 から、旧式 IXXX... は index 4 から
        val documentNumber = if (line1.length > 5 && line1[1] == '<') {
            line1.substring(5, 14).replace("<", "")
        } else {
            line1.substring(4, 13).replace("<", "")
        }

        // Line 2: DOB [0, 6), DOE [8, 14)
        val dateOfBirth = line2.substring(0, 6)
        val dateOfExpiry = line2.substring(8, 14)

        return ParsedMrz(documentNumber, dateOfBirth, dateOfExpiry)
    }

    /**
     * TD2 (2行×36文字) のパース。
     *
     * Line 2: DocNo [0, 9), DOB [13, 19), DOE [21, 27)
     */
    private fun parseTd2(lines: List<String>): ParsedMrz {
        val line2 = lines[1]
        require(line2.length >= 27) {
            "Invalid TD2 Line 2 length: expected >= 27 chars, got ${line2.length}"
        }

        val documentNumber = line2.substring(0, 9).replace("<", "")
        val dateOfBirth = line2.substring(13, 19)
        val dateOfExpiry = line2.substring(21, 27)

        return ParsedMrz(documentNumber, dateOfBirth, dateOfExpiry)
    }

    /**
     * TD3 / MRV-A (2行×44文字) のパース。
     *
     * Line 2: DocNo [0, 9), DOB [13, 19), DOE [28, 34)
     */
    private fun parseTd3(lines: List<String>): ParsedMrz {
        val line2 = lines[1]
        require(line2.length >= 27) {
            "Invalid TD3 Line 2 length: expected >= 27 chars, got ${line2.length}"
        }

        val documentNumber = line2.substring(0, 9).replace("<", "")
        val dateOfBirth = line2.substring(13, 19)
        val dateOfExpiry = line2.substring(21, 27)

        return ParsedMrz(documentNumber, dateOfBirth, dateOfExpiry)
    }
}
