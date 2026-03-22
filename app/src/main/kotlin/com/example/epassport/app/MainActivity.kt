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
    private lateinit var docNoInput: EditText
    private lateinit var dobInput: EditText
    private lateinit var doeInput: EditText
    private lateinit var scanButton: Button
    private var isReadyToScan = false

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
        
        docNoInput = EditText(this).apply {
            hint = "旅券番号 (Passport No. 例:TK1234567)"
        }
        dobInput = EditText(this).apply {
            hint = "生年月日 (Date of Birth 例:900305)"
        }
        doeInput = EditText(this).apply {
            hint = "有効期限 (Date of Expiry 例:321120)"
        }

        scanButton = Button(this).apply {
            text = "NFC読み取りを開始する"
            setPadding(0, 32, 0, 32)
            setOnClickListener {
                if (docNoInput.text.isBlank() || dobInput.text.isBlank() || doeInput.text.isBlank()) {
                    statusTextView.text = "エラー：MRZ情報をすべて入力してください"
                    statusTextView.setTextColor(Color.RED)
                    return@setOnClickListener
                }
                isReadyToScan = true
                statusTextView.text = "【スキャン待機中】\nスマホの裏側上部にパスポートを数秒間ぴったり当ててください..."
                statusTextView.setTextColor(Color.BLUE)
            }
        }

        statusTextView = TextView(this).apply {
            text = "上記全てを入力後、「読み取り開始」ボタンを押してください"
            setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 32)
        }

        layout.addView(title)
        layout.addView(docNoInput)
        layout.addView(dobInput)
        layout.addView(doeInput)
        layout.addView(scanButton)
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
            if (!isReadyToScan) {
                statusTextView.text = "先に「NFC読み取りを開始する」ボタンを押してください"
                statusTextView.setTextColor(Color.RED)
                return
            }
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                statusTextView.text = "NFC反応あり。ICチップへ通信を開始します..."
                statusTextView.setTextColor(Color.BLACK)
                processTag(tag)
            }
        }
    }

    private fun processTag(tag: Tag) {
        val isoDep = IsoDep.get(tag) ?: run {
            statusTextView.text = "エラー: このNFCタグはパスポート(ISO14443-4)ではありません"
            statusTextView.setTextColor(Color.RED)
            isReadyToScan = false
            return
        }

        // Parse user MRZ input
        val docNo = docNoInput.text.toString().trim()
        val dob = dobInput.text.toString().trim()
        val doe = doeInput.text.toString().trim()
        val mrzData = MrzData(docNo, dob, doe)

        // Use our NFC Transceiver wrapper
        val transceiver = AndroidNfcTransceiver(isoDep)

        // Use the newly created stub AppDataGroupReader
        val authenticator = BacAuthenticator()
        val reader = AppDataGroupReader()
        val useCase = ReadPassportUseCase(authenticator, reader)

        statusTextView.text = "NFC Tag connected. BAC認証（鍵の生成と共有）を実行中..."
        statusTextView.setTextColor(Color.BLUE)

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
                statusTextView.text = "✅ 読み取り成功！ ICチップからデータを取得しました。\nMRZ: $mrzText\nImage Type: $imgType"
                statusTextView.setTextColor(Color.GREEN)
                isReadyToScan = false // Reset
            } catch (e: Exception) {
                statusTextView.text = "❌ エラー発生: ${e.message}\n(途中でパスポートが離れたか、入力した文字が間違っている可能性があります)"
                statusTextView.setTextColor(Color.RED)
                e.printStackTrace()
                isReadyToScan = false // Reset
            }
        }
    }
}
