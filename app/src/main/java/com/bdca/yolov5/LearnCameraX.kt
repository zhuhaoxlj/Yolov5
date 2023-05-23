package com.bdca.yolov5

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.bdca.yolov5.databinding.ActivityMainBinding
import com.bdca.yolov5.utils.ImageProcess
import com.google.common.util.concurrent.ListenableFuture

/**
 *
 *
 * @author MarkGosling
 * @date 2023/5/23 16:43
 * @description: description
 * @version 1.0.0
 * @modified by markgosling on 2023/5/23 16:43
 **/
class LearnCameraX : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var binding: ActivityMainBinding
    private var imageProcess = ImageProcess()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
        setContentView(binding.root)
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val preview: Preview = Preview.Builder()
            .setTargetResolution(Size(screenWidth, screenHeight))
            .build()
        val cameraSelector: CameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        preview.setSurfaceProvider(binding.cameraPreviewMatch.surfaceProvider)
        var camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
        val imageAnalysis = ImageAnalysis.Builder()
            // enable the following line if RGBA output is needed.
            // .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            // insert your code here.


            imageProxy.setCropRect(Rect(0, 0, imageProxy.width, imageProxy.height))
            Log.i("rotation", imageProxy.imageInfo.rotationDegrees.toString() + "")
            val previewHeight = 2560
            val previewWidth = 1536
            // 边框画笔
            val boxPaint = Paint()
            boxPaint.strokeWidth = 5f
            boxPaint.style = Paint.Style.STROKE
            // 字体画笔
            val textPain = Paint()
            textPain.textSize = 50f
            textPain.style = Paint.Style.FILL
            // 圆点画笔
            val pointPain = Paint()
            pointPain.strokeWidth = 15f
            pointPain.color = Color.RED
            pointPain.style = Paint.Style.FILL
            // 这里 Observable 将 image analyse 的逻辑放到子线程计算, 渲染UI的时候再拿回来对应的数据, 避免前端UI卡顿
            val start = System.currentTimeMillis()
            val yuvBytes = arrayOfNulls<ByteArray>(3)
            val planes = imageProxy.planes
            val imageHeight = imageProxy.height
            val imageWidth = imageProxy.width
            imageProcess.fillBytes(planes, yuvBytes)
            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            val rgbBytes = IntArray(imageHeight * imageWidth)
            imageProcess.YUV420ToARGB8888(
                yuvBytes[0],
                yuvBytes[1],
                yuvBytes[2],
                imageWidth,
                imageHeight,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                rgbBytes
            )
            // 原图 bitmap
            val imageBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
            imageBitmap.setPixels(rgbBytes, 0, imageWidth, 0, 0, imageWidth, imageHeight)
            val scaleX = previewWidth.toFloat() / imageWidth
            val scaleY = previewHeight.toFloat() / imageHeight
            val matrix = Matrix()
            matrix.setScale(scaleX, scaleY)
            // 模型输入的 bitmap
            val previewToModelTransform = imageProcess.getTransformationMatrix(
                previewWidth,
                previewHeight,
                640,
                640,
                imageProxy.imageInfo.rotationDegrees,
                false
            )
            val modelInputBitmap = Bitmap.createBitmap(
                imageBitmap,
                0,
                0,
                imageBitmap.width,
                imageBitmap.height,
                previewToModelTransform,
                false
            )
            runOnUiThread {
                binding.boxLabelCanvas.setImageBitmap(modelInputBitmap)
            }
            // after done, release the ImageProxy object
            imageProxy.close()
        }

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
    }
}