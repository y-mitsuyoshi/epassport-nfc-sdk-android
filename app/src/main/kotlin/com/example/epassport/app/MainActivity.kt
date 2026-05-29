package com.example.epassport.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.epassport.api.EPassportReader
import com.example.epassport.api.ReadResult
import com.example.epassport.domain.model.MrzData
import com.example.epassport.usecase.ReadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusTextView: TextView
    private lateinit var docNoInput: EditText
    private lateinit var dobInput: EditText
    private lateinit var doeInput: EditText
    private lateinit var scanButton: Button
    
    // Result UI components
    private lateinit var resultCard: LinearLayout
    private lateinit var faceImageView: ImageView
    private lateinit var detailsLayout: LinearLayout

    private var isReadyToScan = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            setPadding(0, 0, 0, 48)
        }

        // 2. Input Fields Card Card
        val inputCard = LinearLayout(this).apply {
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

        // 3. Scan Button
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
                val docNo = docNoInput.text.toString().trim()
                val dob = dobInput.text.toString().trim()
                val doe = doeInput.text.toString().trim()
                if (docNo.isBlank() || dob.isBlank() || doe.isBlank()) {
                    showStatus("エラー：MRZ情報を入力してください", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                    return@setOnClickListener
                }
                isReadyToScan = true
                showStatus("【スキャン待機中】\nスマホの裏側中央（またはカメラ付近）をパスポートのICカードページに密着させてください...", Color.parseColor("#3B82F6"), Color.parseColor("#DBEAFE"))
                resultCard.visibility = View.GONE
            }
        }

        // 4. Status Indicator Box
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

        // 5. Result Container Card (Hidden by default)
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
        mainLayout.addView(inputCard)
        mainLayout.addView(scanButton)
        mainLayout.addView(statusTextView)
        mainLayout.addView(resultCard)

        rootScrollView.addView(mainLayout)
        setContentView(rootScrollView)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
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

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            if (!isReadyToScan) {
                showStatus("先に「NFC読み取りを開始する」ボタンを押してください", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
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

        val docNo = docNoInput.text.toString().trim()
        val dob = dobInput.text.toString().trim()
        val doe = doeInput.text.toString().trim()
        val mrzData = MrzData(docNo, dob, doe)
        System.err.println("User Inputs - docNo: '$docNo' (len=${docNo.length}), dob: '$dob' (len=${dob.length}), doe: '$doe' (len=${doe.length})")
        System.err.println("MRZ Info (MainActivity): '${mrzData.mrzInformation}'")

        activityScope.launch {
            val result = EPassportReader.read(tag, mrzData) { progress ->
                activityScope.launch(Dispatchers.Main) {
                    when (progress) {
                        ReadProgress.CONNECTING -> showStatus("NFC接続中...", Color.parseColor("#2563EB"), Color.parseColor("#DBEAFE"))
                        ReadProgress.AUTHENTICATING -> showStatus("暗号認証（BAC）を実行中...", Color.parseColor("#7C3AED"), Color.parseColor("#F3E8FF"))
                        ReadProgress.READING_DG1 -> showStatus("テキストデータ（DG1）を読み込み中...", Color.parseColor("#D97706"), Color.parseColor("#FEF3C7"))
                        ReadProgress.READING_DG2 -> showStatus("顔写真データ（DG2）を読み込み中...", Color.parseColor("#D97706"), Color.parseColor("#FEF3C7"))
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
                            // If decoding fails (e.g. JP2), we hide the image or show placeholder
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
                    addDetailRow("🔒 DERIVED MRZ INFO / 鍵生成用のMRZ情報 (デバッグ用)", mrzData.mrzInformation)

                    resultCard.visibility = View.VISIBLE
                }
                is ReadResult.Error -> {
                    val e = result.exception
                    showStatus("❌ エラー発生: ${e.message}\n(パスポートが離れたか、入力した文字が間違っている可能性があります)", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"))
                    e.printStackTrace()
                }
            }
            isReadyToScan = false
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
