package com.emobox.sendtest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/** 시험용 그림 저장소 + 전송 도우미 + 결과 기록 */
object EmoStore {
    const val KAKAO = "com.kakao.talk"

    fun dir(ctx: Context): File = File(ctx.filesDir, "emo").apply { mkdirs() }

    /** 첫 실행 때 견본 그림 4장(PNG 2, 움짤 GIF 1, 사진 JPG 1)을 넣어 둔다 */
    fun ensureSamples(ctx: Context) {
        val d = dir(ctx)
        if (d.listFiles()?.isNotEmpty() == true) return
        ctx.assets.list("samples")?.forEach { name ->
            ctx.assets.open("samples/$name").use { input ->
                File(d, name).outputStream().use { out -> input.copyTo(out) }
            }
        }
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    fun uri(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun mime(f: File): String = when (f.extension.lowercase()) {
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    /** 다른 앱(갤러리·카톡 공유)에서 받은 그림을 앱 저장소로 복사 */
    fun importUri(ctx: Context, src: Uri): File? = try {
        val type = ctx.contentResolver.getType(src) ?: "image/png"
        val ext = when {
            type.contains("gif") -> "gif"
            type.contains("jpeg") || type.contains("jpg") -> "jpg"
            type.contains("webp") -> "webp"
            else -> "png"
        }
        val f = File(dir(ctx), "my_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        ctx.contentResolver.openInputStream(src)!!.use { input ->
            f.outputStream().use { out -> input.copyTo(out) }
        }
        f
    } catch (e: Exception) {
        null
    }

    /** 시험 1: 그림을 클립보드에 '그림'으로 복사 */
    fun copyToClipboard(ctx: Context, f: File) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "이모", uri(ctx, f)))
    }

    /** 시험 3: 공유. toKakao=true 이면 카톡으로 바로, 아니면 앱 고르기 */
    fun shareIntent(ctx: Context, f: File, toKakao: Boolean): Intent {
        val u = uri(ctx, f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime(f)
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = ClipData.newRawUri("이모", u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (toKakao) setPackage(KAKAO)
        }
        return if (toKakao) send else Intent.createChooser(send, "보낼 앱 고르기")
    }

    /** 미리보기용 작은 그림 (큰 사진도 메모리 걱정 없이) */
    fun thumb(f: File, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    // ---------- 결과 기록 ----------
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("results", Context.MODE_PRIVATE)
    fun put(ctx: Context, key: String, value: String) = prefs(ctx).edit().putString(key, value).apply()
    fun get(ctx: Context, key: String): String? = prefs(ctx).getString(key, null)
    fun all(ctx: Context): Map<String, *> = prefs(ctx).all

    fun kakaoVersion(ctx: Context): String = try {
        val pm = ctx.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(KAKAO, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(KAKAO, 0)
        info.versionName ?: "?"
    } catch (e: Exception) {
        "설치 안 됨"
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
