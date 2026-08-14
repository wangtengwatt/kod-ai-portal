package com.kod.suanbao

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 蒜宝桌面宠物自定义 View（App 内飘浮版）。
 *
 * 核心能力：
 * 1. Canvas 手绘蒜宝（蒜体/尖芽/眼睛/嘴/腮红），无图片依赖
 * 2. 状态机：idle / thinking / success / sad / peeking / hiding
 * 3. 随机漂浮 + 边缘躲避：靠近屏幕边缘自动切换探头/收起姿态，半身裁切
 *
 * 用法：直接在 XML 布局里放 <com.kod.suanbao.SuanbaoView />，
 * 或用代码 new SuanbaoView(context) 加进 ViewGroup。
 * 父容器需给足够大的空间让蒜宝漂移。
 */
class SuanbaoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /* ------------------------------------------------------------------ */
    /*  配置常量                                                          */
    /* ------------------------------------------------------------------ */

    companion object {
        /** 蒜宝基准半径（dp），实际尺寸 = radius * 2 */
        private const val BASE_RADIUS_DP = 48f

        /** 漂浮速度（px/秒），每秒移动多少像素 */
        private const val DRIFT_SPEED_PX_PER_SEC = 60f

        /** 漂浮方向改变间隔（毫秒） */
        private const val DIRECTION_CHANGE_INTERVAL_MS = 3500L

        /** 进入探头姿态的距离阈值（蒜宝中心距边缘 < 此值时探头），单位 dp */
        private const val PEEK_THRESHOLD_DP = 70f

        /** 进入收起姿态的距离阈值（蒜宝中心距边缘 < 此值时收起），单位 dp */
        private const val HIDE_THRESHOLD_DP = 35f

        /** 思考状态头顶点的跳动周期（毫秒） */
        private const val THINKING_DOT_PERIOD_MS = 900L
    }

    /* ------------------------------------------------------------------ */
    /*  状态                                                              */
    /* ------------------------------------------------------------------ */

    /** 当前业务状态（由外部 setSuanbaoState 驱动） */
    private var businessState: SuanbaoState = SuanbaoState.IDLE

    /** 当前实际渲染状态（可能因边缘躲避覆盖为 peeking/hiding） */
    private val renderState: SuanbaoState
        get() {
            // 边缘姿态优先级最高，覆盖业务状态
            // 但 sad 时蒜宝下沉，不切换边缘姿态，保持情绪表达
            val edge = computeEdgeState()
            return when {
                businessState == SuanbaoState.SAD -> businessState
                edge != null -> edge
                else -> businessState
            }
        }

    /* ------------------------------------------------------------------ */
    /*  漂浮动画                                                          */
    /* ------------------------------------------------------------------ */

    /** 当前蒜宝中心 X（相对 View 左上，px） */
    private var centerX: Float = 0f
    /** 当前蒜宝中心 Y */
    private var centerY: Float = 0f

    /** 漂浮方向角度（弧度） */
    private var driftAngle: Float = 0f
    /** 上次方向改变时间戳 */
    private var lastDirChangeMs: Long = 0L
    /** 上次 onDraw 时间戳（用于计算 dt） */
    private var lastFrameMs: Long = 0L

    /** 漂浮的上下浮动相位（让漂浮有"呼吸感"） */
    private var bobPhase: Float = 0f

    /** ValueAnimator 驱动 onDraw 重绘 */
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { invalidate() }
    }

    /* ------------------------------------------------------------------ */
    /*  Paint 缓存                                                        */
    /* ------------------------------------------------------------------ */

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#97C459")
        style = Paint.Style.FILL
    }
    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B6D11")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val sproutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B6D11")
        style = Paint.Style.FILL
    }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val eyePupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#173404")
        style = Paint.Style.FILL
    }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF9F27")
        alpha = 128
        style = Paint.Style.FILL
    }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#27500A")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thinkingDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#534AB7")
        style = Paint.Style.FILL
    }

    /* ------------------------------------------------------------------ */
    /*  尺寸                                                              */
    /* ------------------------------------------------------------------ */

    private val density: Float get() = resources.displayMetrics.density
    private val radiusPx: Float get() = BASE_RADIUS_DP * density
    private val peekThresholdPx: Float get() = PEEK_THRESHOLD_DP * density
    private val hideThresholdPx: Float get() = HIDE_THRESHOLD_DP * density

    /* ------------------------------------------------------------------ */
    /*  公开 API                                                          */
    /* ------------------------------------------------------------------ */

    /** 设置业务状态（idle/thinking/success/sad）。边缘姿态由 View 自动管理。 */
    fun setSuanbaoState(state: SuanbaoState) {
        businessState = state
        invalidate()
    }

    /** 开始飘浮动画。在 Activity 的 onResume 或 onViewAttached 时调。 */
    fun startFloating() {
        if (!animator.isStarted) animator.start()
        lastFrameMs = System.currentTimeMillis()
    }

    /** 停止飘浮。在 onPause/onViewDetached 时调，节省电量。 */
    fun stopFloating() {
        animator.cancel()
    }

    /* ------------------------------------------------------------------ */
    /*  生命周期                                                          */
    /* ------------------------------------------------------------------ */

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 初始位置：居中略偏上
        centerX = w / 2f
        centerY = h / 2f
        // 初始随机方向
        driftAngle = (Random.nextFloat() * Math.PI * 2).toFloat()
        lastDirChangeMs = System.currentTimeMillis()
        lastFrameMs = System.currentTimeMillis()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startFloating()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFloating()
    }

    /* ------------------------------------------------------------------ */
    /*  漂浮 + 边缘检测                                                   */
    /* ------------------------------------------------------------------ */

    /** 计算当前是否应进入边缘姿态，返回 PEEKING / HIDING / null */
    private fun computeEdgeState(): SuanbaoState? {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return null

        val r = radiusPx
        val leftDist = centerX - r    // 蒜宝左边缘到屏幕左边距离（负=越界）
        val rightDist = w - centerX - r
        val topDist = centerY - r
        val bottomDist = h - centerY - r

        val minDist = minOf(leftDist, rightDist, topDist, bottomDist)

        return when {
            minDist < hideThresholdPx -> SuanbaoState.HIDING
            minDist < peekThresholdPx -> SuanbaoState.PEEKING
            else -> null
        }
    }

    /** 更新漂浮位置（每帧调） */
    private fun updateDrift(dtMs: Long) {
        val now = System.currentTimeMillis()

        // 定期改变方向
        if (now - lastDirChangeMs > DIRECTION_CHANGE_INTERVAL_MS) {
            driftAngle = (Random.nextFloat() * Math.PI * 2).toFloat()
            lastDirChangeMs = now
        }

        // 呼吸相位
        bobPhase += dtMs / 1000f * 2f // 2 rad/s

        val dtSec = dtMs / 1000f
        val speed = DRIFT_SPEED_PX_PER_SEC * density
        val dx = cos(driftAngle) * speed * dtSec
        val dy = sin(driftAngle) * speed * dtSec + sin(bobPhase) * 0.5f

        centerX += dx
        centerY += dy

        // 边界反弹（防止飞出太远，但允许略微越界以触发边缘姿态）
        val r = radiusPx
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = r * 0.6f // 允许蒜宝越过边缘 40%，触发 peeking/hiding

        if (centerX < -margin || centerX > w + margin) {
            driftAngle = (Math.PI - driftAngle).toFloat()
            centerX = centerX.coerceIn(-margin, w + margin)
        }
        if (centerY < -margin || centerY > h + margin) {
            driftAngle = (-driftAngle).toFloat()
            centerY = centerY.coerceIn(-margin, h + margin)
        }
    }

    /* ------------------------------------------------------------------ */
    /*  onDraw                                                            */
    /* ------------------------------------------------------------------ */

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 16L else now - lastFrameMs
        lastFrameMs = now

        updateDrift(dt)

        val state = renderState
        val r = radiusPx

        // 漂浮的上下偏移（呼吸感），sad 时下沉
        val bobOffset = when (state) {
            SuanbaoState.SAD -> sin(bobPhase) * 3f + 8f // 下沉
            SuanbaoState.HIDING -> 0f
            else -> sin(bobPhase) * 4f
        }

        val cx = centerX
        val cy = centerY + bobOffset

        // 根据状态决定光晕
        drawGlow(canvas, cx, cy, r, state)

        // 根据状态裁切 + 绘制蒜宝
        drawSuanbao(canvas, cx, cy, r, state, now)
    }

    /* ------------------------------------------------------------------ */
    /*  绘制：光晕                                                         */
    /* ------------------------------------------------------------------ */

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, r: Float, state: SuanbaoState) {
        val glowColor = when (state) {
            SuanbaoState.SUCCESS -> Color.parseColor("#1D9E75")
            SuanbaoState.SAD -> Color.parseColor("#378ADD")
            SuanbaoState.THINKING -> Color.parseColor("#534AB7")
            else -> return
        }
        glowPaint.color = glowColor
        glowPaint.alpha = 40
        canvas.drawCircle(cx, cy, r * 1.3f, glowPaint)
        glowPaint.alpha = 20
        canvas.drawCircle(cx, cy, r * 1.6f, glowPaint)
    }

    /* ------------------------------------------------------------------ */
    /*  绘制：蒜宝主体                                                     */
    /* ------------------------------------------------------------------ */

    private fun drawSuanbao(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        state: SuanbaoState,
        now: Long,
    ) {
        // 边缘姿态：裁切，只画露出部分
        val w = width.toFloat()
        val h = height.toFloat()
        when (state) {
            SuanbaoState.PEEKING -> {
                // 保留原始裁切：让蒜宝半身可见
                val clipRect = RectF(
                    maxOf(0f, cx - r * 1.5f),
                    maxOf(0f, cy - r * 1.5f),
                    minOf(w, cx + r * 1.5f),
                    minOf(h, cy + r * 1.5f),
                )
                canvas.save()
                canvas.clipRect(clipRect)
                drawFullSuanbao(canvas, cx, cy, r, state, now, peeking = true)
                canvas.restore()
                return
            }
            SuanbaoState.HIDING -> {
                // 几乎只露尖芽
                canvas.save()
                val clipRect = RectF(
                    maxOf(0f, cx - r * 0.4f),
                    maxOf(0f, cy - r * 1.2f),
                    minOf(w, cx + r * 0.4f),
                    minOf(h, cy + r * 1.2f),
                )
                canvas.clipRect(clipRect)
                drawFullSuanbao(canvas, cx, cy, r, state, now, peeking = false)
                canvas.restore()
                return
            }
            else -> {
                drawFullSuanbao(canvas, cx, cy, r, state, now, peeking = false)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  绘制：完整蒜宝                                                     */
    /* ------------------------------------------------------------------ */

    private fun drawFullSuanbao(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        state: SuanbaoState,
        now: Long,
        peeking: Boolean,
    ) {
        // 蒜体（椭圆，略高）
        val bodyRect = RectF(cx - r, cy - r * 1.1f, cx + r, cy + r * 1.1f)
        canvas.drawOval(bodyRect, bodyPaint)
        canvas.drawOval(bodyRect, bodyStrokePaint)

        // 尖芽
        val sproutPath = Path().apply {
            moveTo(cx - r * 0.08f, cy - r * 1.1f)
            quadTo(cx, cy - r * 1.5f, cx + r * 0.08f, cy - r * 1.1f)
        }
        canvas.drawPath(sproutPath, sproutPaint)

        // 眼睛
        val eyeOffsetX = r * 0.33f
        val eyeY = cy - r * 0.15f
        val eyeRx = r * 0.13f
        val eyeRy = when (state) {
            SuanbaoState.HIDING -> r * 0.04f // 眯成线
            else -> r * 0.17f
        }

        when {
            peeking -> {
                // 只画靠近屏幕内侧的那只眼
                // 判断哪边是内侧
                val innerEyeX = if (cx < width / 2f) cx + eyeOffsetX else cx - eyeOffsetX
                drawEye(canvas, innerEyeX, eyeY, eyeRx, eyeRy, state)
            }
            state == SuanbaoState.HIDING -> {
                // 两眼眯成线
                drawEye(canvas, cx - eyeOffsetX, eyeY, eyeRx, eyeRy, state)
                drawEye(canvas, cx + eyeOffsetX, eyeY, eyeRx, eyeRy, state)
            }
            else -> {
                drawEye(canvas, cx - eyeOffsetX, eyeY, eyeRx, eyeRy, state)
                drawEye(canvas, cx + eyeOffsetX, eyeY, eyeRx, eyeRy, state)
            }
        }

        // 嘴
        val mouthY = cy + r * 0.3f
        val mouthPath = Path()
        when (state) {
            SuanbaoState.SAD -> {
                // 嘴角下垂
                mouthPath.moveTo(cx - r * 0.25f, mouthY + r * 0.1f)
                mouthPath.quadTo(cx, mouthY - r * 0.1f, cx + r * 0.25f, mouthY + r * 0.1f)
            }
            SuanbaoState.HIDING -> {
                // 抿嘴
                mouthPath.moveTo(cx - r * 0.15f, mouthY)
                mouthPath.quadTo(cx, mouthY + r * 0.05f, cx + r * 0.15f, mouthY)
            }
            SuanbaoState.PEEKING -> {
                // 嘴角微翘
                mouthPath.moveTo(cx - r * 0.05f, mouthY)
                mouthPath.quadTo(cx + r * 0.1f, mouthY + r * 0.1f, cx + r * 0.2f, mouthY - r * 0.02f)
            }
            else -> {
                // 笑嘴
                mouthPath.moveTo(cx - r * 0.25f, mouthY - r * 0.05f)
                mouthPath.quadTo(cx, mouthY + r * 0.2f, cx + r * 0.25f, mouthY - r * 0.05f)
            }
        }
        canvas.drawPath(mouthPath, mouthPaint)

        // 腮红（sad/hiding 不画）
        if (state != SuanbaoState.SAD && state != SuanbaoState.HIDING) {
            val blushR = r * 0.12f
            val blushOffsetX = r * 0.55f
            val blushY = cy + r * 0.15f
            if (!peeking || cx < width / 2f) {
                canvas.drawCircle(cx + blushOffsetX, blushY, blushR, blushPaint)
            }
            if (!peeking || cx >= width / 2f) {
                canvas.drawCircle(cx - blushOffsetX, blushY, blushR, blushPaint)
            }
        }

        // 思考状态头顶三点
        if (state == SuanbaoState.THINKING) {
            drawThinkingDots(canvas, cx, cy - r * 1.4f, r, now)
        }
    }

    /* ------------------------------------------------------------------ */
    /*  绘制：单只眼睛                                                     */
    /* ------------------------------------------------------------------ */

    private fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        rx: Float,
        ry: Float,
        state: SuanbaoState,
    ) {
        if (state == SuanbaoState.HIDING) {
            // 眯眼：画一条线
            canvas.drawLine(x - rx, y, x + rx, y, eyePupilPaint.apply { strokeWidth = 2f })
            return
        }
        // 眼白
        canvas.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), eyeWhitePaint)
        // 瞳孔
        val pupilR = minOf(rx, ry) * 0.5f
        canvas.drawCircle(x, y + ry * 0.1f, pupilR, eyePupilPaint)
    }

    /* ------------------------------------------------------------------ */
    /*  绘制：思考状态头顶跳动点                                           */
    /* ------------------------------------------------------------------ */

    private fun drawThinkingDots(canvas: Canvas, cx: Float, topY: Float, r: Float, now: Long) {
        val dotR = r * 0.06f
        val spacing = r * 0.22f
        for (i in 0 until 3) {
            val phase = ((now % THINKING_DOT_PERIOD_MS).toFloat() / THINKING_DOT_PERIOD_MS)
            val offset = (i + 1) / 3f
            // 三点依次跳动
            val t = (phase + offset) % 1f
            val bounce = sin(t * Math.PI).toFloat() // 0→1→0
            val y = topY - bounce * r * 0.25f
            val alpha = (255 * (0.4f + 0.6f * bounce)).toInt()
            thinkingDotPaint.alpha = alpha
            canvas.drawCircle(cx + (i - 1) * spacing, y, dotR, thinkingDotPaint)
        }
    }
}
