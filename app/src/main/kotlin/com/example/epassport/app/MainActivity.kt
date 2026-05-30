package com.example.epassport.app

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import com.example.epassport.api.EPassportReader
import com.example.epassport.api.ReadResult
import com.example.epassport.domain.model.MrzData
import com.example.epassport.usecase.ReadProgress
import com.example.epassport.ocr.CameraMrzScanner
import com.example.epassport.ocr.MrzParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusTextView: TextView
    private lateinit var docNoInput: EditText
    private lateinit var dobInput: EditText
    private lateinit var doeInput: EditText
    private lateinit var scanButton: Button
    
    // Mode Switch UI
    private enum class InputMode { MANUAL, CAMERA }
    private var currentMode = InputMode.MANUAL
    private lateinit var inputCard: LinearLayout
    private lateinit var cameraCard: LinearLayout
    private lateinit var previewView: PreviewView
    private lateinit var manualModeBtn: Button
    private lateinit var cameraModeBtn: Button
    
    // Result UI components
    private lateinit var resultCard: LinearLayout
    private lateinit var faceImageView: ImageView
    private lateinit var detailsLayout: LinearLayout

    private var isReadyToScan = false
    private var scannedMrzData: MrzData? = null
    private var mrzScanner: CameraMrzScanner? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        mrzScanner = CameraMrzScanner(this, this)

        // Set elegant dark charcoal background
        val rootScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A")) // Slate 900
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. Premium Header Title
        val titleView = TextView(this).apply {
            text = "ePassport Reader"
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC")) // Slate 50
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val subtitleView = TextView(this).apply {
            text = "NFC Secured Chip Scanner"
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }

        // 2. Mode Selector Tabs
        val modeSelectorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 24f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 36)
            }
        }

        manualModeBtn = Button(this).apply {
            text = "手動入力"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchMode(InputMode.MANUAL) }
        }

        cameraModeBtn = Button(this).apply {
            text = "カメラOCR"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchMode(InputMode.CAMERA) }
        }

        modeSelectorLayout.addView(manualModeBtn)
        modeSelectorLayout.addView(cameraModeBtn)

        // 3. Input Fields Card (Manual Mode UI)
        inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 32f
                setStroke(1, Color.parseColor("#334155")) // Slate 700
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 36)
            }
        }

        docNoInput = createStyledEditText("旅券番号 (例: TR6930600)")
        dobInput = createStyledEditText("生年月日 6桁 (例: 901008)")
        doeInput = createStyledEditText("有効期限 6桁 (例: 261017)")

        inputCard.addView(createLabel("Passport No. / 旅券番号"))
        inputCard.addView(docNoInput)
        inputCard.addView(createLabel("Date of Birth / 生年月日 (YYMMDD)"))
        inputCard.addView(dobInput)
        inputCard.addView(createLabel("Date of Expiry / 有効期限 (YYMMDD)"))
        inputCard.addView(doeInput)

        // 4. Camera Preview Card (Camera OCR Mode UI)
        cameraCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 32f
                setStroke(1, Color.parseColor("#334155")) // Slate 700
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                560 // Fixed height for elegant alignment
            ).apply {
                setMargins(0, 0, 0, 36)
            }
        }

        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            // Round corners for premium feel
            background = GradientDrawable().apply {
                cornerRadius = 24f
            }
            clipToOutline = true
        }
        cameraCard.addView(previewView)

        // 5. Scan Trigger / Manual Confirmation Button
        scanButton = Button(this).apply {
            text = "NFC 読み取りを開始する"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB")) // Royal Blue
                cornerRadius = 24f
            }
            setPadding(0, 36, 0, 36)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 36)
            }
            setOnClickListener {
                if (currentMode == InputMode.MANUAL) {
                    val docNo = docNoInput.text.toString().trim()
                    val dob = dobInput.text.toString().trim()
                    val doe = doeInput.text.toString().trim()
                    if (docNo.isBlank() || dob.isBlank() || doe.isBlank()) {
                        showStatus("エラー：MRZ情報を入力してください", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                        return@setOnClickListener
                    }
                    scannedMrzData = MrzData(docNo, dob, doe)
                    triggerScanReady()
                } else {
                    // In camera mode, scanButton acts as a restart button for camera if needed
                    startCameraOcrScan()
                }
            }
        }

        // 6. Status Indicator Box
        statusTextView = TextView(this).apply {
            text = "上記3つの項目を入力後、読み取りを開始してください"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 48)
            }
        }

        // 7. Result Container Card (Hidden by default)
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(40, 48, 40, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 36f
                setStroke(2, Color.parseColor("#10B981")) // Emerald 500
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val resultTitle = TextView(this).apply {
            text = "IC CHIP DATA RETRIEVED"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10B981")) // Emerald 500
            setPadding(0, 0, 0, 24)
        }

        faceImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(240, 320).apply {
                setMargins(0, 0, 0, 36)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155")) // Slate 700
                cornerRadius = 24f
            }
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        detailsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        resultCard.addView(resultTitle)
        resultCard.addView(faceImageView)
        resultCard.addView(detailsLayout)

        // Assemble all layouts
        mainLayout.addView(titleView)
        mainLayout.addView(subtitleView)
        mainLayout.addView(modeSelectorLayout)
        mainLayout.addView(inputCard)
        mainLayout.addView(cameraCard)
        mainLayout.addView(scanButton)
        mainLayout.addView(statusTextView)
        mainLayout.addView(resultCard)

        rootScrollView.addView(mainLayout)
        setContentView(rootScrollView)

        // Initialize state to Manual
        switchMode(InputMode.MANUAL)
    }

    private fun switchMode(mode: InputMode) {
        currentMode = mode
        resultCard.visibility = View.GONE
        isReadyToScan = false

        if (mode == InputMode.MANUAL) {
            mrzScanner?.stopScan()
            inputCard.visibility = View.VISIBLE
            cameraCard.visibility = View.GONE
            
            // Styled buttons
            manualModeBtn.setTextColor(Color.WHITE)
            manualModeBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB")) // Active Royal Blue
                cornerRadius = 20f
            }
            cameraModeBtn.setTextColor(Color.parseColor("#94A3B8")) // Inactive Slate 400
            cameraModeBtn.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            
            scanButton.text = "NFC 読み取りを開始する"
            scanButton.visibility = View.VISIBLE
            showStatus("MRZ情報を手動入力し、読み取りボタンを押してください", Color.parseColor("#94A3B8"), Color.parseColor("#1E293B"))
        } else {
            inputCard.visibility = View.GONE
            cameraCard.visibility = View.VISIBLE
            
            // Styled buttons
            cameraModeBtn.setTextColor(Color.WHITE)
            cameraModeBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB")) // Active Royal Blue
                cornerRadius = 20f
            }
            manualModeBtn.setTextColor(Color.parseColor("#94A3B8")) // Inactive Slate 400
            manualModeBtn.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            
            scanButton.text = "カメラを再起動する"
            scanButton.visibility = View.GONE
            showStatus("カメラ起動中... パスポートのMRZ部分（最下部2行）を映してください", Color.parseColor("#EAB308"), Color.parseColor("#FEF9C3"))
            
            // Request camera permission
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCameraOcrScan()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun startCameraOcrScan() {
        showStatus("パスポートのMRZ（最下部の2行）をカメラ枠内に合わせてください...", Color.parseColor("#EAB308"), Color.parseColor("#FEF9C3"))
        mrzScanner?.startScan(
            cameraPreviewView = previewView,
            onSuccess = { mrzText ->
                runOnUiThread {
                    try {
                        val parsed = MrzParser.parse(mrzText)
                        scannedMrzData = MrzData(parsed.documentNumber, parsed.dateOfBirth, parsed.dateOfExpiry)
                        
                        // Automatically transit to NFC scan ready
                        triggerScanReady()
                    } catch (e: Exception) {
                        showStatus("OCR読み取りエラー: 解析に失敗しました。再試行中...", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                        // Restart camera on failure
                        startCameraOcrScan()
                    }
                }
            },
            onFailure = { e ->
                runOnUiThread {
                    showStatus("カメラエラー: ${e.message}", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                }
            }
        )
    }

    private fun triggerScanReady() {
        isReadyToScan = true
        scanButton.visibility = View.VISIBLE
        scanButton.text = "NFC スキャン待機中..."
        showStatus("【スキャン準備OK！】\nスマホの裏側中央（またはカメラ付近）をパスポートのICカードページに密着させてください...", Color.parseColor("#3B82F6"), Color.parseColor("#DBEAFE"))
        resultCard.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraOcrScan()
            } else {
                showStatus("カメラの権限が拒否されたため、OCRを使用できません。手動入力に切り替えてください。", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
            }
        }
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B")) // Slate 500
            setPadding(12, 12, 12, 12)
        }
    }

    private fun createStyledEditText(hint: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.parseColor("#475569")) // Slate 600
            setTextColor(Color.parseColor("#F8FAFC")) // Slate 50
            textSize = 15f
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A")) // Slate 900
                cornerRadius = 16f
                setStroke(1, Color.parseColor("#334155")) // Slate 700
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
    }

    private fun showStatus(msg: String, textColor: Int, bgColor: Int) {
        statusTextView.text = msg
        statusTextView.setTextColor(textColor)
        statusTextView.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 20f
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // FLAG_IMMUTABLE: NFC foreground dispatch はインテントの変更が不要なため、
        // FLAG_MUTABLE を使う必要はない（外部アプリによるインテント改ざんを防止）。
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onStop() {
        super.onStop()
        // CameraX は lifecycleOwner にバインドされているため、
        // ON_STOP イベントで自動的にカメラを届ける。
        // ここで stopScan() を呼ぶと unbindAll() され、バックグラウンドから戻った際に
        // カメラが再開されなくなるため呼び出してはならない。
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        // TextRecognizer のネイティブリソースを解放する（メモリリーク防止）
        mrzScanner?.release()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            if (!isReadyToScan || scannedMrzData == null) {
                showStatus("先にMRZ読み取り（手動またはカメラ）を完了させてください", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                return
            }
            @Suppress("DEPRECATION")
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                showStatus("NFC接続検知。ICチップと通信しています...", Color.parseColor("#2563EB"), Color.parseColor("#DBEAFE"))
                processTag(tag)
            }
        }
    }

    private fun processTag(tag: Tag) {
        IsoDep.get(tag) ?: run {
            showStatus("エラー: このNFCはISO14443-4規格のパスポートではありません", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
            isReadyToScan = false
            return
        }

        val mrz = scannedMrzData ?: return

        activityScope.launch {
            val result = EPassportReader.read(tag, mrz) { progress ->
                activityScope.launch(Dispatchers.Main) {
                    when (progress) {
                        ReadProgress.CONNECTING -> showStatus("NFC接続中...", Color.parseColor("#2563EB"), Color.parseColor("#DBEAFE"))
                        ReadProgress.AUTHENTICATING -> showStatus("暗号認証（BAC）を実行中...", Color.parseColor("#7C3AED"), Color.parseColor("#F3E8FF"))
                        ReadProgress.READING_DG1 -> showStatus("テキストデータ（DG1）を読み込み中...", Color.parseColor("#D97706"), Color.parseColor("#FEF3C7"))
                        ReadProgress.READING_DG2 -> showStatus("顔写真データ（DG2）を読み込み中...", Color.parseColor("#D97706"), Color.parseColor("#FEF3C7"))
                        ReadProgress.PERFORMING_ACTIVE_AUTH -> showStatus("🔒 アクティブ認証 (クローン検知) を実行中...", Color.parseColor("#059669"), Color.parseColor("#D1FAE5"))
                        else -> {}
                    }
                }
            }

            when (result) {
                is ReadResult.Success -> {
                    showStatus("🎉 読み込み成功！ICデータを取得しました。", Color.parseColor("#059669"), Color.parseColor("#D1FAE5"))
                    val passportData = result.data
                    
                    // Render Image if available
                    val dg2 = passportData.dg2
                    if (dg2 != null) {
                        val imgBytes = dg2.faceImageBytes
                        val bitmap = try {
                            BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                        } catch (e: Exception) {
                            null
                        }
                        if (bitmap != null) {
                            faceImageView.setImageBitmap(bitmap)
                            faceImageView.visibility = View.VISIBLE
                        } else {
                            faceImageView.visibility = View.GONE
                        }
                    } else {
                        faceImageView.visibility = View.GONE
                    }

                    // Render details beautifully
                    detailsLayout.removeAllViews()
                    val dg1 = passportData.dg1
                    val name = "${dg1.secondaryIdentifier} ${dg1.primaryIdentifier}"
                    
                    addDetailRow("FULL NAME / 氏名", name)
                    addDetailRow("NATIONALITY / 国籍", getCountryName(dg1.nationality))
                    addDetailRow("PASSPORT NO / 旅券番号", dg1.documentNumber)
                    addDetailRow("DATE OF BIRTH / 生年月日", formatDate(dg1.dateOfBirth))
                    addDetailRow("DATE OF EXPIRY / 有効期限", formatDate(dg1.dateOfExpiry, true))
                    addDetailRow("GENDER / 性別", getGenderName(dg1.sex))
                    addDetailRow("ISSUING STATE / 発行国", getCountryName(dg1.issuingState))
                    addDetailRow("DOCUMENT CODE / 種類", dg1.documentCode)
                    
                    val hasAA = passportData.activeAuthenticationData != null
                    addDetailRow("🔒 ACTIVE AUTHENTICATION / 真贋検証", if (hasAA) "SUCCESS (本物判定)" else "NOT SUPPORTED (非対応)")
                    addDetailRow("🔒 DERIVED MRZ INFO / 鍵生成用のMRZ情報", mrz.mrzInformation)

                    resultCard.visibility = View.VISIBLE
                }
                is ReadResult.Error -> {
                    val e = result.exception
                    showStatus("❌ エラー発生: ${e.message}\n(パスポートが離れたか、入力・スキャンした文字が間違っている可能性があります)", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                    e.printStackTrace()
                }
            }
            isReadyToScan = false
            scannedMrzData = null
            
            // Switch back scan button visibility if manual
            if (currentMode == InputMode.CAMERA) {
                scanButton.visibility = View.GONE
            }
        }
    }

    private fun addDetailRow(label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 16, 12, 16)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(1, Color.parseColor("#334155")) // Bottom divider Slate 700
            }
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B")) // Slate 500
        }

        val valueView = TextView(this).apply {
            text = value
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC")) // Slate 50
            setPadding(0, 4, 0, 0)
        }

        row.addView(labelView)
        row.addView(valueView)
        detailsLayout.addView(row)
    }

    private fun formatDate(yyyymmdd: String, isExpiry: Boolean = false): String {
        if (yyyymmdd.length != 6) return yyyymmdd
        val yy = yyyymmdd.substring(0, 2)
        val mm = yyyymmdd.substring(2, 4)
        val dd = yyyymmdd.substring(4, 6)
        val century = if (isExpiry) {
            "20"
        } else {
            val yearInt = yy.toInt()
            if (yearInt > 45) "19" else "20"
        }
        return "${century}${yy}年 ${mm}月 ${dd}日"
    }

    private fun getCountryName(code: String): String {
        return when (code.uppercase()) {
            "JPN" -> "JAPAN / 日本"
            "USA" -> "UNITED STATES / アメリカ"
            "GBR" -> "UNITED KINGDOM / イギリス"
            "FRA" -> "FRANCE / フランス"
            "DEU" -> "GERMANY / ドイツ"
            "CHN" -> "CHINA / 中国"
            "KOR" -> "KOREA / 韓国"
            else -> code
        }
    }

    private fun getGenderName(sex: String): String {
        return when (sex.uppercase()) {
            "M" -> "MALE / 男性"
            "F" -> "FEMALE / 女性"
            else -> "OTHER / その他"
        }
    }
}
