package com.example.epassport.app

import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data
import com.example.epassport.domain.port.DataGroupReader
import com.example.epassport.domain.port.NfcTransceiver

class AppDataGroupReader : DataGroupReader {
    override suspend fun readDg1(transceiver: NfcTransceiver): Dg1Data {
        // FIXME: Read EF.DG1 raw bytes using Apdu commands and parse it!
        // This is just a stub returning dummy data.
        return Dg1Data(byteArrayOf(), "DUMMY_MRZ_DATA_READ")
    }

    override suspend fun readDg2(transceiver: NfcTransceiver): Dg2Data {
        // FIXME: Read EF.DG2 raw bytes using Apdu commands and parse it!
        return Dg2Data(byteArrayOf(), "image/jpeg")
    }
}
