package com.bdca.yolov5

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bdca.yolov5.R
import com.bdca.yolov5.databinding.ActivityScoreBinding
import com.xuexiang.xui.utils.StatusBarUtils
import com.xuexiang.xui.widget.progress.CircleProgressView.CircleProgressUpdateListener
import com.xuexiang.xui.widget.progress.HorizontalProgressView.HorizontalProgressUpdateListener
import java.util.Random

/**
 * @author MarkGosling
 * @version 1.0.0
 * @date 2023/5/19 09:33
 * @description: description
 * @modified by markgosling on 2023/5/19 09:33
 */
class ScoreActivity : AppCompatActivity(), HorizontalProgressUpdateListener,
    CircleProgressUpdateListener {
    private var binding: ActivityScoreBinding? = null

    /**
     * 均值
     */
    var mean = 75.0

    /**
     * 标准差
     */
    var stdDev = 7.5
    var random = Random()

    /**
     * 生成正态分布随机数
     */
    var randomNumber1 = random.nextGaussian() * stdDev + mean
    var randomNumber2 = random.nextGaussian() * stdDev + mean
    var randomNumber3 = random.nextGaussian() * stdDev + mean
    var randomNumber4 = random.nextGaussian() * stdDev + mean
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoreBinding.inflate(layoutInflater)
        StatusBarUtils.fullScreen(this)
        val number1 = Math.max(60.0, Math.min(90.0, randomNumber1)).toFloat()
        val number2 = Math.max(60.0, Math.min(90.0, randomNumber2)).toFloat()
        val number3 = Math.max(60.0, Math.min(90.0, randomNumber3)).toFloat()
        val number4 = Math.max(60.0, Math.min(90.0, randomNumber4)).toFloat()
        binding!!.hpvLanguage.setEndProgress(number1)
        binding!!.hpvMath.setEndProgress(number2)
        binding!!.hpvHistory.setEndProgress(number3)
        binding!!.hpvEnglish.setEndProgress(number4)
        binding!!.hpvLanguage.setProgressViewUpdateListener(this)
        binding!!.hpvMath.setProgressViewUpdateListener(this)
        binding!!.hpvHistory.setProgressViewUpdateListener(this)
        binding!!.hpvEnglish.setProgressViewUpdateListener(this)
        binding!!.progressViewCircleMain.setEndProgress((number1 + number2 + number3 + number4) / 4)
        binding!!.progressViewCircleMain.setGraduatedEnabled(true)
        binding!!.progressViewCircleMain.setProgressViewUpdateListener(this)
        binding!!.hpvLanguage.startProgressAnimation()
        binding!!.hpvMath.startProgressAnimation()
        binding!!.hpvHistory.startProgressAnimation()
        binding!!.hpvEnglish.startProgressAnimation()
        binding!!.btnStart.setOnClickListener { v: View? -> finish() }
        setContentView(binding!!.root)
    }

    override fun onCircleProgressStart(view: View) {}

    /**
     * 进度条更新中
     *
     * @param view
     * @param progress
     */
    override fun onCircleProgressUpdate(view: View, progress: Float) {
        val progressInt = progress.toInt()
        if (view.id == R.id.progressView_circle_main) {
            binding!!.progressTextMain.text = progressInt.toString() + ""
        }
    }

    override fun onCircleProgressFinished(view: View) {}

    /**
     * 进度条更新结束
     *
     * @param view
     */
    override fun onHorizontalProgressFinished(view: View) {
        if (view.id == R.id.hpv_english) {
            binding!!.progressViewCircleMain.startProgressAnimation()
        }
    }

    /**
     * 进度条开始更新
     *
     * @param view
     */
    override fun onHorizontalProgressStart(view: View) {}

    /**
     * 进度条更新中
     *
     * @param view
     * @param progress
     */
    override fun onHorizontalProgressUpdate(view: View, progress: Float) {
        val progressInt = progress.toInt()
        when (view.id) {
            R.id.hpv_language -> binding!!.progressTextLanguage.text = "$progressInt%"
            R.id.hpv_english -> binding!!.progressTextEnglish.text = "$progressInt%"
            R.id.hpv_history -> binding!!.progressTextHistory.text = "$progressInt%"
            R.id.hpv_math -> binding!!.progressTextMath.text = "$progressInt%"
            else -> {}
        }
    }
}