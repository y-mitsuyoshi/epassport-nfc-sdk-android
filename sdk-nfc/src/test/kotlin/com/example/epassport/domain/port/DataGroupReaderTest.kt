package com.example.epassport.domain.port

import com.example.epassport.data.reader.IcaoDataGroupReader
import org.junit.Assert.assertTrue
import org.junit.Test

class DataGroupReaderTest {

    @Test
    fun icaoDataGroupReader_implementsInterface() {
        val reader = IcaoDataGroupReader()
        assertTrue(reader is DataGroupReader)
    }
}
