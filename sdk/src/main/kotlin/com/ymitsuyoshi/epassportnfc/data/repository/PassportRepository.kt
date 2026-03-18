package com.ymitsuyoshi.epassportnfc.data.repository

import com.ymitsuyoshi.epassportnfc.core.EPassportException
import com.ymitsuyoshi.epassportnfc.data.auth.BacAuthenticator
import com.ymitsuyoshi.epassportnfc.data.nfc.NfcApduCommand
import com.ymitsuyoshi.epassportnfc.data.nfc.NfcApduResponse
import com.ymitsuyoshi.epassportnfc.domain.model.DataGroup
import com.ymitsuyoshi.epassportnfc.domain.model.PassportData
import com.ymitsuyoshi.epassportnfc.domain.model.PersonalData
import com.ymitsuyoshi.epassportnfc.domain.repository.BacKey
import com.ymitsuyoshi.epassportnfc.domain.repository.IPassportRepository
import java.text.SimpleDateFormat
import java.util.Locale

/** AID of the ePassport application (LDS, eMRTD) as defined in ICAO 9303 */
private val MRTD_APPLICATION_AID = byteArrayOf(
    0xA0.toByte(), 0x00, 0x00, 0x02, 0x47.toByte(), 0x10, 0x01
)

/** Elementary file identifiers for ICAO data groups */
private val DATA_GROUP_FIDS: Map<DataGroup, ByteArray> = mapOf(
    DataGroup.DG1 to byteArrayOf(0x01, 0x01),
    DataGroup.DG2 to byteArrayOf(0x01, 0x02),
    DataGroup.DG3 to byteArrayOf(0x01, 0x03),
    DataGroup.DG4 to byteArrayOf(0x01, 0x04),
    DataGroup.DG5 to byteArrayOf(0x01, 0x05),
    DataGroup.DG7 to byteArrayOf(0x01, 0x07),
    DataGroup.DG11 to byteArrayOf(0x01, 0x0B),
    DataGroup.DG12 to byteArrayOf(0x01, 0x0C),
    DataGroup.DG14 to byteArrayOf(0x01, 0x0E),
    DataGroup.DG15 to byteArrayOf(0x01, 0x0F),
    DataGroup.DG16 to byteArrayOf(0x01, 0x10),
)

/**
 * Concrete implementation of [IPassportRepository] that communicates with an ePassport chip
 * over NFC using ISO-DEP (ISO 14443-4) APDU commands.
 *
 * @param transceive Lambda wrapping [android.nfc.tech.IsoDep.transceive] that sends raw APDU
 *   bytes and returns the chip's response bytes.
 */
