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
}