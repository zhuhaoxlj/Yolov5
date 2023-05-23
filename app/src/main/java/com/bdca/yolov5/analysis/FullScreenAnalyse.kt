package com.bdca.yolov5.analysis

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.media.Image
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.bdca.yolov5.MainActivity
import com.bdca.yolov5.MainActivity.Companion.setDLength
import com.bdca.yolov5.MainActivity.Companion.setDVariance
import com.bdca.yolov5.MainActivity.Companion.setNLength
import com.bdca.yolov5.MainActivity.Companion.setNVariance
import com.bdca.yolov5.detector.Yolov5TFLiteDetector
import com.bdca.yolov5.utils.ImageProcess
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt

/**
 * @author markgosling
 */
class FullScreenAnalyse(
    context: Context?,
    private var previewView: PreviewView,
    private var boxLabelCanvas: ImageView,
    var rotation: Int,
    private val inferenceTimeTextView: TextView,
    private val frameSizeTextView: TextView,
    yolov5TFLiteDetector: Yolov5TFLiteDetector
) : ImageAnalysis.Analyzer {
    class Result(
        var costTime: Long,
        var bitmap: Bitmap,
        var nVariance: Float,
        var dVariance: Float,
        var nLength: Float,
        var dLength: Float
    )

    var nVariance = 0f
    var dVariance = 0f
    var nLength = 0f
    var dLength = 0f

    // 过滤异常点阈值
    var POINT_THRESHOLD = 500
    var maxLength = 15
    var noResultFrameCount = 0
    var imageProcess: ImageProcess
    private val yolov5TFLiteDetector: Yolov5TFLiteDetector

    // 在类的范围内创建一个集合来保存历史圆点的坐标
    var nPointList = ArrayList<PointF>()
    var dPointList = ArrayList<PointF>()
    var p1_mean_x = 0.0
    var p1_mean_y = 0.0
    var p2_mean_x = 0.0
    var p2_mean_y = 0.0
    var longTermPointList1 = ArrayList<PointF>()
    var longTermPointList2 = ArrayList<PointF>()

    init {
        imageProcess = ImageProcess()
        this.yolov5TFLiteDetector = yolov5TFLiteDetector
    }

    @SuppressLint("CheckResult")
    override fun analyze(image: ImageProxy) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = processImage(image)
            withContext(Dispatchers.Main) {
                val previewHeight = previewView.height
                val previewWidth = previewView.width
                boxLabelCanvas.setImageBitmap(result.bitmap)
                frameSizeTextView.text = previewHeight.toString() + "x" + previewWidth
                inferenceTimeTextView.text = result.costTime.toString() + "ms"
                setNVariance(result.nVariance.toString() + "")
                setDVariance(result.dVariance.toString() + "")
                setNLength(result.nLength.toString() + "")
                setDLength(result.dLength.toString() + "")
            }
        }
    }

    private suspend fun processImage(image: ImageProxy): Result = suspendCoroutine { continuation ->
        image.setCropRect(Rect(0, 0, image.width, image.height))
        Log.i("rotation", image.imageInfo.rotationDegrees.toString() + "")
        val previewHeight = previewView.height
        val previewWidth = previewView.width
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
        val planes = image.planes
        val imageHeight = image.height
        val imageWidth = image.width
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
            yolov5TFLiteDetector.inputSize.width,
            yolov5TFLiteDetector.inputSize.height,
            image.imageInfo.rotationDegrees,
            false
        )
        val previewToModelTransform2 = imageProcess.getTransformationMatrix(
            previewWidth,
            previewHeight,
            yolov5TFLiteDetector.inputSize.width,
            yolov5TFLiteDetector.inputSize.height,
            0,
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
        val modelToPreviewTransform = Matrix()
        previewToModelTransform2.invert(modelToPreviewTransform)
        val recognitions = yolov5TFLiteDetector.detect(modelInputBitmap)
        val emptyCropSizeBitmap =
            Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val cropCanvas = Canvas(emptyCropSizeBitmap)
        if (recognitions.size == 0) {
            noResultFrameCount++
        }
        if (noResultFrameCount == 30) {
            noResultFrameCount = 0
            nPointList.clear()
            longTermPointList1.clear()
            dPointList.clear()
            longTermPointList2.clear()
            nLength = 0f
            dLength = 0f
            nVariance = 0f
            dVariance = 0f
        }
        for (res in recognitions) {
            val location = res?.location
            val label = res?.labelName
            val confidence = res?.confidence
            modelToPreviewTransform.mapRect(location)
            // 保存当前圆点的坐标
            val currentPoint = PointF(location!!.centerX(), location.centerY())
            if (res.labelId == 0) {
                if (MainActivity.isDrawRect) {
                    boxPaint.color = Color.parseColor("#ff991f")
                    cropCanvas.drawRect(location, boxPaint)
                    textPain.color = Color.parseColor("#ff991f")
                    cropCanvas.drawText(
                        label + ":" + String.format("%.2f", confidence),
                        location.left,
                        location.top - 15,
                        textPain
                    )
                }
                longTermPointList1.add(currentPoint)
                // 计算平均中心点坐标
                p1_mean_x = 0.0
                p1_mean_y = 0.0
                for (point in longTermPointList1) {
                    p1_mean_x += point.x.toDouble()
                    p1_mean_y += point.y.toDouble()
                }
                p1_mean_x /= longTermPointList1.size.toDouble()
                p1_mean_y /= longTermPointList1.size.toDouble()
                Log.i("point", "x:" + p1_mean_x + "_" + "y:" + p1_mean_y)
                // 计算每个中心点到平均中心点的距离之差的平方和
                for (point in longTermPointList1) {
                    val diff_x = point.x - p1_mean_x
                    val diff_y = point.y - p1_mean_y
                    nVariance += (diff_x * diff_x + diff_y * diff_y).toFloat()
                }
                nVariance /= longTermPointList1.size.toFloat()
                if (distanceBetweenPoints(
                        currentPoint.x.toDouble(),
                        currentPoint.y.toDouble(),
                        p1_mean_x,
                        p1_mean_y
                    ) < POINT_THRESHOLD
                ) {
                    // 绘制圆点
                    cropCanvas.drawPoint(currentPoint.x, currentPoint.y, pointPain)
                    addNewPoint2N(currentPoint)
                    if (nPointList.size > 1) {
                        val temp = nPointList[nPointList.size - 2]
                        nLength += distanceBetweenPoints(
                            currentPoint.x.toDouble(),
                            currentPoint.y.toDouble(),
                            temp.x.toDouble(),
                            temp.y.toDouble()
                        ).toFloat()
                    }
                } else {
                    longTermPointList1.removeAt(longTermPointList1.size - 1)
                }
            } else {
                if (MainActivity.isDrawRect) {
                    boxPaint.color = Color.parseColor("#00be76")
                    cropCanvas.drawRect(location, boxPaint)
                    textPain.color = Color.parseColor("#00be76")
                    cropCanvas.drawText(
                        label + ":" + String.format("%.2f", confidence),
                        location.left,
                        location.top - 15,
                        textPain
                    )
                }
                longTermPointList2.add(currentPoint)
                // 计算平均中心点坐标
                p2_mean_x = 0.0
                p2_mean_y = 0.0
                for (point in longTermPointList2) {
                    p2_mean_x += point.x.toDouble()
                    p2_mean_y += point.y.toDouble()
                }
                p2_mean_x /= longTermPointList2.size.toDouble()
                p2_mean_y /= longTermPointList2.size.toDouble()
                Log.i("point", "x:" + p2_mean_x + "_" + "y:" + p2_mean_y)
                // 计算每个中心点到平均中心点的距离之差的平方和
                for (point in longTermPointList2) {
                    val diff_x = point.x - p2_mean_x
                    val diff_y = point.y - p2_mean_y
                    dVariance += (diff_x * diff_x + diff_y * diff_y).toFloat()
                }
                dVariance /= longTermPointList2.size.toFloat()
                if (distanceBetweenPoints(
                        currentPoint.x.toDouble(),
                        currentPoint.y.toDouble(),
                        p2_mean_x,
                        p2_mean_y
                    ) < POINT_THRESHOLD
                ) {
                    // 绘制圆点
                    cropCanvas.drawPoint(currentPoint.x, currentPoint.y, pointPain)
                    addNewPoint2D(currentPoint)
                    if (dPointList.size > 1) {
                        val temp = dPointList[dPointList.size - 2]
                        dLength += distanceBetweenPoints(
                            currentPoint.x.toDouble(),
                            currentPoint.y.toDouble(),
                            temp.x.toDouble(),
                            temp.y.toDouble()
                        ).toFloat()
                    }
                } else {
                    longTermPointList2.removeAt(longTermPointList2.size - 1)
                }
            }
            // 绘制圆点
            cropCanvas.drawPoint(currentPoint.x, currentPoint.y, pointPain)
        }
        // 绘制连线
        if (nPointList.size > 1) {
            val linePaint = Paint()
            linePaint.strokeWidth = 5f
            linePaint.color = Color.parseColor("#00b5ef")
            linePaint.style = Paint.Style.STROKE
            val linePath = Path()
            linePath.moveTo(nPointList[0].x, nPointList[0].y)
            for (i in 1 until nPointList.size) {
                linePath.lineTo(nPointList[i].x, nPointList[i].y)
            }
            cropCanvas.drawPath(linePath, linePaint)
        }
        if (dPointList.size > 1) {
            val linePaint = Paint()
            linePaint.strokeWidth = 5f
            linePaint.color = Color.WHITE
            linePaint.style = Paint.Style.STROKE
            val linePath = Path()
            linePath.moveTo(dPointList[0].x, dPointList[0].y)
            for (i in 1 until dPointList.size) {
                linePath.lineTo(dPointList[i].x, dPointList[i].y)
            }
            cropCanvas.drawPath(linePath, linePaint)
        }
        val end = System.currentTimeMillis()
        val costTime = end - start
        image.close()

        continuation.resume(
            Result(
                costTime,
                emptyCropSizeBitmap,
                nVariance,
                dVariance,
                nLength,
                dLength
            )
        )
    }

    fun reset() {
        nPointList.clear()
        longTermPointList1.clear()
        dPointList.clear()
        longTermPointList2.clear()
        nLength = 0f
        dLength = 0f
        nVariance = 0f
        dVariance = 0f
    }

    private fun addNewPoint2N(newPoint: PointF) {
        // 添加新元素到列表末尾
        nPointList.add(newPoint)
        if (nPointList.size > maxLength) {
            // 删除最旧的元素（列表开头的元素）
            nPointList.removeAt(0)
        }
    }


    private fun addNewPoint2D(newPoint: PointF) {
        // 添加新元素到列表末尾
        dPointList.add(newPoint)
        if (dPointList.size > maxLength) {
            // 删除最旧的元素（列表开头的元素）
            dPointList.removeAt(0)
        }
    }

    companion object {
        fun distanceBetweenPoints(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val dx = x2 - x1
            val dy = y2 - y1
            return sqrt(dx * dx + dy * dy)
        }
    }
}