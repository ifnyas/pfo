package co.wangun.pfo

import android.app.Activity
import android.graphics.*
import android.hardware.Camera
import android.os.StrictMode
import android.util.Base64
import android.util.Log
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs


object FaceUtils {

    // BMP Converter
    //
    @Throws(IllegalArgumentException::class)
    fun convert(base64Str: String): Bitmap {

        val decodedBytes = Base64.decode(
            base64Str.substring(base64Str.indexOf(",") + 1), Base64.DEFAULT
        )
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    @JvmStatic
    fun convert(bitmap: Bitmap): String {

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    @JvmStatic
    fun saveImage(bmp: Bitmap, name: String, path: String) {

        val myDir = File(path)
        myDir.mkdirs()

        val fileName = "$name.jpg"
        val file = File(myDir, fileName)
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    @JvmStatic
    fun downloadImage(link: String, path: String) {

        // TODO: please change this to async
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        // not recommended method

        try {
            val url = URL(link)
            val connection = url
                .openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            val bmp = BitmapFactory.decodeStream(input)
            saveImage(bmp, "Daftar", path)
            Log.d("BmpConverter", "image saved")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Display Utils
    //
    // Gets the current display rotation in angles.
    @JvmStatic
    fun getDisplayRotation(activity: Activity): Int {
        val rotation = activity.windowManager.defaultDisplay
            .rotation
        when (rotation) {
            Surface.ROTATION_0 -> return 0
            Surface.ROTATION_90 -> return 90
            Surface.ROTATION_180 -> return 180
            Surface.ROTATION_270 -> return 270
        }
        return 0
    }

    @JvmStatic
    fun getDisplayOrientation(degrees: Int, cameraId: Int): Int {
        // See android.hardware.Camera.setDisplayOrientation for documentation.
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        return result
    }

    @JvmStatic
    fun getOptimalPreviewSize(
        currentActivity: Activity,
        sizes: List<Camera.Size>?,
        targetRatio: Double
    ): Camera.Size? {

        // Use a very small tolerance because we want an exact match.
        val aspectTolerance = 0.001
        if (sizes == null) return null
        var optimalSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of preview surface. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size.
        val point =
            getDefaultDisplaySize(currentActivity, Point())
        val targetHeight = Math.min(point.x, point.y)

        // Try to find an size match aspect ratio and size
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > aspectTolerance) continue
            if (abs(size.height - targetHeight) < minDiff) {
                optimalSize = size
                minDiff = abs(size.height - targetHeight).toDouble()
            }
        }

        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.w("FaceUtils", "No preview size match the aspect ratio")
            minDiff = Double.MAX_VALUE
            for (size in sizes) {
                if (abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - targetHeight).toDouble()
                }
            }
        }
        return optimalSize
    }

    private fun getDefaultDisplaySize(
        activity: Activity,
        size: Point
    ): Point {
        val d = activity.windowManager.defaultDisplay
        d.getSize(size)
        return size
    }

    // Image Utils
    //
    //Rotate Bitmap
    @JvmStatic
    fun rotate(bmp: Bitmap, degrees: Float): Bitmap {
        var b = bmp
        if (degrees != 0f) {
            val m = Matrix()
            m.setRotate(
                degrees, b.width.toFloat() / 2,
                b.height.toFloat() / 2
            )
            val b2 = Bitmap.createBitmap(
                b, 0, 0, b.width,
                b.height, m, true
            )
            if (b != b2) {
                b.recycle()
                b = b2
            }
        }
        return b
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        val ret = Bitmap.createBitmap(w, h, bitmap.config)
        val canvas = Canvas(ret)
        canvas.drawBitmap(bitmap, -rect.left.toFloat(), -rect.top.toFloat(), null)
        bitmap.recycle()
        return ret
    }

    @JvmStatic
    fun cropFace(face: FaceModel, bitmap: Bitmap, rotate: Int): Bitmap {
        var bmp: Bitmap
        val eyesDis = face.eyesDistance()
        val mid = PointF()
        face.getMidPoint(mid)
        val rect = Rect(
            (mid.x - eyesDis * 1.20f).toInt(),
            (mid.y - eyesDis * 0.55f).toInt(),
            (mid.x + eyesDis * 1.20f).toInt(),
            (mid.y + eyesDis * 1.85f).toInt()
        )
        var config: Bitmap.Config? = Bitmap.Config.RGB_565
        if (bitmap.config != null) config = bitmap.config
        bmp = bitmap.copy(config, true)
        when (rotate) {
            90 -> bmp = rotate(bmp, 90f)
            180 -> bmp = rotate(bmp, 180f)
            270 -> bmp = rotate(bmp, 270f)
        }
        bmp = cropBitmap(bmp, rect)
        return bmp
    }
}