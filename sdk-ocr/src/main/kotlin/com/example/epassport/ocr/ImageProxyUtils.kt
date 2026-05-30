package com.example.epassport.ocr

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * CameraX [ImageProxy] を JPEG [ByteArray] に変換するユーティリティ。
 *
 * YUV_420_888 → NV21 → JPEG へ変換する。
 */
object ImageProxyUtils {

    /**
     * [ImageProxy] を JPEG 形式の [ByteArray] に変換する。
     *
     * @param quality JPEG品質 (0-100)
     * @return JPEGエンコードされたバイト配列
     */
    fun toJpegByteArray(imageProxy: ImageProxy, quality: Int = 90): ByteArray {
        val nv21 = yuv420888ToNv21(imageProxy)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val planes = imageProxy.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        return nv21
    }
}
