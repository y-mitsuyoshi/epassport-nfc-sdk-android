package com.example.epassport.ocr

/**
 * OCRテキスト行からICAO機械読み取り旅行文書（MRZ）を抽出する純粋関数。
 *
 * 対応フォーマット:
 * - TD3 Passport: 2行×44文字（海外パスポート含む）
 * - MRV-A Visa:   2行×44文字
 *
 * 主な機能:
 * - 2行連続で44文字の[A-Z0-9<]パターンにマッチする行を検索
 * - ICAO Doc 9303 で定義される各種文書コードを認識
 * - 1行目の先頭文字誤認識（P<, R<, F<, P）を自動補正
 * - 正規化後も厳密に44文字であることを保証
 */
object MrzExtractor {

    /**
     * ICAO Doc 9303 で定義される文書コード。
     * P=Passport, V=Visa, I=Identity/Resident, C=Collective, A=Admittance
     */
    private val ICAO_DOC_CODES = setOf('P', 'V', 'I', 'C', 'A')

    private val MRZ_LINE_PATTERN = Regex("^[A-Z0-9<]{44}$")

    /**
     * 正規化済みのテキスト行リストからMRZを抽出する。
     *
     * @param lines スペース除去・大文字変換済みの各行テキスト
     * @return "LINE1\nLINE2" 形式のMRZ文字列、または抽出失敗時は null
     */
    fun extractFromLines(lines: List<String>): String? {
        for (i in 0 until lines.size - 1) {
            val line1 = lines[i]
            val line2 = lines[i + 1]

            // 2行連続で44文字のMRZパターンに合致するかチェック
            if (!MRZ_LINE_PATTERN.matches(line1) || !MRZ_LINE_PATTERN.matches(line2)) {
                continue
            }

            // 文書コードがICAO規格に合致するか確認（OCR誤認識も許容・補正）
            if (isValidDocumentPrefix(line1)) {
                val normalizedLine1 = normalizeLine1(line1)
                if (normalizedLine1 != null && normalizedLine1.length == 44) {
                    return "$normalizedLine1\n$line2"
                }
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
        // OCR 誤認識パターンの許容
        if (firstChar == 'R' || firstChar == 'F') return true
        return false
    }

    /**
     * MRZ 1行目の先頭文字をICAO標準に正規化する。
     *
     * 対応するOCR誤認識パターン:
     * - P<...  → そのまま
     * - R<...  → P<... (RをPに置き換え、長さ維持)
     * - F<...  → P<... (FをPに置き換え、長さ維持)
     * - P...   → P<... (<が欠落した場合、2文字目に<を挿入し44文字に調整)
     * - V<, I<, C<, A< → そのまま（他の文書タイプはコード置換しない）
     */
    internal fun normalizeLine1(line: String): String? {
        return when {
            line.startsWith("P<") -> line
            line.startsWith("R<") || line.startsWith("F<") -> {
                // Passport の OCR誤認識のみ P に置き換え、長さを44文字に厳密に維持
                "P" + line.substring(1)
            }
            line.startsWith("P") -> {
                // P で始まるが P< でない場合: '<' がOCRで認識されていない可能性
                // 2文字目に '<' を挿入し、末尾をトリムして44文字に調整
                if (line.length >= 2) {
                    "P<" + line.substring(1).take(42)
                } else {
                    null
                }
            }
            // その他のICA文書コード（V, I, C, A）は正規化不要
            line.isNotEmpty() && line[0] in ICAO_DOC_CODES -> line
            else -> null
        }
    }
}
