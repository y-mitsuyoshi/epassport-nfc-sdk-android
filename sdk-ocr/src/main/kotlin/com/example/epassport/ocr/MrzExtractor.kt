package com.example.epassport.ocr

/**
 * OCRテキスト行からICAO機械読み取り旅行文書（MRZ）を抽出する純粋関数。
 *
 * 対応フォーマット（ICAO Doc 9303）:
 * - TD3 Passport / MRV-A Visa: 2行×44文字
 * - TD2 / MRV-B:               2行×36文字
 * - TD1 Identity/Resident:     3行×30文字
 *
 * 主な機能:
 * - 各行数×文字数パターンを自動検出
 * - ICAO Doc 9303 で定義される各種文書コードを認識
 * - 1行目の先頭文字誤認識（P<, R<, F<, P）を自動補正
 * - 正規化後も厳密に規定文字数であることを保証
 */
object MrzExtractor {

    /**
     * ICAO Doc 9303 で定義される文書コード。
     * P=Passport, V=Visa, I=Identity/Resident, C=Collective, A=Admittance, D=Diplomatic
     */
    private val ICAO_DOC_CODES = setOf('P', 'V', 'I', 'C', 'A', 'D')

    private val MRZ_44_PATTERN = Regex("^[A-Z0-9<]{44}$")
    private val MRZ_36_PATTERN = Regex("^[A-Z0-9<]{36}$")
    private val MRZ_30_PATTERN = Regex("^[A-Z0-9<]{30}$")

    /**
     * 正規化済みのテキスト行リストからMRZを抽出する。
     *
     * 優先順位: TD3/MRV-A (44×2) → TD2/MRV-B (36×2) → TD1 (30×3)
     *
     * @param lines スペース除去・大文字変換済みの各行テキスト
     * @return MRZ文字列（改行区切り）、または抽出失敗時は null
     */
    fun extractFromLines(lines: List<String>): String? {
        // 1. TD3 Passport / MRV-A Visa: 2行×44文字（最も一般的）
        extractTwoLineMrz(lines, 44, MRZ_44_PATTERN)?.let { return it }
        // 2. TD2 / MRV-B: 2行×36文字
        extractTwoLineMrz(lines, 36, MRZ_36_PATTERN)?.let { return it }
        // 3. TD1 Identity/Resident: 3行×30文字
        extractThreeLineMrz(lines)?.let { return it }
        return null
    }

    private fun extractTwoLineMrz(
        lines: List<String>,
        expectedLength: Int,
        pattern: Regex
    ): String? {
        for (i in 0 until lines.size - 1) {
            val line1 = lines[i]
            val line2 = lines[i + 1]
            if (!pattern.matches(line1) || !pattern.matches(line2)) continue
            if (!isValidDocumentPrefix(line1)) continue
            val normalized = normalizeLine1(line1, expectedLength)
            if (normalized != null && normalized.length == expectedLength) {
                return "$normalized\n$line2"
            }
        }
        return null
    }

    private fun extractThreeLineMrz(lines: List<String>): String? {
        for (i in 0 until lines.size - 2) {
            val line1 = lines[i]
            val line2 = lines[i + 1]
            val line3 = lines[i + 2]
            if (!MRZ_30_PATTERN.matches(line1) ||
                !MRZ_30_PATTERN.matches(line2) ||
                !MRZ_30_PATTERN.matches(line3)
            ) {
                continue
            }
            if (!isValidDocumentPrefix(line1)) continue
            val normalized = normalizeLine1(line1, 30)
            if (normalized != null && normalized.length == 30) {
                return "$normalized\n$line2\n$line3"
            }
        }
        return null
    }

    /**
     * 1行目の先頭がICAO文書コードで始まっているかチェックする。
     * Passport の OCR誤認識として R<, F< も許容する。
     */
    private fun isValidDocumentPrefix(line: String): Boolean {
        if (line.isEmpty()) return false
        val firstChar = line[0]
        if (firstChar in ICAO_DOC_CODES) return true
        // OCR 誤認識パターンの許容（Passport の P が R や F に誤認識されるケース）
        if (firstChar == 'R' || firstChar == 'F') return true
        return false
    }

    /**
     * MRZ 1行目の先頭文字をICAO標準に正規化する。
     *
     * @param line MRZの1行目
     * @param expectedLength 期待される行の長さ（44, 36, または 30）
     *
     * 対応するOCR誤認識パターン:
     * - P<...  → そのまま
     * - R<...  → P<... (RをPに置き換え、長さ維持)
     * - F<...  → P<... (FをPに置き換え、長さ維持)
     * - P...   → P<... (<が欠落した場合、2文字目に<を挿入し末尾をトリム)
     * - その他の文書コード（V, I, C, A, D）はそのまま
     */
    internal fun normalizeLine1(line: String, expectedLength: Int = 44): String? {
        return when {
            line.startsWith("P<") -> line
            line.startsWith("R<") || line.startsWith("F<") -> {
                // Passport の OCR誤認識のみ P に置き換え
                "P" + line.substring(1)
            }
            line.startsWith("P") -> {
                // P で始まるが P< でない場合: '<' がOCRで認識されていない可能性
                if (line.length >= 2) {
                    "P<" + line.substring(1).take(expectedLength - 2)
                } else {
                    null
                }
            }
            // その他のICA文書コードは正規化不要
            line.isNotEmpty() && line[0] in ICAO_DOC_CODES -> line
            else -> null
        }
    }
}
