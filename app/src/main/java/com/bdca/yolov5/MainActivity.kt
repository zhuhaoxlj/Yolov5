package com.bdca.yolov5

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.view.PreviewView
import com.bdca.yolov5.analysis.FullScreenAnalyse
import com.bdca.yolov5.databinding.ActivityMainBinding
import com.bdca.yolov5.detector.Yolov5TFLiteDetector
import com.bdca.yolov5.utils.CameraProcess
import com.bdca.yolov5.utils.ImageProcess
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.xuexiang.xui.utils.XToastUtils

/**
 * @author markgosling
 */
class MainActivity : AppCompatActivity() {
    private lateinit var cameraPreviewMatch: PreviewView
    private var boxLabelCanvas: ImageView? = null
    private lateinit var drawRectSwitch: SwitchCompat
    private lateinit var showStatusButton: ImageView
    private var inferenceTimeTextView: TextView? = null
    private var frameSizeTextView: TextView? = null
    private var yolov5TFLiteDetector: Yolov5TFLiteDetector? = null
    private val cameraProcess: CameraProcess = CameraProcess()
    private var binding: ActivityMainBinding? = null
    private lateinit var imageProcess: ImageProcess

    /**
     * 加载模型
     *
     * @param modelName
     */
    private fun initModel(modelName: String) {
        // 加载模型
        try {
            yolov5TFLiteDetector = Yolov5TFLiteDetector()
            yolov5TFLiteDetector!!.modelFile = modelName
            yolov5TFLiteDetector!!.addNNApiDelegate()
            yolov5TFLiteDetector!!.addGpuDelegate()
            yolov5TFLiteDetector!!.initialModel(this)
            Log.i("model", "Success loading model: " + yolov5TFLiteDetector!!.modelFile)
        } catch (e: Exception) {
            Log.e("model", "load model error: " + e.message + e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        requestPermission()
        // 获取窗口的根视图
        val decorView = window.decorView
        // 设置标志位，隐藏状态栏
        val flags = View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = flags
        // 全屏画面
        cameraPreviewMatch = findViewById(R.id.camera_preview_match)
        cameraPreviewMatch.scaleType = PreviewView.ScaleType.FILL_START
        DVarianceTextview = findViewById(R.id.d_variance_value)
        NVarianceTextview = findViewById(R.id.n_variance_value)
        NLengthTextview = findViewById(R.id.n_length_value)
        DLengthTextview = findViewById(R.id.d_length_value)
        val layout = findViewById<LinearLayout>(R.id.layout2)
        val hideStatusButton = findViewById<ImageView>(R.id.hide_status)
        hideStatusButton.setOnClickListener { hideLayoutWithAnimation(layout) }
        showStatusButton = findViewById(R.id.show_status)
        showStatusButton.setOnClickListener {
            showLayoutWithAnimation(
                layout
            )
        }
        // box/label画面
        boxLabelCanvas = findViewById(R.id.box_label_canvas)
        // 沉浸式体验按钮
        drawRectSwitch = findViewById(R.id.immersive)
        // 实时更新的一些view
        inferenceTimeTextView = findViewById(R.id.inference_time)
        frameSizeTextView = findViewById(R.id.frame_size)
        // 申请摄像头权限
        if (!cameraProcess.allPermissionsGranted(this)) {
            cameraProcess.requestPermissions(this)
        }
        // 获取手机摄像头拍照旋转参数
        val rotation = windowManager.defaultDisplay.rotation
        Log.i("image", "rotation: $rotation")
        cameraProcess.showCameraSupportSize(this@MainActivity)
        // 初始化加载yolov5s
        initModel("best model")
        val fullScreenAnalyse = FullScreenAnalyse(
            this@MainActivity,
            cameraPreviewMatch,
            boxLabelCanvas!!,
            rotation,
            inferenceTimeTextView!!,
            frameSizeTextView!!,
            yolov5TFLiteDetector!!
        )
        val resetButton = findViewById<Button>(R.id.reset)
        resetButton.setOnClickListener { fullScreenAnalyse.reset() }
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        cameraProcess.startCamera(
            this@MainActivity,
            fullScreenAnalyse,
            cameraPreviewMatch,
            screenWidth,
            screenHeight
        )
        drawRectSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            isDrawRect = b
        }
        binding!!.score.setOnClickListener {
            val intent = Intent(this@MainActivity, ScoreActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showLayoutWithAnimation(view: View) {
        showStatusButton.visibility = View.INVISIBLE
        // 先将布局设置为可见
        view.visibility = View.VISIBLE
        val animator = ObjectAnimator.ofFloat(view, "translationY", view.height.toFloat(), 0f)
        // 设置动画持续时间，单位为毫秒
        animator.duration = 500
        // 启动动画
        animator.start()
    }

    private fun hideLayoutWithAnimation(view: View) {
        // 先将视图设置为可见
        showStatusButton.visibility = View.VISIBLE
        // 从透明度0.0到1.0的渐变
        val alphaAnimation = AlphaAnimation(0.0f, 1.0f)
        // 设置动画持续时间，单位为毫秒
        alphaAnimation.duration = 1200
        val animator = ObjectAnimator.ofFloat(view, "translationY", 0f, view.height.toFloat())
        // 设置动画持续时间，单位为毫秒
        animator.duration = 500
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 隐藏布局
                view.visibility = View.GONE
            }
        })
        // 启动动画
        animator.start()
        // 启动动画
        showStatusButton.startAnimation(alphaAnimation)
    }

    private fun requestPermission() {
        // request permission
        // 申请单个权限
        XXPermissions.with(this).permission(Permission.MANAGE_EXTERNAL_STORAGE) // 设置权限请求拦截器（局部设置）
            //.interceptor(new PermissionInterceptor())
            // 设置不触发错误检测机制（局部设置）
            //.unchecked()
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                    if (!allGranted) {
                        XToastUtils.warning("获取部分权限成功，但部分权限未正常授予")
                        return
                    }
                    Log.i("request_permission", "获取权限成功")
                }

                override fun onDenied(permissions: List<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        XToastUtils.warning("被永久拒绝授权，请手动授予权限")
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(applicationContext, permissions)
                    } else {
                        Log.i("request_permission", "获取权限失败")
                    }
                }
            })
    }

    companion object {
        private var DVarianceTextview: TextView? = null
        private var NVarianceTextview: TextView? = null
        private var NLengthTextview: TextView? = null
        private var DLengthTextview: TextView? = null

        @JvmField
        var isDrawRect = true

        @JvmStatic
        fun setDVariance(text: String?) {
            DVarianceTextview!!.text = text
        }

        @JvmStatic
        fun setNVariance(text: String?) {
            NVarianceTextview!!.text = text
        }

        @JvmStatic
        fun setDLength(text: String?) {
            DLengthTextview!!.text = text
        }

        @JvmStatic
        fun setNLength(text: String?) {
            NLengthTextview!!.text = text
        }
    }
}