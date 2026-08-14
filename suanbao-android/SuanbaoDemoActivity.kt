package com.kod.suanbao

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * 蒜宝 Demo Activity。
 *
 * 演示：
 * 1. SuanbaoView 放进布局后自动飘浮
 * 2. 外部通过 setSuanbaoState() 驱动业务状态变化
 * 3. 边缘躲避由 View 内部自动处理，无需外部干预
 *
 * 在你的真实 App 里，把 SuanbaoView 叠在主界面最上层即可，
 * 业务状态由你的对话/任务逻辑驱动。
 */
class SuanbaoDemoActivity : AppCompatActivity() {

    private lateinit var suanbao: SuanbaoView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suanbao_demo)

        suanbao = findViewById(R.id.suanbao)

        // Demo：循环演示四种业务状态
        demoStateCycle()
    }

    override fun onResume() {
        super.onResume()
        suanbao.startFloating()
    }

    override fun onPause() {
        super.onPause()
        suanbao.stopFloating()
    }

    /** Demo 用：每 4 秒切一个状态，观察蒜宝表情变化 + 边缘躲避 */
    private fun demoStateCycle() {
        val states = listOf(
            SuanbaoState.IDLE,
            SuanbaoState.THINKING,
            SuanbaoState.SUCCESS,
            SuanbaoState.SAD,
        )
        var index = 0
        val cycleRunnable = object : Runnable {
            override fun run() {
                suanbao.setSuanbaoState(states[index % states.size])
                index++
                handler.postDelayed(this, 4000L)
            }
        }
        handler.postDelayed(cycleRunnable, 1000L)
    }

    /*
     * 在你的真实对话 App 里这样用：
     *
     *   // 发消息时
     *   suanbao.setSuanbaoState(SuanbaoState.THINKING)
     *
     *   // 对话成功
     *   suanbao.setSuanbaoState(SuanbaoState.SUCCESS)
     *
     *   // 对话失败
     *   suanbao.setSuanbaoState(SuanbaoState.SAD)
     *
     *   // 恢复待机
     *   suanbao.setSuanbaoState(SuanbaoState.IDLE)
     *
     * 边缘的 peeking/hiding 由 View 自动管理，你不用管。
     */
}
