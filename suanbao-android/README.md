# 蒜宝桌面宠物 - Android 自定义 View

## 文件清单

| 文件 | 说明 |
|------|------|
| `SuanbaoState.kt` | 状态枚举：IDLE / THINKING / SUCCESS / SAD / PEEKING / HIDING |
| `SuanbaoView.kt` | 核心自定义 View，Canvas 手绘 + 漂浮 + 边缘躲避 |
| `activity_suanbao_demo.xml` | Demo 布局示例 |
| `SuanbaoDemoActivity.kt` | Demo Activity，演示状态循环 |

## 接入步骤

### 1. 复制源码

把 `SuanbaoState.kt` 和 `SuanbaoView.kt` 复制到你 Android 项目的源码目录，包名按需改（默认 `com.kod.suanbao`）。

### 2. 放进布局

在你 Activity 的 XML 布局最上层加：

```xml
<com.kod.suanbao.SuanbaoView
    android:id="@+id/suanbao"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

`match_parent` 是必须的——View 需要拿到父容器尺寸做边缘检测。

### 3. 驱动状态

在对话/任务逻辑里调用：

```kotlin
suanbao.setSuanbaoState(SuanbaoState.THINKING)  // 发消息
suanbao.setSuanbaoState(SuanbaoState.SUCCESS)    // 成功
suanbao.setSuanbaoState(SuanbaoState.SAD)        // 失败
suanbao.setSuanbaoState(SuanbaoState.IDLE)       // 待机
```

边缘的 PEEKING / HIDING 由 View 自动管理，无需外部干预。

### 4. 生命周期

```kotlin
override fun onResume() { suanbao.startFloating() }
override fun onPause()  { suanbao.stopFloating() }
```

View 在 attach 时会自动 start，detach 时自动 stop，通常不用手动管。

## 边缘躲避原理

```
                  屏幕中央区
                ← PEEK 阈值 →
              ┌──────────────┐
              │              │
   HIDE 阈值  │   正常漂浮   │
   ←───────→ │   IDLE/...   │
  ┌──┬───────┼──────────────┼───────┬──┐
  │ HIDING │   PEEKING     │ PEEKING│ HIDING │
  └──┴───────┴──────────────┴───────┴──┘
```

- 蒜宝中心距任一边缘 < `PEEK_THRESHOLD`(70dp) → 切 PEEKING，半身裁切，只露内侧一只眼
- 距离 < `HIDE_THRESHOLD`(35dp) → 切 HIDING，几乎只露尖芽
- 越界后边界反弹，蒜宝弹回屏幕内

## 可调参数（SuanbaoView.kt 顶部 companion object）

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `BASE_RADIUS_DP` | 48 | 蒜宝基准半径 |
| `DRIFT_SPEED_PX_PER_SEC` | 60 | 漂浮速度 |
| `DIRECTION_CHANGE_INTERVAL_MS` | 3500 | 方向改变间隔 |
| `PEEK_THRESHOLD_DP` | 70 | 探头触发距离 |
| `HIDE_THRESHOLD_DP` | 35 | 收起触发距离 |

## 依赖

- `androidx.appcompat:appcompat`（Demo Activity 用，View 本身不依赖）
- `androidx.constraintlayout:constraintlayout`（Demo 布局用，可选）

View 本身是纯 Canvas 绘制，无第三方依赖。

## 后续可扩展

- [ ] Lottie 动画替换 Canvas 手绘（更精致的表情过渡）
- [ ] 触摸交互：点蒜宝切换表情、长按拖拽
- [ ] 全局悬浮窗模式（需 `SYSTEM_ALERT_WINDOW` 权限）
- [ ] 与 KOD Web 端状态同步（WebSocket / 轮询）
