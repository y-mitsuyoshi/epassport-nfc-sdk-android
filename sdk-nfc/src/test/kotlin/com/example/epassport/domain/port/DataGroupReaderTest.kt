package com.example.epassport.domain.port

import com.example.epassport.data.reader.IcaoDataGroupReader
import org.junit.Assert.assertNotNull
import org.junit.Test

class DataGroupReaderTest {

    @Test
    fun icaoDataGroupReader_implementsDataGroupReader() {
        // Verify IcaoDataGroupReader can be assigned to DataGroupReader interface
        val reader: DataGroupReader = IcaoDataGroupReader()
        assertNotNull(reader)
    }
}