class PassportRepository(
    private val transceive: suspend (ByteArray) -> ByteArray,
) : IPassportRepository {

    private val bacAuthenticator = BacAuthenticator(transceive)
    private val dateFormat = SimpleDateFormat("yyMMdd", Locale.US)

    override suspend fun readPassport(bacKey: BacKey, dataGroups: Set<DataGroup>): PassportData {
        selectApplication()
        bacAuthenticator.authenticate(bacKey)

        var personalData: PersonalData? = null
        var faceImageBytes: ByteArray? = null
        val activeAuthSupported: Boolean

        if (DataGroup.DG1 in dataGroups) {
            personalData = parseDg1(readDataGroup(DataGroup.DG1))
        }
        if (DataGroup.DG2 in dataGroups) {
            faceImageBytes = extractFaceImage(readDataGroup(DataGroup.DG2))
        }
        activeAuthSupported = DataGroup.DG15 in dataGroups && tryReadDataGroup(DataGroup.DG15) != null

        return PassportData(
            personalData = personalData
                ?: throw EPassportException.DataGroupNotFoundException("DG1 (MRZ) was not read"),
            faceImageBytes = faceImageBytes,
            activeAuthSupported = activeAuthSupported,
        )
    }

    override suspend fun readPersonalData(bacKey: BacKey): PersonalData {
        selectApplication()
        bacAuthenticator.authenticate(bacKey)
        return parseDg1(readDataGroup(DataGroup.DG1))
    }

    override suspend fun isEPassport(): Boolean {
        return try {
            val response = NfcApduResponse.fromBytes(
                transceive(NfcApduCommand.selectApplication(MRTD_APPLICATION_AID).toBytes())
            )
            response.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun selectApplication() {
        val response = NfcApduResponse.fromBytes(
            transceive(NfcApduCommand.selectApplication(MRTD_APPLICATION_AID).toBytes())
        )
        response.requireSuccess("SELECT application")
    }

    private suspend fun readDataGroup(dataGroup: DataGroup): ByteArray {
        val fid = DATA_GROUP_FIDS[dataGroup]
            ?: throw EPassportException.UnsupportedFeatureException("No FID known for $dataGroup")

        val selectResponse = NfcApduResponse.fromBytes(
            transceive(NfcApduCommand.selectFile(fid).toBytes())
        )
        selectResponse.requireSuccess("SELECT $dataGroup")

        return readBinaryFile()
    }

    private suspend fun tryReadDataGroup(dataGroup: DataGroup): ByteArray? {
        return try {
            readDataGroup(dataGroup)
        } catch (_: EPassportException) {
            null
        }
    }

    private suspend fun readBinaryFile(): ByteArray {
        // Read first 4 bytes to determine the total length from the TLV header
        val header = NfcApduResponse.fromBytes(transceive(NfcApduCommand.readBinary(0, 4).toBytes()))
        header.requireSuccess("READ BINARY (header)")

        val totalLength = parseTlvLength(header.data)
        val result = ByteArray(totalLength)
        var offset = 0

        while (offset < totalLength) {
            val chunkSize = minOf(READ_CHUNK_SIZE, totalLength - offset)
            val chunk = NfcApduResponse.fromBytes(
                transceive(NfcApduCommand.readBinary(offset, chunkSize).toBytes())
            )
            chunk.requireSuccess("READ BINARY (offset=$offset)")
            chunk.data.copyInto(result, offset)
            offset += chunk.data.size
        }
        return result
    }

    private fun parseTlvLength(header: ByteArray): Int {
        if (header.size < 2) throw EPassportException.DataParsingException("TLV header too short")
        val first = header[1].toInt() and 0xFF
        return when {
            first <= 0x7F -> first + 2
            first == 0x81 && header.size >= 3 -> (header[2].toInt() and 0xFF) + 3
            first == 0x82 && header.size >= 4 ->
                ((header[2].toInt() and 0xFF) shl 8 or (header[3].toInt() and 0xFF)) + 4
            else -> throw EPassportException.DataParsingException("Unsupported TLV length encoding")
        }
    }

    private fun parseDg1(dg1Data: ByteArray): PersonalData {
        // The MRZ data is encoded as a TLV structure; the raw MRZ lines are in the value.
        // This is a simplified parser for the TD3 (passport) MRZ format.
        val mrzString = dg1Data.toString(Charsets.UTF_8).filter { it.isLetterOrDigit() || it == '<' }
        if (mrzString.length < 88) {
            throw EPassportException.DataParsingException(
                "DG1 MRZ data too short: ${mrzString.length} chars"
            )
        }

        val line1 = mrzString.substring(0, 44)
        val line2 = mrzString.substring(44, 88)

        val documentNumber = line2.substring(0, 9).trimEnd('<')
        val dateOfBirth = dateFormat.parse(line2.substring(13, 19))
            ?: throw EPassportException.DataParsingException("Cannot parse date of birth")
        val gender = line2[20]
        val dateOfExpiry = dateFormat.parse(line2.substring(21, 27))
            ?: throw EPassportException.DataParsingException("Cannot parse expiry date")
        val nationality = line2.substring(10, 13).trimEnd('<')

        val namePart = line1.substring(5, 44)
        val nameSections = namePart.split("<<")
        val surname = nameSections.getOrElse(0) { "" }.replace('<', ' ').trim()
        val givenNames = nameSections.getOrElse(1) { "" }.replace('<', ' ').trim()

        return PersonalData(
            surname = surname,
            givenNames = givenNames,
            documentNumber = documentNumber,
            nationality = nationality,
            dateOfBirth = dateOfBirth,
            gender = gender,
            dateOfExpiry = dateOfExpiry,
        )
    }

    private fun extractFaceImage(dg2Data: ByteArray): ByteArray {
        // DG2 contains a biometric data block; the JPEG-2000 image starts after the header.
        // This is a simplified extraction — full parsing requires ASN.1/BER-TLV traversal.
        val jp2Signature = byteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50.toByte())
        val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        val jp2Offset = dg2Data.indexOf(jp2Signature)
        if (jp2Offset >= 0) return dg2Data.copyOfRange(jp2Offset, dg2Data.size)

        val jpegOffset = dg2Data.indexOf(jpegSignature)
        if (jpegOffset >= 0) return dg2Data.copyOfRange(jpegOffset, dg2Data.size)

        throw EPassportException.DataParsingException("Could not locate face image in DG2 data")
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        outer@ for (i in 0..this.size - pattern.size) {
            for (j in pattern.indices) {
                if (this[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private companion object {
        const val READ_CHUNK_SIZE = 224
    }
}
