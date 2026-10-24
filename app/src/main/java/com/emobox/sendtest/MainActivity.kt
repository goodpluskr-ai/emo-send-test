package com.emobox.sendtest

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * 이모 전송 시험 앱 — 카톡 대화방에 그림을 넣는 4가지 방법을 실제로 시험하고 결과를 기록한다.
 *  1. 복사 → 붙여넣기   2. 전용 키보드   3. 공유하기   4. 떠 있는 버튼   (+5. 공유로 받아 저장)
 */
class MainActivity : Activity() {

    private val ink = Color.parseColor("#1C2240")
    private val muted = Color.parseColor("#666D8F")
    private val accent = Color.parseColor("#FF5C7A")

    private lateinit var root: LinearLayout
    private lateinit var thumbs: LinearLayout
    private var selected: File? = null
    private lateinit var kbdLog: TextView
    private lateinit var bubbleBtn: Button
    private lateinit var memo: EditText
    private val resultLabels = mutableMapOf<String, TextView>()
    private var wantBubble = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EmoStore.ensureSamples(this)
        buildUi()
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshThumbs()
        refreshKbdLog()
        if (wantBubble && Settings.canDrawOverlays(this) && !BubbleService.running) {
            wantBubble = false
            startBubble()
        }
        bubbleBtn.text = if (BubbleService.running) "떠 있는 버튼 끄기" else "떠 있는 버튼 켜기"
    }

    // ---------------- 화면 ----------------
    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
            setBackgroundColor(Color.parseColor("#EEF1F9"))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#EEF1F9"))
            fitsSystemWindows = true
            addView(root)
        }
        setContentView(scroll)

        root.addView(text("이모 전송 시험", 24f, bold = true))
        root.addView(text("카톡 대화방에 그림을 넣는 방법이 실제로 되는지 확인하는 시험용 앱이에요. 각 시험 후 결과 버튼을 눌러 기록해 주세요.", 14f, color = muted))
        root.addView(text("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n카카오톡: ${EmoStore.kakaoVersion(this)}", 12.5f, color = muted).apply { setPadding(0, dp(8), 0, 0) })

        // 시험 그림
        val pick = card("시험할 그림 고르기", "그림을 눌러 고르세요. PNG·움짤(GIF)·사진(JPG)을 각각 시험해 보면 좋아요.")
        thumbs = LinearLayout(this)
        pick.addView(HorizontalScrollView(this).apply { addView(thumbs) }, LinearLayout.LayoutParams(-1, dp(96)))
        pick.addView(button("내 그림 추가 (갤러리)") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 10)
        })

        // 시험 1
        val t1 = card("시험 1 · 복사 → 붙여넣기", "① 아래 버튼으로 복사  ② 카톡 대화방 입력창을 길게 누르기  ③ ‘붙여넣기’ → 그림이 들어가나요?")
        t1.addView(row(button("고른 그림 복사") {
            selected?.let { EmoStore.copyToClipboard(this, it); toast("복사했어요. 카톡에서 입력창을 길게 눌러 보세요") } ?: toast("먼저 그림을 고르세요")
        }, button("카톡 열기") { openKakao() }))
        t1.addView(results("t1", listOf("그림으로 들어감", "글자(주소)만 들어감", "붙여넣기 메뉴 없음")))

        // 시험 2
        val t2 = card("시험 2 · 전용 키보드", "① ‘키보드 켜기’에서 ‘이모 시험 키보드’ 켜기  ② 카톡 입력창에서 ‘키보드 바꾸기’  ③ 그림을 누르면 바로 들어가나요? 키보드 위쪽 글에 지원 여부가 나와요.")
        t2.addView(row(button("키보드 켜기") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            button("키보드 바꾸기") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }))
        kbdLog = text("", 12.5f, color = muted)
        t2.addView(kbdLog)
        t2.addView(results("t2", listOf("바로 들어감", "복사로만 됨", "안 됨")))

        // 시험 3
        val t3 = card("시험 3 · 공유하기", "고른 그림을 카톡으로 공유해요. 대화방을 다시 골라야 하는지, 그림이 제대로 가는지 확인하세요.")
        t3.addView(row(button("카톡으로 바로 공유") { share(true) }, button("앱 골라서 공유") { share(false) }))
        t3.addView(results("t3", listOf("잘 감 (대화방 선택 필요)", "잘 감 (보던 방으로 바로)", "안 됨")))

        // 시험 4
        val t4 = card("시험 4 · 떠 있는 버튼", "① 버튼 켜기 (처음엔 ‘다른 앱 위에 표시’ 권한을 켜야 해요)  ② 카톡 대화방에서 분홍 ‘이모’ 버튼 누르기  ③ 그림 탭 = 복사 → 입력창 길게 눌러 붙여넣기 / 그림 길게 = 카톡 공유")
        bubbleBtn = button("떠 있는 버튼 켜기") { toggleBubble() }
        t4.addView(bubbleBtn)
        t4.addView(results("t4", listOf("편하게 보냄", "되지만 불편", "안 됨")))

        // 시험 5
        val t5 = card("시험 5 · 카톡에서 받은 그림 저장", "카톡 대화방에서 받은 그림을 길게 누르기 → 공유 → ‘이모 전송 시험’을 고르면 이 앱에 저장되나요?")
        t5.addView(results("t5", listOf("저장됨", "목록에 앱이 없음", "안 됨")))

        // 결과
        val sum = card("결과 보내기", "메모를 남기고 ‘결과 복사’를 누른 뒤, Claude와의 대화창에 붙여넣어 주세요.")
        memo = EditText(this).apply {
            hint = "메모 (예: 움짤은 멈춘 그림으로 감, 붙여넣기 2번 눌러야 함)"
            minLines = 2; textSize = 14f
            setText(EmoStore.get(this@MainActivity, "memo") ?: "")
        }
        sum.addView(memo)
        sum.addView(button("결과 복사", primary = true) { copyReport() })
    }

    private fun card(title: String, desc: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        }
        c.addView(text(title, 17f, bold = true))
        c.addView(text(desc, 13.5f, color = muted).apply { setPadding(0, dp(4), 0, dp(8)) })
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return c
    }

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int = ink) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        if (primary) { setTextColor(Color.WHITE); background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(12).toFloat() } }
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        views.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }) }
    }

    private fun results(key: String, options: List<String>): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, 0) }
        val label = text("", 13f, bold = true, color = accent)
        resultLabels[key] = label
        fun show() { label.text = EmoStore.get(this, key)?.let { "기록: $it" } ?: "기록: 아직 안 함" }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.forEach { o ->
            chips.addView(Button(this).apply {
                text = o; isAllCaps = false; textSize = 13f
                setOnClickListener { EmoStore.put(this@MainActivity, key, o); show() }
            })
        }
        show()
        box.addView(label); box.addView(chips)
        return box
    }

    private fun refreshThumbs() {
        thumbs.removeAllViews()
        val files = EmoStore.list(this)
        if (selected == null || selected?.exists() != true) selected = files.firstOrNull()
        files.forEach { f ->
            thumbs.addView(ImageView(this).apply {
                setImageBitmap(EmoStore.thumb(f, dp(84)))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EEF1F9")); cornerRadius = dp(14).toFloat()
                    if (f == selected) setStroke(dp(3), accent)
                }
                contentDescription = f.name
                setOnClickListener { selected = f; refreshThumbs() }
            }, LinearLayout.LayoutParams(dp(88), dp(88)).apply { marginEnd = dp(8) })
        }
    }

    private fun refreshKbdLog() {
        val lines = EmoStore.all(this).filterKeys { it.startsWith("kbd_") }
            .entries.sortedBy { it.key }
            .map { (k, v) -> (if (k.startsWith("kbd_types_")) "지원형식 " + k.removePrefix("kbd_types_") else "마지막 결과 " + k.removePrefix("kbd_last_")) + ": " + v }
        kbdLog.text = if (lines.isEmpty()) "키보드 기록: 아직 없음" else "키보드 자동 기록\n" + lines.joinToString("\n")
    }

    // ---------------- 동작 ----------------
    private fun openKakao() {
        val i = packageManager.getLaunchIntentForPackage(EmoStore.KAKAO)
        if (i != null) startActivity(i) else toast("카카오톡이 설치되어 있지 않아요")
    }

    private fun share(toKakao: Boolean) {
        val f = selected ?: return toast("먼저 그림을 고르세요")
        try { startActivity(EmoStore.shareIntent(this, f, toKakao)) }
        catch (e: Exception) { toast("공유할 수 없어요: ${e.javaClass.simpleName}") }
    }

    private fun toggleBubble() {
        if (BubbleService.running) {
            stopService(Intent(this, BubbleService::class.java))
            bubbleBtn.text = "떠 있는 버튼 켜기"; return
        }
        if (!Settings.canDrawOverlays(this)) {
            wantBubble = true
            toast("목록에서 ‘이모 전송 시험’을 찾아 허용한 뒤 뒤로 가기를 누르세요")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        }
        startBubble()
    }

    private fun startBubble() {
        try {
            startForegroundService(Intent(this, BubbleService::class.java))
            bubbleBtn.text = "떠 있는 버튼 끄기"
            toast("켜졌어요. 화면 오른쪽의 분홍 ‘이모’ 버튼을 확인하고 카톡을 열어 보세요")
        } catch (e: Exception) {
            EmoStore.put(this, "bubble_error", "${e.javaClass.simpleName}: ${e.message}")
            toast("버튼을 켜지 못했어요: ${e.javaClass.simpleName}")
        }
    }

    @Deprecated("Activity 기본 API 사용")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let { uris += it } }
        if (uris.isEmpty()) data.data?.let { uris += it }
        importAll(uris)
    }

    /** 시험 5: 다른 앱에서 공유로 들어온 그림 저장 */
    private fun handleIncoming(i: Intent?) {
        if (i == null) return
        val uris = mutableListOf<Uri>()
        when (i.action) {
            Intent.ACTION_SEND -> streamOf(i)?.let { uris += it }
            Intent.ACTION_SEND_MULTIPLE -> streamListOf(i)?.let { uris += it }
            else -> return
        }
        if (importAll(uris) > 0) EmoStore.put(this, "t5", "저장됨 (자동 기록)")
        resultLabels["t5"]?.text = EmoStore.get(this, "t5")?.let { "기록: $it" } ?: "기록: 아직 안 함"
        i.action = null
    }

    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListOf(i: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)

    private fun importAll(uris: List<Uri>): Int {
        var n = 0
        uris.forEach { u -> EmoStore.importUri(this, u)?.let { selected = it; n++ } }
        toast(if (n > 0) "${n}장 저장했어요" else "그림을 저장하지 못했어요")
        if (::thumbs.isInitialized) refreshThumbs()
        return n
    }

    private fun copyReport() {
        EmoStore.put(this, "memo", memo.text.toString())
        val r = StringBuilder()
        r.append("[이모 전송 시험 결과]\n")
        r.append("기기: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        r.append("카카오톡: ${EmoStore.kakaoVersion(this)}\n")
        listOf("t1" to "1 복사→붙여넣기", "t2" to "2 전용 키보드", "t3" to "3 공유하기", "t4" to "4 떠 있는 버튼", "t5" to "5 받은 그림 저장")
            .forEach { (k, name) -> r.append("$name: ${EmoStore.get(this, k) ?: "안 함"}\n") }
        EmoStore.all(this).filterKeys { it.startsWith("kbd_") || it.startsWith("bubble_") }
            .toSortedMap().forEach { (k, v) -> r.append("  ($k) $v\n") }
        r.append("메모: ${memo.text}\n")
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("시험 결과", r.toString()))
        toast("결과를 복사했어요. Claude 대화창에 붙여넣어 주세요")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
