package com.imgreducer.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val maxFiles = 30
    private var selectedUris: List<Uri> = emptyList()

    private lateinit var tvStatus: TextView
    private lateinit var spFormat: Spinner
    private lateinit var spSize: Spinner
    private lateinit var customBox: LinearLayout
    private lateinit var etWidth: EditText
    private lateinit var etHeight: EditText
    private lateinit var cbRatio: CheckBox
    private lateinit var etTargetKb: EditText
    private lateinit var tvQuality: TextView
    private lateinit var sbQuality: SeekBar
    private lateinit var btnCompress: Button
    private lateinit var tvResult: TextView

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        selectedUris = if (uris.size > maxFiles) {
            Toast.makeText(this, "Maximum $maxFiles images can be selected at once", Toast.LENGTH_LONG).show()
            uris.take(maxFiles)
        } else {
            uris
        }
        tvStatus.text = "Selected: ${selectedUris.size} / $maxFiles images"
        tvResult.text = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F172A")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }
        root.addView(container)

        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }

        val title = TextView(this).apply {
            text = "Image Size Reducer"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        container.addView(title)

        val btnPick = Button(this).apply {
            text = "Choose Images (Max 30)"
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            setOnClickListener { pickImages.launch("image/*") }
        }
        container.addView(btnPick)

        tvStatus = TextView(this).apply {
            text = "You can choose up to 30 images at once"
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        container.addView(tvStatus)

        container.addView(label("Output Format"))
        spFormat = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("JPEG (.jpg) — Standard", "WebP (.webp) — Best Compression", "PNG (.png) — Transparent")
            )
            setBackgroundColor(Color.parseColor("#334155"))
        }
        container.addView(spFormat)

        container.addView(label("Resize (Width / Height)"))
        spSize = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Original Size", "3840 px (4K)", "1920 px (Full HD)", "1280 px (HD)", "1080x1080 (Square)", "800 px", "600 px", "400 px", "Custom Size")
            )
            setSelection(3)
            setBackgroundColor(Color.parseColor("#334155"))
        }
        container.addView(spSize)

        customBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        etWidth = EditText(this).apply {
            hint = "Width (px)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        etHeight = EditText(this).apply {
            hint = "Height (px)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(etWidth)
        row.addView(etHeight)
        cbRatio = CheckBox(this).apply {
            text = "Keep Aspect Ratio"
            setTextColor(Color.parseColor("#CBD5E1"))
            isChecked = true
        }
        customBox.addView(row)
        customBox.addView(cbRatio)
        container.addView(customBox)

        spSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                customBox.visibility = if (pos == 8) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        container.addView(label("Target File Size in KB (Optional)"))
        etTargetKb = EditText(this).apply {
            hint = "e.g., 200 (Compress to under 200 KB)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#334155"))
            setPadding(24, 24, 24, 24)
        }
        container.addView(etTargetKb)

        tvQuality = label("Quality: 75%")
        container.addView(tvQuality)
        sbQuality = SeekBar(this).apply {
            max = 90
            progress = 65
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    tvQuality.text = "Quality: ${p + 10}%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        container.addView(sbQuality)

        btnCompress = Button(this).apply {
            text = "Compress & Save to Gallery"
            setBackgroundColor(Color.parseColor("#22C55E"))
            setTextColor(Color.parseColor("#052E16"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 40
            layoutParams = lp
            setOnClickListener { startCompression() }
        }
        container.addView(btnCompress)

        tvResult = TextView(this).apply {
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 14f
            setPadding(0, 30, 0, 0)
        }
        container.addView(tvResult)

        setContentView(root)
    }

    private fun startCompression() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Please select images first", Toast.LENGTH_SHORT).show()
            return
        }
        btnCompress.isEnabled = false
        tvResult.text = "Processing ${selectedUris.size} images..."

        val formatIdx = spFormat.selectedItemPosition
        val sizeIdx = spSize.selectedItemPosition
        val customW = etWidth.text.toString().toIntOrNull() ?: 0
        val customH = etHeight.text.toString().toIntOrNull() ?: 0
        val keepRatio = cbRatio.isChecked
        val targetKb = etTargetKb.text.toString().toIntOrNull() ?: 0
        val quality = sbQuality.progress + 10

        thread {
            var successCount = 0
            var totalBytes = 0L

            for ((index, uri) in selectedUris.withIndex()) {
                try {
                    val input = contentResolver.openInputStream(uri) ?: continue
                    val origBitmap = BitmapFactory.decodeStream(input)
                    input.close()
                    if (origBitmap == null) continue

                    val (tw, th) = calcSize(origBitmap.width, origBitmap.height, sizeIdx, customW, customH, keepRatio)
                    var bmp = if (tw != origBitmap.width || th != origBitmap.height) {
                        Bitmap.createScaledBitmap(origBitmap, tw, th, true)
                    } else origBitmap

                    val (format, ext, mime) = when (formatIdx) {
                        1 -> Triple(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP,
                            "webp", "image/webp"
                        )
                        2 -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
                        else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                    }

                    val bytes = compressBitmap(bmp, format, quality, targetKb)
                    saveToGallery(bytes, "reduced_${System.currentTimeMillis()}_$index.$ext", mime)
                    totalBytes += bytes.size
                    successCount++
                } catch (_: Exception) {}
            }

            runOnUiThread {
                btnCompress.isEnabled = true
                val totalSizeFormatted = if (totalBytes < 1048576) {
                    String.format("%.1f KB", totalBytes / 1024.0)
                } else {
                    String.format("%.2f MB", totalBytes / 1048576.0)
                }
                tvResult.text = "Success! Saved $successCount images to Gallery (Pictures/ImageReducer).\nTotal Output Size: $totalSizeFormatted"
            }
        }
    }

    private fun calcSize(ow: Int, oh: Int, idx: Int, cw: Int, ch: Int, keepRatio: Boolean): Pair<Int, Int> {
        val presets = mapOf(1 to 3840, 2 to 1920, 3 to 1280, 5 to 800, 6 to 600, 7 to 400)
        return when {
            idx == 0 -> ow to oh
            idx == 4 -> 1080 to 1080
            idx == 8 -> {
                if (cw <= 0 && ch <= 0) ow to oh
                else if (keepRatio) {
                    when {
                        cw > 0 && ch <= 0 -> cw to (oh.toFloat() * cw / ow).roundToInt()
                        ch > 0 && cw <= 0 -> (ow.toFloat() * ch / oh).roundToInt() to ch
                        else -> {
                            val r = min(cw.toFloat() / ow, ch.toFloat() / oh)
                            (ow * r).roundToInt() to (oh * r).roundToInt()
                        }
                    }
                } else (if (cw > 0) cw else ow) to (if (ch > 0) ch else oh)
            }
            else -> {
                val maxW = presets[idx] ?: ow
                if (ow > maxW) maxW to (oh.toFloat() * maxW / ow).roundToInt() else ow to oh
            }
        }
    }

    private fun compressBitmap(bmp: Bitmap, format: Bitmap.CompressFormat, quality: Int, targetKb: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (targetKb <= 0) {
            bmp.compress(format, quality, out)
            return out.toByteArray()
        }
        val targetBytes = targetKb * 1024
        if (format == Bitmap.CompressFormat.PNG) {
            var cur = bmp
            var scale = 1f
            cur.compress(format, 100, out)
            while (out.size() > targetBytes && scale > 0.2f) {
                scale *= 0.8f
                out.reset()
                cur = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1), (bmp.height * scale).roundToInt().coerceAtLeast(1), true)
                cur.compress(format, 100, out)
            }
            return out.toByteArray()
        }
        var minQ = 5
        var maxQ = 98
        var best: ByteArray? = null
        repeat(7) {
            val mid = (minQ + maxQ) / 2
            out.reset()
            bmp.compress(format, mid, out)
            if (out.size() <= targetBytes) {
                best = out.toByteArray()
                minQ = mid
            } else {
                maxQ = mid
            }
        }
        return best ?: run {
            out.reset()
            bmp.compress(format, 5, out)
            out.toByteArray()
        }
    }

    private fun saveToGallery(bytes: ByteArray, filename: String, mime: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ImageReducer")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    }
}
