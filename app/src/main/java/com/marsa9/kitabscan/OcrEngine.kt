package com.marsa9.kitabscan

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class OcrEngine(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val det: OrtSession
    private val rec: OrtSession
    val dict: List<String>

    init {
        val am = context.assets
        det = env.createSession(am.open("ocr/det.onnx").readBytes())
        rec = env.createSession(am.open("ocr/rec.onnx").readBytes())
        dict = am.open("ocr/dict.txt").bufferedReader().readLines()
    }

    private fun describe(name: String, s: OrtSession): String {
        val ins = s.inputInfo.entries.joinToString { (k, v) ->
            "$k ${(v.info as TensorInfo).shape.toList()}"
        }
        val outs = s.outputInfo.entries.joinToString { (k, v) ->
            "$k ${(v.info as TensorInfo).shape.toList()}"
        }
        return "$name\n in: $ins\n out: $outs"
    }

    fun info(): String =
        describe("det", det) + "\n" + describe("rec", rec) + "\ndict: ${dict.size} baris"
      private fun loadBitmap(file: File, maxSide: Int = 2800): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)!!
    }

    // ---- Deteksi baris teks ----
    private fun detect(bmp: Bitmap): List<TextBox> {
        val scale = min(1f, 1280f / max(bmp.width, bmp.height))
        val rw = max(32, (bmp.width * scale / 32).roundToInt() * 32)
        val rh = max(32, (bmp.height * scale / 32).roundToInt() * 32)
        val small = Bitmap.createScaledBitmap(bmp, rw, rh, true)
        val px = IntArray(rw * rh)
        small.getPixels(px, 0, rw, 0, 0, rw, rh)

        val plane = rw * rh
        val data = FloatArray(3 * plane)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (i in px.indices) {
            val p = px[i]
            val red = (p shr 16 and 0xFF) / 255f
            val green = (p shr 8 and 0xFF) / 255f
            val blue = (p and 0xFF) / 255f
            data[i] = (blue - mean[0]) / std[0]
            data[plane + i] = (green - mean[1]) / std[1]
            data[2 * plane + i] = (red - mean[2]) / std[2]
        }

        val prob = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(data), longArrayOf(1, 3, rh.toLong(), rw.toLong())
        ).use { t ->
            det.run(mapOf(det.inputNames.first() to t)).use { res ->
                val fb = (res[0] as OnnxTensor).floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        check(prob.size == plane) { "Ukuran output deteksi tidak cocok" }

        val thr = 0.3f
        val unclip = 1.6f
        val visited = BooleanArray(plane)
        val stack = IntArray(plane)
        val sx = bmp.width.toFloat() / rw
        val sy = bmp.height.toFloat() / rh
        val boxes = mutableListOf<TextBox>()

        for (start in 0 until plane) {
            if (visited[start] || prob[start] <= thr) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var minX = rw; var maxX = 0; var minY = rh; var maxY = 0
            var count = 0; var sum = 0f
            while (sp > 0) {
                val cur = stack[--sp]
                val x = cur % rw
                val y = cur / rw
                count++; sum += prob[cur]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && !visited[cur - 1] && prob[cur - 1] > thr) { visited[cur - 1] = true; stack[sp++] = cur - 1 }
                if (x < rw - 1 && !visited[cur + 1] && prob[cur + 1] > thr) { visited[cur + 1] = true; stack[sp++] = cur + 1 }
                if (y > 0 && !visited[cur - rw] && prob[cur - rw] > thr) { visited[cur - rw] = true; stack[sp++] = cur - rw }
                if (y < rh - 1 && !visited[cur + rw] && prob[cur + rw] > thr) { visited[cur + rw] = true; stack[sp++] = cur + rw }
            }
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            if (count < 20 || min(bw, bh) < 3 || sum / count < 0.5f) continue

            val d = (bw * bh) * unclip / (2f * (bw + bh))
            val l = ((minX - d) * sx).roundToInt().coerceIn(0, bmp.width - 1)
            val t = ((minY - d) * sy).roundToInt().coerceIn(0, bmp.height - 1)
            val r = ((maxX + 1 + d) * sx).roundToInt().coerceIn(l + 1, bmp.width)
            val b = ((maxY + 1 + d) * sy).roundToInt().coerceIn(t + 1, bmp.height)
            boxes.add(TextBox(l, t, r, b))
        }
        return boxes
    }

    // ---- Baca satu potongan teks ----
    private fun recognize(crop: Bitmap): String {
        val h = 48
        val w = min(2400, max(16, (crop.width * h / crop.height.toFloat()).roundToInt()))
        val small = Bitmap.createScaledBitmap(crop, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)

        val plane = w * h
        val data = FloatArray(3 * plane)
        for (i in px.indices) {
            val p = px[i]
            data[i] = ((p and 0xFF) / 255f - 0.5f) / 0.5f
            data[plane + i] = ((p shr 8 and 0xFF) / 255f - 0.5f) / 0.5f
            data[2 * plane + i] = ((p shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
        }

        return OnnxTensor.createTensor(
            env, FloatBuffer.wrap(data), longArrayOf(1, 3, h.toLong(), w.toLong())
        ).use { t ->
            rec.run(mapOf(rec.inputNames.first() to t)).use { res ->
                val out = res[0] as OnnxTensor
                val shape = out.info.shape
                val steps = shape[1].toInt()
                val classes = shape[2].toInt()
                val fb = out.floatBuffer
                val arr = FloatArray(fb.remaining()).also { fb.get(it) }

                val sb = StringBuilder()
                var prev = 0
                for (ti in 0 until steps) {
                    var best = 0
                    var bv = -1f
                    val off = ti * classes
                    for (c in 0 until classes) {
                        val v = arr[off + c]
                        if (v > bv) { bv = v; best = c }
                    }
                    if (best != 0 && best != prev) {
                        sb.append(if (best - 1 < dict.size) dict[best - 1] else " ")
                    }
                    prev = best
                }
                reverseRtl(sb.toString())
            }
        }
    }

    // Model Arab mengeluarkan karakter dari kiri ke kanan, jadi dibalik
    // (angka dan huruf latin tetap utuh)
    private fun reverseRtl(s: String): String {
        val latin = Regex("[a-zA-Z0-9 :*./%+-]")
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in s) {
            val c = ch.toString()
            if (!latin.containsMatchIn(c)) {
                if (cur.isNotEmpty()) { parts.add(cur.toString()); cur.setLength(0) }
                parts.add(c)
            } else cur.append(c)
        }
        if (cur.isNotEmpty()) parts.add(cur.toString())
        return parts.reversed().joinToString("")
    }

    // ---- Satu halaman penuh ----
    fun recognizePage(file: File): String {
        val bmp = loadBitmap(file)
        val boxes = detect(bmp).sortedBy { (it.t + it.b) / 2 }

        val lines = mutableListOf<MutableList<TextBox>>()
        for (b in boxes) {
            val line = lines.lastOrNull()
            if (line != null) {
                val lt = line.minOf { it.t }
                val lb = line.maxOf { it.b }
                val overlap = min(b.b, lb) - max(b.t, lt)
                if (overlap > 0.5f * min(b.b - b.t, lb - lt)) { line.add(b); continue }
            }
            lines.add(mutableListOf(b))
        }

        return lines.joinToString("\n") { line ->
            line.sortedByDescending { it.r }.joinToString(" ") { b ->
                recognize(Bitmap.createBitmap(bmp, b.l, b.t, b.r - b.l, b.b - b.t))
            }
        }
    }      
}

private class TextBox(val l: Int, val t: Int, val r: Int, val b: Int)