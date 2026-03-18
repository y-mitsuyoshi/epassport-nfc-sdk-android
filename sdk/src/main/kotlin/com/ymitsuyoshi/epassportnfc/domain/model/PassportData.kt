package com.ymitsuyoshi.epassportnfc.domain.model

/**
 * Enumeration of ICAO 9303 data groups that can be present on an ePassport chip.
 *
 * Each entry lists the data group number and its canonical description.
 */
enum class DataGroup(val number: Int, val description: String) {
    DG1(1, "Machine Readable Zone"),
    DG2(2, "Encoded Face"),
    DG3(3, "Encoded Finger(s)"),
    DG4(4, "Encoded Eye(s)"),
    DG5(5, "Displayed Portrait"),
    DG7(7, "Displayed Signature or Usual Mark"),
    DG11(11, "Additional Personal Details"),
    DG12(12, "Additional Document Details"),
    DG14(14, "Security Options"),
    DG15(15, "Active Authentication Public Key Info"),
    DG16(16, "Person(s) to Notify");

    companion object {
        /** Returns the [DataGroup] for the given number, or null if unknown. */
        fun fromNumber(number: Int): DataGroup? = entries.firstOrNull { it.number == number }
    }
}

/**
 * Aggregates all data read from an ePassport chip.
 *
 * @property personalData Personal data parsed from DG1 (MRZ).
 * @property faceImageBytes Raw JPEG-2000 encoded face image from DG2, or null if not read.
 * @property dataGroupHashes Map of data-group numbers to their stored hash values
 *   (from the Document Security Object, SOD), used for passive authentication.
 * @property activeAuthSupported True when DG15 is present indicating Active Authentication support.
 */
data class PassportData(
    val personalData: PersonalData,
    val faceImageBytes: ByteArray? = null,
    val dataGroupHashes: Map<Int, ByteArray> = emptyMap(),
    val activeAuthSupported: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PassportData) return false
        return personalData == other.personalData &&
            nullableBytesEqual(faceImageBytes, other.faceImageBytes) &&
            activeAuthSupported == other.activeAuthSupported
    }

    override fun hashCode(): Int {
        var result = personalData.hashCode()
        result = 31 * result + (faceImageBytes?.contentHashCode() ?: 0)
        result = 31 * result + activeAuthSupported.hashCode()
        return result
    }

    private companion object {
        fun nullableBytesEqual(a: ByteArray?, b: ByteArray?): Boolean =
            if (a == null) b == null else b != null && a.contentEquals(b)
    }
}
