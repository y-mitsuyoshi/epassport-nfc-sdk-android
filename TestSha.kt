import java.security.MessageDigest

fun main() {
    val mrzData = MrzData("L898902C<", "690806", "940623")
    val kSeed = mrzData.deriveBacKeySeed()
    println("kSeed: " + kSeed.joinToString("") { "%02X".format(it) })
}

data class MrzData(val documentNumber: String, val dateOfBirth: String, val dateOfExpiry: String) {
    fun deriveBacKeySeed(): ByteArray {
        val docNum = padString(documentNumber, 9)
        val docNumCheckDigit = computeCheckDigit(docNum)

        val dob = padString(dateOfBirth, 6)
        val dobCheckDigit = computeCheckDigit(dob)

        val doe = padString(dateOfExpiry, 6)
        val doeCheckDigit = computeCheckDigit(doe)

        val mrzInformation = "$docNum$docNumCheckDigit$dob$dobCheckDigit$doe$doeCheckDigit"
        println("mrzInformation: $mrzInformation")

        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(mrzInformation.toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        
        return hash.sliceArray(0..15)
    }

    fun computeCheckDigit(input: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        for (i in input.indices) {
            val char = input[i]
            val value = when {
                char in '0'..'9' -> char - '0'
                char in 'A'..'Z' -> char - 'A' + 10
                char == '<' -> 0
                else -> throw IllegalArgumentException("Invalid MRZ character: $char")
            }
            sum += value * weights[i % 3]
        }
        return sum % 10
    }

    private fun padString(input: String, length: Int): String {
        return input.padEnd(length, '<').substring(0, length)
    }
}
