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
    class ParsedMrz(
        documentNumber: CharArray,
        dateOfBirth: CharArray,
        dateOfExpiry: CharArray
    ) {
        val documentNumber: CharArray = documentNumber.copyOf()
        val dateOfBirth: CharArray = dateOfBirth.copyOf()
        val dateOfExpiry: CharArray = dateOfExpiry.copyOf()

        fun clear() {
            documentNumber.fill('\u0000')
            dateOfBirth.fill('\u0000')
            dateOfExpiry.fill('\u0000')
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedMrz) return false
            return documentNumber.contentEquals(other.documentNumber) &&
                    dateOfBirth.contentEquals(other.dateOfBirth) &&
                    dateOfExpiry.contentEquals(other.dateOfExpiry)
        }

        override fun hashCode(): Int {
            var result = documentNumber.contentHashCode()
            result = 31 * result + dateOfBirth.contentHashCode()
            result = 31 * result + dateOfExpiry.contentHashCode()
            return result
        }

        override fun toString(): String {
            return "ParsedMrz(documentNumber=***, dateOfBirth=***, dateOfExpiry=***)"
        }
    }

    /**
     * MRZテキストからフォーマットを自動判定してデータを抽出します。
     *
     * 対応フォーマット:
     * - TD1: 3行×30文字（身分証明書・在留カード等）
     * - TD2: 2行×36文字（公務旅券・一部の特殊旅券等）
     * - TD3: 2行×44文字（通常パスポート・査証A等）
     */
    /**
     * MRZテキストからフォーマットを自動判定してデータを抽出します。
     *
     * 対応フォーマット:
     * - TD1: 3行×30文字（身分証明書・在留カード等）
     * - TD2: 2行×36文字（公務旅券・一部の特殊旅券等）
     * - TD3: 2行×44文字（通常パスポート・査証A等）
     */
    fun parse(mrzRawText: String): ParsedMrz {
        // 各行をトリム、空白除去、大文字化し、マークダウンのバックディックやノイズ行を除去
        val lines = mrzRawText.split("\n")
            .map { it.trim().replace(" ", "").uppercase() }
            .filter { line ->
                line.isNotEmpty() && !line.startsWith("`") && line.length >= 25
            }
            .map { line ->
                // MRZで使用可能な文字 (A-Z, 0-9, <) のみ抽出
                line.filter { it.isLetterOrDigit() || it == '<' }
            }

        require(lines.size >= 2) { 
            "Invalid MRZ format: Must contain at least 2 structured lines, got ${lines.size} lines from input" 
        }

        return when {
            // TD1: 3行×30文字
            lines.size >= 3 && lines.take(3).all { it.length in 28..32 } -> {
                parseTd1(lines.take(3).map { it.padEnd(30, '<').substring(0, 30) })
            }
            // TD2: 2行×36文字
            lines.size >= 2 && lines[0].length in 34..38 -> {
                parseTd2(lines.take(2).map { it.padEnd(36, '<').substring(0, 36) })
            }
            // TD3: 2行×44文字（デフォルト）
            else -> {
                val td3Lines = lines.take(2).map { it.padEnd(44, '<').substring(0, 44) }
                parseTd3(td3Lines)
            }
        }
    }

    private fun cleanNumericString(str: String): String {
        return str.map { char ->
            when (char) {
                'O', 'Q' -> '0'
                'I', 'L', 'J' -> '1'
                'Z' -> '2'
                'S' -> '5'
                'B' -> '8'
                'T' -> '7'
                'G' -> '6'
                else -> char
            }
        }.joinToString("")
    }

    /**
     * TD1 (3行×30文字) のパース。
     *
     * Line 1: Document code (2) + State (3) + Document Number (9) + Check digit (1) + Optional data (15)
     * Line 2: Optional data (6) + DOB (6) + Check (1) + Sex (1) + DOE (6) + Check (1) +
     *         Optional data (6) + Check (1) + Reserved (2)
     */
    private fun parseTd1(lines: List<String>): ParsedMrz {
        val line1 = lines[0]
        val line2 = lines[1]

        // 文書番号: Line 1 の index 5 から 14 文字目まで（9文字）
        val docNumRaw = line1.substring(5, 14).replace("<", "")
        // 日本の在留カード等は数字のみだが、一般旅券番号はアルファベットを含むため、適度な数字クリーンアップ（後半のみなど）にするか、
        // または生年月日・有効期限のように完全な数字のみの部分だけクリーンにする
        val documentNumber = docNumRaw.toCharArray()

        // Line 2: DOB [6, 12), DOE [14, 20)
        val dobStr = cleanNumericString(line2.substring(6, 12))
        val doeStr = cleanNumericString(line2.substring(14, 20))
        val dateOfBirth = dobStr.toCharArray()
        val dateOfExpiry = doeStr.toCharArray()

        val parsed = ParsedMrz(documentNumber, dateOfBirth, dateOfExpiry)
        documentNumber.fill('\u0000')
        dateOfBirth.fill('\u0000')
        dateOfExpiry.fill('\u0000')
        return parsed
    }

    /**
     * TD2 (2行×36文字) のパース。
     *
     * Line 2: DocNo [0, 9), DOB [13, 19), DOE [21, 27)
     */
    private fun parseTd2(lines: List<String>): ParsedMrz {
        val line2 = lines[1]

        val docNumRaw = line2.substring(0, 9).replace("<", "")
        val documentNumber = docNumRaw.toCharArray()
        val dobStr = cleanNumericString(line2.substring(13, 19))
        val doeStr = cleanNumericString(line2.substring(21, 27))
        val dateOfBirth = dobStr.toCharArray()
        val dateOfExpiry = doeStr.toCharArray()

        val parsed = ParsedMrz(documentNumber, dateOfBirth, dateOfExpiry)
        documentNumber.fill('\u0000')
        dateOfBirth.fill('\u0000')
        dateOfExpiry.fill('\u0000')
        return parsed
    }

    /**
     * TD3 / MRV-A (2行×44文字) のパース。
     *
     * Line 2: DocNo [0, 9), DOB [13, 19), DOE [21, 27)
     */
    private fun parseTd3(lines: List<String>): ParsedMrz {
        val line2 = lines[1]

        val docNumRaw = line2.substring(0, 9).replace("<", "")
        val documentNumber = docNumRaw.toCharArray()
        val dobStr = cleanNumericString(line2.substring(13, 19))
        val doeStr = cleanNumericString(line2.substring(21, 27))
        val dateOfBirth = dobStr.toCharArray()
        val dateOfExpiry = doeStr.toCharArray()

        val parsed = ParsedMrz(documentNumber, dateOfBirth, dateOfExpiry)
        documentNumber.fill('\u0000')
        dateOfBirth.fill('\u0000')
        dateOfExpiry.fill('\u0000')
        return parsed
    }
}
