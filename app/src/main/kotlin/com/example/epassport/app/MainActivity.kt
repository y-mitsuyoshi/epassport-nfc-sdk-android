package com.example.epassport.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.epassport.data.auth.BacAuthenticator
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.port.DataGroupReader
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.usecase.ReadPassportUseCase
import com.example.epassport.usecase.ReadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.LinearLayout
import android.view.ViewGroup
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusTextView: TextView
    private lateinit var mrzInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple programmatic UI for testing without XML layout files
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "ePassport NFC Reader"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        
        mrzInput = EditText(this).apply {
            hint = "Format: PassportNo,YYMMDD,YYMMDD"
            // Default dummy (for example only, please replace with real MRZ pieces):
            // L898902C<, 690806, 940623
            setText("L898902C<,690806,940623")
        }

        val guideText = TextView(this).apply {
            text = "1. Edit MRZ (DocNo, Birth, Expiry)\n2. Tap Passport on NFC sensor."
            setPadding(0, 32, 0, 32)
        }

        statusTextView = TextView(this).apply {
            text = "Waiting for NFC tag..."
            setTextColor(Color.GRAY)
        }

        layout.addView(title)
        layout.addView(mrzInput)
        layout.addView(guideText)
        layout.addView(statusTextView)

        setContentView(layout)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                statusTextView.text = "NFC Tag Detected. Starting read..."
                processTag(tag)
            }
        }
    }

    private fun processTag(tag: Tag) {
        val isoDep = IsoDep.get(tag) ?: run {
            statusTextView.text = "Tag does not support IsoDep (ISO 14443-4)"
            return
        }

        // Parse user MRZ input
        val mrzParts = mrzInput.text.toString().split(",")
        if (mrzParts.size != 3) {
            statusTextView.text = "Error: Invalid MRZ format in input."
            return
        }
        val mrzData = MrzData(mrzParts[0].trim(), mrzParts[1].trim(), mrzParts[2].trim())

        // Use our NFC Transceiver wrapper
        val transceiver = AndroidNfcTransceiver(isoDep)

        // Use the newly created stub AppDataGroupReader
        val authenticator = BacAuthenticator()
        val reader = AppDataGroupReader()
        val useCase = ReadPassportUseCase(authenticator, reader)

        statusTextView.text = "NFC Tag connected. Authenticating via BAC..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Execute on IO thread background inside UseCase
                val passportData = withContext(Dispatchers.IO) {
                    useCase.execute(transceiver, mrzData) { progress ->
                        // Dispatch back to main thread for UI updates
                        launch(Dispatchers.Main) {
                            statusTextView.text = "Progress: $progress"
                        }
                    }
                }
                val mrzText = passportData.dg1.documentNumber
                val imgType = passportData.dg2?.mimeType ?: "No Image"
                statusTextView.text = "Success! Read DGs.\nMRZ: $mrzText\nImage Type: $imgType"
            } catch (e: Exception) {
                statusTextView.text = "Error: ${e.message}"
                e.printStackTrace()
            }
        }
    }
}
