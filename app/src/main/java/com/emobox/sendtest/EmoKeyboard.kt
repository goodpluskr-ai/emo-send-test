package com.emobox.sendtest

import android.content.ClipDescription
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

/**
 * 시험 2: 전용 키보드.
 * 카톡 입력창이 '그림 직접 입력'을 받아주는지 확인하고 결과를 기록한다.
 * 받아주지 않으면 클립보드 복사로 대신한다.
 */
class EmoKeyboard : InputMethodService() {

    private lateinit var status: TextView
    private lateinit var row: LinearLayout

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EEF1F9"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        status = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#1C2240"))
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply { addView(row) }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        bottom.addView(Button(this).apply {
            text = "다른 키보드로"
            setOnClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        })
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(96)))
        root.addView(bottom)
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val types = EditorInfoCompat.getContentMimeTypes(info)
        val pkg = info.packageName ?: "?"
        status.text = if (types.isEmpty())
            "입력창: $pkg\n그림 직접 입력: 지원 안 함 → 누르면 복사해 둘게요"
        else
            "입력창: $pkg\n그림 직접 입력 지원: ${types.joinToString()}\n그림을 누르면 바로 넣어요"
        EmoStore.put(this, "kbd_types_$pkg", if (types.isEmpty()) "없음" else types.joinToString())
        fillRow()
    }

    private fun fillRow() {
        row.removeAllViews()
        EmoStore.ensureSamples(this)
        EmoStore.list(this).forEach { f ->
            val iv = ImageView(this).apply {
                setImageBitmap(EmoStore.thumb(f, dp(84)))
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = GradientDrawable().apply {
                    setColor(Color.WHITE); cornerRadius = dp(14).toFloat()
                }
                setPadding(dp(4), dp(4), dp(4), dp(4))
                contentDescription = f.name
                setOnClickListener { send(f) }
            }
            row.addView(iv, LinearLayout.LayoutParams(dp(88), dp(88)).apply { marginEnd = dp(8) })
        }
    }

    private fun send(f: File) {
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val pkg = ei.packageName ?: "?"
        val mime = EmoStore.mime(f)
        val types = EditorInfoCompat.getContentMimeTypes(ei)
        val supported = types.any { ClipDescription.compareMimeTypes(mime, it) }

        val result: String
        if (supported) {
            val content = InputContentInfoCompat(
                EmoStore.uri(this, f), ClipDescription("이모", arrayOf(mime)), null
            )
            val ok = InputConnectionCompat.commitContent(
                ic, ei, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
            )
            result = if (ok) "직접 입력 성공($mime)" else "직접 입력 거절됨($mime)"
            status.text = if (ok) "✅ 그림을 넣었어요 ($mime)\n카톡 화면에 그림이 나타났는지 확인하세요"
            else "❌ 입력창이 이 그림을 거절했어요 ($mime)"
        } else {
            EmoStore.copyToClipboard(this, f)
            result = "직접 입력 미지원($mime) → 복사로 대신함"
            status.text = "이 입력창은 $mime 직접 입력을 받지 않아요.\n대신 복사했어요 → 입력창을 길게 눌러 ‘붙여넣기’"
        }
        EmoStore.put(this, "kbd_last_$pkg", result)
    }
}
