package com.kod.suanbao

/**
 * 蒜宝状态枚举。
 *
 * 基础四态与 Web 端保持一致，新增两个边缘姿态：
 * - [PEEKING]  靠近边缘时探头偷看（半身裁切）
 * - [HIDING]   越界时收起（只露一小角）
 */
enum class SuanbaoState {
    /** 待机：正常漂浮，大眼睛笑 */
    IDLE,

    /** 思考：头顶三个跳动点 */
    THINKING,

    /** 思考成功：绿色光晕 + 笑眼弯弯 */
    SUCCESS,

    /** 难过：蓝色光晕 + 下沉 + 嘴角下垂 */
    SAD,

    /** 靠近边缘：半身躲入，只露一只眼探头偷看 */
    PEEKING,

    /** 越界收起：几乎藏起来，只露尖芽一小角 */
    HIDING;

    /** 是否属于边缘姿态（探头/收起） */
    val isEdgeState: Boolean get() = this == PEEKING || this == HIDING
}
