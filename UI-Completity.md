# CatFrame UI 组件库差异分析报告（全量重扫描）

**基准版本**: Minecraft 26.1.2（deobf 源码）  
**CatFrame 版本**: 0.6.4  
**分析日期**: 2026-08-06  
**说明**: 本次为三视角并行全量重扫描（完整性 / 正确性 / 影响），逐条核验 2026-07-20 版旧报告，修正其中多处过时断言（Checkbox、ImageWidget、MultilineEditBox 等旧报告称"缺失/可暂缓"的组件其实早已实现），并新增代码级 Bug 与兼容性发现。

**第二轮修订（2026-08-06）**: 补齐 S13 全部未核对项（11 项，见 3.2），并对照 1.7.10 原版反混淆源码 `build/tmp/recompSrc/net/minecraft/client/gui/`（69 文件）验证输入时序——**C1/W8 前提被证伪并撤销**，C2/C3 复验成立，新增 W24-W36 与 S14-S22。

---

## 一、总体完成度概览（更新）

| 模块                        | 高版本组件数 | CatFrame 已有 | 完成度   | 说明                                                                     |
| --------------------------- | ------------ | ------------- | -------- | ------------------------------------------------------------------------ |
| 核心接口体系                | 4            | 3             | 75%      | 缺 NarratableEntry                                                       |
| **布局系统（Layout）**      | 10           | 10            | **100%** | ✅ 抽查 API 一致                                                          |
| **Tab 系统**                | 6            | 8（含独有）   | **90%**  | 接口存在，但 visitChildren/doLayout 契约被弱化                           |
| **Toast 系统**              | 6            | 6（含独有）   | **95%**  | 缺 RecipeToast 等具体实现，getToken 语义有偏差                           |
| **基础控件**                | 8            | 8             | **100%** | Checkbox/ImageWidget/StringWidget 均已实现                               |
| **编辑框系**                | 4            | 4             | **85%**  | AbstractEditBox/MultilineEditBox/MultilineTextField 齐全，接口缺口见 W15 |
| **容器控件（滚动/列表）**   | 7            | 7             | **90%**  | SelectionList 缺 swap/nextEntry 等，见 W16                               |
| **事件系统（events/）**     | 4            | 5             | **80%**  | 键盘路径存在 Critical 断链，见 C2；载荷缺口见 W14                        |
| **无障碍朗读（Narration）** | 6            | **0**         | **0%**   | 完全缺失，见 W20                                                         |
| **滑块（Slider）**          | 2            | **0**         | **0%**   | 缺失，见 W21                                                             |
| **高级文本控件**            | 4            | 0             | **0%**   | MultiLineTextWidget 等缺失，见 W22                                       |
| **高级控件**                | ~10          | 0~2           | ~10%     | 见映射表 1.2                                                             |
| **CatFrame 独有**           | 0            | ~10           | —        | Overlay/TabRegistry/九宫格拉伸 等                                        |
| **综合**                    |              |               | **~80%** | 差距集中在 narration、滑块、高级文本控件与事件载荷                       |

---

## 二、组件映射表（当前代码状态）

### 2.1 已对齐/已实现（33 项）

| 高版本 (26.1.2)                                                                       | CatFrame                                             | 对齐度 | 备注                                                                                      |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------- | ------ | ----------------------------------------------------------------------------------------- |
| AbstractButton                                                                        | AbstractButton                                       | 高     | 无键盘激活（Enter/Space 不触发）                                                          |
| AbstractWidget                                                                        | AbstractComponent                                    | 中     | isActive() 语义偏差（W13）；无 tabOrderGroup setter                                       |
| AbstractScrollArea                                                                    | AbstractScrollArea                                   | 高     | 无 defaultSettings() 工厂；滚动条拖动已修复                                               |
| AbstractSelectionList                                                                 | AbstractSelectionList                                | 中     | 缺 swap/nextEntry 等（W16）                                                               |
| AbstractContainerWidget                                                               | AbstractContainerWidget                              | 高     | 结构一致                                                                                  |
| AbstractStringWidget                                                                  | AbstractStringWidget                                 | 高     | 已从 12 行存根变为完整实现                                                                |
| AbstractTextAreaWidget                                                                | AbstractTextAreaWidget                               | 高     | 几乎 1:1                                                                                  |
| Button                                                                                | Button                                               | 高     |                                                                                           |
| Checkbox                                                                              | Checkbox                                             | 高     | 已实现（旧报告称缺失）；无两行标签（S11）                                                 |
| ContainerObjectSelectionList                                                          | ContainerObjectSelectionList                         | 中     | 已核验：Entry.setFocused 状态机错误（W30）                                                |
| CycleButton                                                                           | CyclingButton                                        | 中     | 缺 Shift 反向/resetValue/Alt 值列表（W17）                                                |
| EditBox                                                                               | AbstractEditBox + SimpleEditBox + SearchingBox       | 中     | 缺水平滚动/双击选词等（W15）                                                              |
| ImageWidget                                                                           | ImageWidget                                          | 高     | 已实现（旧报告称可暂缓）                                                                  |
| MultiLineEditBox                                                                      | MultiLineEditBox                                     | 高     | 已实现（旧报告称可暂缓）                                                                  |
| MultilineTextField                                                                    | MultilineTextField                                   | 高     | 自研换行算法替代 Font.splitter                                                            |
| ObjectSelectionList                                                                   | ObjectSelectionList                                  | 中     | 已核验：缺 nextFocusPath/narration（S15）；存根见 W19                                     |
| Renderable                                                                            | Renderable                                           | 高     |                                                                                           |
| ScrollableLayout                                                                      | ScrollableLayout                                     | 高     | 存在内容 Y 错位 Bug（W7）                                                                 |
| StringWidget                                                                          | StringWidget                                         | 中     | 缺 TextOverflow/SCROLLING（S10）                                                          |
| TabButton                                                                             | TabButton                                            | 中     | 已核验：isSelected 状态源不一致（W29）                                                    |
| TabOrderedElement                                                                     | TabOrderedElement                                    | 中     | 无 setTabOrderGroup，全部组件 tab 组恒 0                                                  |
| Tooltip                                                                               | Tooltip                                              | 高     | 无独立 narration 重载                                                                     |
| WidgetSprites                                                                         | WidgetSprites                                        | 高     | 四态精灵与 26.1.2 record 对齐                                                             |
| WidgetTooltipHolder                                                                   | WidgetTooltipHolder                                  | 高     | 缺 updateNarration                                                                        |
| Tab（接口）                                                                           | Tab                                                  | 中     | 契约弱化（W18）                                                                           |
| Toast（接口）                                                                         | Toast                                                | 高     | getToken 默认值语义不同（S12）                                                            |
| GridLayout / LinearLayout / FrameLayout / EqualSpacingLayout / HeaderAndFooterLayout  | 同名                                                 | 高     | 布局数学抽查正确                                                                          |
| SpacerElement / LayoutSettings / AbstractLayout / Layout                              | 同名                                                 | 高     |                                                                                           |
| LayoutElement                                                                         | ILayout                                              | 高     |                                                                                           |
| GuiEventListener                                                                      | GuiEventListener                                     | 中     | 事件模型缺口（W14）                                                                       |
| ContainerEventHandler / AbstractContainerEventHandler                                 | 同名                                                 | 高     | mouseScrolled 无坐标，空实现无法转发                                                      |
| ComponentPath / FocusNavigationEvent / ScreenDirection / ScreenAxis / ScreenRectangle | 同名                                                 | 中     | 无 InitialFocus；ScreenRectangle 缺 12 个矩形运算方法但无消费者（W31）                    |
| Screen                                                                                | screens/Screen                                       | 中     | 仅缺 changeFocus（setInitialFocus/clearFocus 已实现，见 S22）；根组件 setter 空实现（S5） |
| TabNavigationBar / GridLayoutTab / LoadingTab                                         | TabBar / GridLayoutTab / LoadingTab                  | 中     | 已核验：键盘导航未接线（W24）、位置 API 不一致（W25）                                     |
| toasts/SystemToast 等                                                                 | toast/SystemToast、BaseToast、ItemToast、SimpleToast | 高     | 高版本另含 RecipeToast/TutorialToast 等 4 个具体实现                                      |

### 2.2 高版本有、CatFrame 完全无对应（缺失清单）

| 缺失项                                                                                                   | 影响                                                                               | 优先级 |
| -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------ |
| narration 无障碍体系（6 文件）                                                                           | 中：高版本每个 AbstractWidget 均实现 updateWidgetNarration                         | 中     |
| AbstractSliderButton / AbstractOptionSliderButton                                                        | 中：滑块调节无替代                                                                 | 高     |
| MultiLineTextWidget / MultiLineLabel / FittingMultiLineTextWidget / FocusableTextWidget                  | 中：多行只读文本与聚焦文本缺口                                                     | 中     |
| IME preedit 通道（IMEPreeditOverlay + preeditUpdated 事件）                                              | 中：高版本 EditBox 原生支持 IME 预编辑；CatFrame 有 IngameIME 依赖但事件接口无入口 | 中     |
| ActiveTextCollector                                                                                      | 中：文本点击样式定位/逐字符命中的根基                                              | 中     |
| PopupScreen                                                                                              | 低：Overlay 系统可替代                                                             | 低     |
| ItemDisplayWidget / PlayerSkinWidget / PlayerFaceExtractor                                               | 低                                                                                 | 低     |
| CommonButtons / PlainTextButton / LockIconButton / LogoRenderer / SplashRenderer                         | 低                                                                                 | 低     |
| CommandSuggestions / ChatComponent                                                                       | 低：与 UI 库定位弱相关                                                             | 低     |
| OptionsList / ResettableOptionWidget / SelectableEntry / TextCursorUtils / ComponentRenderUtils / Whence | 低-中                                                                              | 低     |
| DebugScreenOverlay / BossHealthOverlay / SubtitleOverlay / PlayerTabOverlay                              | 低：Overlay 系统可承载                                                             | 低     |
| RecipeToast / TutorialToast / AdvancementToast / NowPlayingToast                                         | 低                                                                                 | 低     |

### 2.3 CatFrame 独有（高版本无）

Overlay 系统、TabRegistry、TabBar 四状态纹理、九宫格拉伸（TextureStretching/ContentPanelRenderer）、WaitingPanel、ToggleButton、SearchingBox、GuiButtonAdapter、ActionBarOverlay/TitleOverlay、Style/Text 体系、ToastCorner 四角定位。

---

## 三、旧报告断言核验（2026-07-20 版）

| #   | 旧报告断言                                 | 核验结论                                                                        |
| --- | ------------------------------------------ | ------------------------------------------------------------------------------- |
| 1   | 布局系统 100%                              | ✅ 仍成立                                                                        |
| 2   | Tab 系统 100% 且超越                       | ⚠️ 部分过时：visitChildren/doLayout 契约弱化未披露                               |
| 3   | Toast 系统 100% 且超越                     | ⚠️ 部分过时：getToken 默认值语义差异未披露                                       |
| 4   | 核心接口体系 75%（仅缺 NarratableEntry）   | ✅ 仍成立                                                                        |
| 5   | 容器/滚动系统"已落地 ✅"                    | ⚠️ 部分过时：SelectionList 缺口未披露                                            |
| 6   | 事件/焦点系统"已落地 ✅"                    | ⚠️ 部分过时：键盘断链（C2）、载荷缺口（W14）未披露                               |
| 7   | Narration 0% 完全缺失（"约 15 个文件"）    | ✅ 缺失成立，但文件数应为 **6 个**（NarrationThumbnail/ScreenNarrator 并不存在） |
| 8   | Checkbox 缺失（建议实现约 150 行）         | ❌ **已过时**：已实现 183 行（Builder/OnValueChange/Tooltip）                    |
| 9   | AbstractSliderButton 缺失                  | ✅ 仍成立                                                                        |
| 10  | ImageWidget 可暂缓                         | ❌ **已过时**：已实现（texture/sprite 双变体）                                   |
| 11  | MultiLineEditBox/MultilineTextField 可暂缓 | ❌ **已过时**：两者均已完整实现                                                  |
| 12  | AbstractList（6 行空接口）需清理           | ❌ **已过时**：已删除                                                            |
| 13  | AbstractStringWidget（12 行存根）需清理    | ❌ **已过时**：已完整实现（104 行）                                              |
| 14  | 综合完成度 ~70%                            | ❌ **低估**：基础控件/编辑框系/列表容器均已落地，应重估至 ~80%                   |
| 15  | 存根清单完整                               | ❌ 不完整：漏报 IconedButton、LoadingDotsWidgets、SimpleEditBox（W19）           |

### 3.1 第二轮勘误（2026-08-06，原版 recompSrc 验证）

| 上轮条目                 | 上轮声称                                                      | recompSrc 原版事实（GuiScreen.java）                                                                                                                                                         | 结论                                                                                                  |
| ------------------------ | ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| C1 幽灵输入              | "原版是 `if (Keyboard.next())` 每帧只消费 1 个事件"           | `handleInput()` L319-322 是 `while (Keyboard.next()) { this.handleKeyboardInput(); }`——逐事件循环；`handleKeyboardInput`（L366-374）内部不调 `next()`                                        | ❌ **证伪撤销**：Mixin HEAD 注入与 Screen 自派发均在循环体内，current_event 有效，时序正确，无幽灵输入 |
| W8 滚轮丢失              | "super 内部 while 消费全部事件后 getEventDWheel 返回最后事件" | `while (Mouse.next()) { this.handleMouseInput(); }`（L311-314）；`handleMouseInput`（L329-361）内部不调 `Mouse.next()`；Screen.handleMouseInput 覆写每轮迭代执行，super 后读到的就是本轮事件 | ❌ **证伪撤销**：逐事件读取正确，滚轮不会丢                                                            |
| C2 keyTyped 断链         | Screen.keyTyped 不 dispatch，叶子组件只有 keyTyped            | ScreenKeyboardInput L64-70 收尾调 `root.keyTyped`（=Screen 仅 Esc）；dispatchKeyPressed 到叶子默认 false                                                                                     | ✅ **复验成立**（三视角一致）                                                                          |
| C3 侧过滤                | 4 个 mixin 全在公共列表                                       | JSON 已把 3 个移入 `client`，但 `MixinGuiScreen` 仍在公共 `mixins` 列表，且 `getMixins()` 覆写（L30-37）仍无条件返回全部 4 个                                                                | ⚠️ **半修复**：仍需把 MixinGuiScreen 移入 client + 删除 getMixins() 覆写                               |
| W12 左 Ctrl              | 只识别右 Ctrl                                                 | 原版 `isCtrlKeyDown()` 同时查左右 Ctrl（29/157）                                                                                                                                             | ✅ 复验成立                                                                                            |
| S9 WidgetSprites"无漂移" | 字段语义一致                                                  | 四态 `get()` 逐分支一致，但 3 参构造回退漂移（`highlightedDisabled=highlighted`，高版本为 `disabledFocused=disabled`）                                                                       | ⚠️ 部分修正：见 S14                                                                                    |
| W11 事件桥注入点         | "注入点在循环之外"                                            | 注入点 `INVOKE handleKeyboardInput` 恰为 L321 循环体内调用点；`Minecraft.runTick` L1874 的直接调用点被 `method="handleInput"` 限定排除                                                       | ✅ 注入点唯一且安全（W11 其余结论成立）                                                                |

### 3.2 S13 未核对项核验结果（11 项，2026-08-06 第二轮）

| #   | 组件                         | 核验结论 | 关键缺口                                                                                                               |
| --- | ---------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------- |
| 1   | Button                       | 部分对齐 | Builder 缺 size/bounds/tooltip；Plain 子类被 useVanillaTexture 替代（S20）；文本色/背景绘制与原版 GuiButton 语义一致 ✅ |
| 2   | TabButton                    | 部分对齐 | isSelected() 用本地字段而非回查 TabManager（W29）；缺焦点态/静音/下划线                                                |
| 3   | TabBar                       | 部分对齐 | keyPressedNav 等**全仓无调用方**（W24）；位置 API 与渲染/命中不一致（W25）                                             |
| 4   | TabManager                   | 部分对齐 | W6 复验确认；初始 tab 按最小 ID 与按钮顺序可能不一致（S16）                                                            |
| 5   | ToastManager                 | 部分对齐 | 缺 getNotificationDisplayTimeMultiplier（W27）；F1 隐藏语义相反（W26）；音效无每帧限流（S17）                          |
| 6   | SystemToast                  | 部分对齐 | 宽度按未换行全文计算，长消息溢出（W28）；缺 9 个预置 SystemToastId 与静态工厂                                          |
| 7   | ObjectSelectionList          | 部分对齐 | 缺 nextFocusPath/narration（S15）；Entry.mouseClicked 空覆写存根                                                       |
| 8   | ContainerObjectSelectionList | 部分对齐 | Entry.setFocused 对旧/新子组件都 setActive(true)（W30）                                                                |
| 9   | ScrollableLayout             | 部分对齐 | W7 三视角复验确认；缺焦点自动滚动/ReserveStrategy（S19）；arrangeElements 双重布局（S18）                              |
| 10  | WidgetSprites                | 部分对齐 | 四态 get() 完全一致；3 参构造回退漂移（S14）                                                                           |
| 11  | ScreenRectangle              | 有缺口   | 缺 12 个方法（intersection/containsPoint/overlaps 等）；但无消费者，焦点导航不依赖（W31）                              |

---

## 四、Critical Issues（MUST FIX）

### C1. 【已证伪撤销】键盘事件读取时机错误：在 Keyboard.next() 之前读 current_event → 幽灵输入
~~上轮声称 1.7.10 原版是 `if (Keyboard.next())` 每帧只消费 1 个事件、注入点在循环之外，据此推导"幽灵输入"。经 1.7.10 原版反混淆源码（[GuiScreen.java#L307-L324](d:/GAMES/Minecraft/modss/project/CatFrame/build/tmp/recompSrc/net/minecraft/client/gui/GuiScreen.java)）验证：`handleInput()` 是 `while (Keyboard.next()) { this.handleKeyboardInput(); }` 逐事件循环，`handleKeyboardInput`（L366-374）内部不调 `Keyboard.next()`。Mixin HEAD 注入与 Screen 自派发均在循环体内执行，读取的 current_event 有效且未被消耗——**时序正确，幽灵输入机制不存在，本条撤销**。~~

（注：上轮修复建议"仿 1.8+ 用 while(Keyboard.next()) 逐事件派发"实为原版现状，若照做会造成事件双重消费，勿实施。）

### C2. keyTyped 断链：Screen 基类下组件树收不到任何键盘输入
[Screen.java#L331-L336](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/screens/Screen.java) · [ContainerEventHandler.java#L104-L136](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/events/ContainerEventHandler.java) · [AbstractEditBox.java#L161-L237](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractEditBox.java)

`Screen.keyTyped` 只处理 Esc 关闭，**不调用 `dispatchKeyTyped`**（注释认为"拆分事件经 ScreenKeyboardInput 派发"），但：
- 全部叶子组件（AbstractEditBox、MultilineEditBox、AbstractButton、CyclingButton、Checkbox 等）**只实现了 `keyTyped`**——文本输入、Delete/Backspace、Ctrl 快捷键逻辑全在 `keyTyped` 里；
- `ScreenKeyboardInput` 的 `keyPressed → dispatchKeyPressed → focused.keyPressed` 走接口默认实现（返回 false，丢失）。

**后果**：`ui.screens.Screen` 基类下，文本框无法输入任何字符、按钮无法用键盘触发。鼠标路径正常，唯独键盘全断。

**第二轮复验（2026-08-06）**：三视角一致确认。关键证据链：ScreenKeyboardInput.handleCurrentEvent（[L64-L70](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/events/ScreenKeyboardInput.java)）收尾调用 `root.keyTyped(character, keyCode)`——root 即 Screen 自身，而 Screen.keyTyped 只处理 Esc；`dispatchKeyTyped` 在根路径从未被调用（全仓 grep 仅容器类自身 keyTyped 转发）；叶子组件只覆写 keyTyped，keyPressed/charTyped 走接口默认返回 false。

**修复**：`Screen.keyTyped` 的 Esc 处理之后补 `dispatchKeyTyped(typedChar, keyCode)`（ContainerEventHandler 已有默认实现会转发给焦点组件）；并让叶子组件实现 `keyPressed/charTyped`，或在 `ContainerEventHandler.dispatchKeyPressed` 内做 keyTyped 桥接（二选一，保持单一输入通道）。

后续：已经修复，以后轮无需处理这些问题。

### C3. 客户端专用 Mixin 未经侧过滤 → 专用服务器必然崩溃
[mixins.catframe.json#L8-L15](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/resources/mixins.catframe.json) · [CatFrameMixinPlugin.java#L30-L36](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/mixin/CatFrameMixinPlugin.java) · [mixin.gradle#L18-L20](d:/GAMES/Minecraft/modss/project/CatFrame/mixin.gradle)

`MixinGuiScreen`（target `net.minecraft.client.gui.GuiScreen`）仍被放在 JSON 的**公共** `mixins` 列表（其余 3 个已移入 `client` 列表）；同时 `CatFrameMixinPlugin.getMixins()` 覆写（L30-37）无条件返回全部 4 个 mixin——该覆写替换 JSON 的公共列表，侧过滤被彻底绕过。其目标类为客户端专用类，在专用服务器 jar 中不存在；配置 `"required": true`，mixin 无法应用按硬错误处理；而模组本身通过 `@SidedProxy` 有服务端代理，会实际装入服务器。

**后果**：任何把 CatFrame 装入专用服务器的部署直接启动失败。

**第二轮复验（2026-08-06）**：JSON 半修复（3 个已移入 client），但 `MixinGuiScreen` 仍在公共列表 + `getMixins()` 覆写仍在——**问题依旧，只是范围缩小到 MixinGuiScreen 一个**。

**修复**：把 `MixinGuiScreen` 移入 JSON 的 `"client"` 列表；删除 `getMixins()` 覆写（或按 `MixinEnvironment.getSide()` 返回侧正确的子集）。

后续：已经修复，以后轮无需处理这些问题。

---

## 五、Warnings（SHOULD FIX）

### W1. glScissor 坐标未乘 GUI scaleFactor → guiScale>1 时裁剪错位
[AbstractScrollArea.java#L178-L182](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractScrollArea.java) · [AbstractTextAreaWidget.java#L98-L100](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractTextAreaWidget.java)

`glScissor` 使用窗口物理像素（不受投影矩阵影响），而组件坐标是 scaled GUI 坐标。guiScale=2 时裁剪区域只有预期的一半，且 Y 轴 `displayHeight - y - height` 物理/缩放单位混用。仅 guiScale=1 时恰好正确，多数玩家用 2/3 倍缩放，影响面大。

**修复**：
```java
ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
int s = sr.getScaleFactor();
GL11.glScissor(this.x * s, displayHeight - (this.y + this.height) * s, this.width * s, this.height * s);
```
（AbstractTextAreaWidget 的 `+1/-1` 内缩同样要乘 s。）

### W2. TabBar Ctrl+数字快捷键映射整体错位
[TabBar.java#L533-L539](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabBar.java)

LWJGL2 键码 `KEY_1=2 … KEY_9=10, KEY_0=11`，但 `digitIndex = (keyCode == 2) ? 0 : keyCode - 3`：KEY_2(3) 与 KEY_1 冲突，KEY_0(11)→8，索引 9 永远不可达。Ctrl+2~Ctrl+0 全部切错 tab。

**修复**：`int digitIndex = keyCode - 2;`（2→0, 11→9），去掉特判。

### W3. MultilineTextField 粘贴截断未扣减选区长度 → 文本已满时粘贴被吞
[MultilineTextField.java#L504-L511](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/MultilineTextField.java)

`remainingCharacters = characterLimit - this.value.length()` 未减去选区长度，而 `insertText` 先截断再替换选区。`value.length() == characterLimit` 且有选区时：粘贴文本被截成空串，选区被删除但内容未插入——选中文本消失、粘贴内容丢失。

**修复**：`remainingCharacters = Math.max(0, characterLimit - this.value.length() + (selected.endIndex - selected.beginIndex))`，或在 `insertText` 中先替换选区再截断。

### W4. MultilineEditBox 选区高亮末行超出内容区 4px
[MultilineEditBox.java#L242-L247](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/MultilineEditBox.java)

跨行选区末行 `drawEnd = this.width - this.innerPadding()`，但高版本为 `width - totalInnerPadding()`（左右合计）。矩形右边界 = `x + innerPadding + width - innerPadding = x + width`，越过右内边距，高亮绘制进边框区域。

**修复**：`drawEnd = this.width - this.totalInnerPadding();`

### W5. AbstractEditBox 粘贴不替换选区 + setText 不截断 maxLength
[AbstractEditBox.java#L252-L265](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractEditBox.java) · [AbstractEditBox.java#L361-L373](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractEditBox.java)

1. `insertText` 直接把文本插在 `cursorPosition` 处，不先 `deleteSelection()`；Ctrl+V 走此路径，有选区时结果是"插入 + 选区原样保留"，与 `keyTyped` 普通字符分支（先删选区）行为不一致；
2. `setText` 直接赋值不截断 maxLength，而 `keyTyped/insertText` 均受 maxLength 限制——同一组件两个写入口行为不一致，可绕过长度限制。

**修复**：
```java
// insertText 开头：
if (selectionEnd != cursorPosition) deleteSelection();
// setText：
this.text = maxLength > 0 && text.length() > maxLength ? text.substring(0, maxLength) : text;
```

### W6. TabManager.reinitializeTabs 引用相等短路 → 重建组件未重新注册
[TabManager.java#L394-L415](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabManager.java)

`reinitializeTabs` 先对所有 tab 调 `initGui()`（`AbstractScreenTab.initGui` clear 三个 widget 列表并重建实例），随后 `setCurrentTab(savedTab, false)`——此时 `currentTab` 仍等于 `savedTab`，`!Objects.equals(...)` 为 false，整个 if 块（visitChildren/addWidget 等）被跳过。高版本风格（Consumer 构造器）下，initGui 重建的新组件实例永远不会推送到外部容器；配合 AbstractScreenTab.initGui 只 clear 不重新 add，窗口 resize 后组件丢失/失效。

**修复**：`reinitializeTabs` 内先置 `currentTab = null` 再 `setCurrentTab(savedTab, false)`（强制走移除+添加路径）；或复制高版本做法：先 `currentTab.visitChildren(removeWidget)` 再 initGui 后 `visitChildren(addWidget)`。

### W7. ScrollableLayout.ScrollContainer.setScrollAmount 内容 Y 多加了容器高度 → 内容不可见
[ScrollableLayout.java#L238-L242](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ScrollableLayout.java)

`content.setY(getY() + getHeight() - (int) this.scrollAmount())`——scrollAmount=0 时内容顶部被放到 `getY()+getHeight()`（容器底部之下），scissor 裁剪下内容完全不可见；且与同一容器的 `setY` 路径（`content.setY(y - scrollAmount)`）公式矛盾，一旦 recalculate/refreshScrollAmount/滚轮触发 setScrollAmount，内容立即跳到底部下方，滚动方向也反了。

**修复**：`content.setY(getY() - (int) this.scrollAmount());`（与 setY 路径一致）。

**第二轮复验（2026-08-06）**：三视角一致确认（correctness 视角建议升级 Critical）；高版本对照为 `content.setY(rect.top() - scrollAmount)`（即 `getY() - scroll`）。修复不变。

### W8. 【已证伪撤销】Screen.handleMouseInput 在 super 消费全部事件后读滚轮 → 滚轮事件易丢失
~~上轮声称 `super.handleMouseInput()` 内部 while 循环消费全部事件、随后 `getEventDWheel` 读到的是最后事件。经 recompSrc 验证：while 循环在 `GuiScreen.handleInput()`（[L311-L314](d:/GAMES/Minecraft/modss/project/CatFrame/build/tmp/recompSrc/net/minecraft/client/gui/GuiScreen.java)），`handleMouseInput`（L329-361）内部不调 `Mouse.next()`；Screen.handleMouseInput 覆写（L276-284）在每轮迭代内执行，super 后读 `Mouse.getEventDWheel()` 得到的就是本轮事件——**逐事件正确，滚轮不丢，本条撤销**。~~

### W9. 延迟管线 flush（LOW）先于 Overlay 渲染（LOWEST）→ Overlay 延迟元素被静默丢弃
[ClientScreenGraphicsHandler.java#L49-L52](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/compact/vanilla/ClientScreenGraphicsHandler.java) · [ClientOverlayHandler.java#L121-L124](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/compact/vanilla/ClientOverlayHandler.java)

两个处理器都挂在 `DrawScreenEvent.Post` 上：`extractDeferredElements()` 在 **LOW**、`OverlayManager.renderAll()` 在 **LOWEST**，Forge 按 HIGHEST→LOWEST 顺序执行，Overlay 渲染发生在当帧 flush **之后**。Overlay 组件收集的 tooltip/延迟物品写入已 flush 过的单例管线，下一帧 `DrawScreenEvent.Pre` 的 `resetForNewFrame()` 清空——元素永远不会被绘制。HUD 上下文 Overlay（RenderGameOverlayEvent.Post）更不在 drawScreen 窗口内。当前内置 Toast/Title/ActionBar 均即时渲染未暴露，但任何带组件 tooltip 或延迟物品的 Overlay 会静默失效。

**修复**（三选一）：A. Overlay 渲染移到 LOW、flush 移到 LOWEST；B. `ClientOverlayHandler.onDrawScreenPost` 在 renderAll() 后补一次 flush；C.（推荐）Overlay 在 `DrawScreenEvent.Pre` 完成收集、Post 统一 flush，与 Screen 内容同一管线周期。

### W10. IME 集成只扫描 OverlayManager → CatFrame Screen 上的文本框完全无 IME
[IMECompact.java#L131-L146](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/compact/IMECompact.java)

`findFocusedArea()` 仅遍历 OverlayManager 注册的 Overlay；而 CatFrame UI 的主消费方是 `ui.screens.Screen`（实现 CatFrameInputScreen、含 AbstractEditBox 的组件树）——Screen 不注册为 Overlay，因此焦点文本框位于 Screen 上时 `shouldReceiveInput()` 恒 false、`commitText` 空转、候选窗锚点返回 -1。与高版本 EditBox 完整输入法支持及项目"与 IngameIME 交互"目标断裂。

**修复**：`findFocusedArea` 增加第二路径——若 `Minecraft.currentScreen` 是 CatFrameInputScreen，从其 `getEventRoot()` 沿焦点链递归查找；或将 CatFrame Screen 的焦点链以可查询方式暴露给 compact 层。

### W11. 事件桥对"所有屏幕的所有键盘事件"全局广播，可取消 Pre 语义覆盖他模组屏幕
[MixinGuiScreenEventBridge.java#L51-L61](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/mixin/middle/MixinGuiScreenEventBridge.java)

`@WrapOperation` 包住 `GuiScreen.handleInput()` 中 `handleKeyboardInput()` 调用点，对任何打开中的屏幕（含原版主菜单、NEI/容器界面、其它模组 GUI）逐个键盘事件广播可取消的 `Pre`。任一订阅者取消 Pre 即整体跳过该屏幕的 handleKeyboardInput——包括原版 Esc 关屏；且与 1.7.10 生态中同类注入（输入法模组、NEI 等）叠加在同一调用点，取消语义互相穿透，无法按屏幕隔离。

**修复**：至少限定 `if (!(screen instanceof CatFrameInputScreen)) { original.call(screen); return; }`，把默认可见面收敛到 CatFrame 生态内；对外部屏幕的广播改为可选开关。

### W12. KeyTypedEvent.isControlKeyPressed 非 Mac 分支只识别右 Ctrl → 左 Ctrl 组合键语义错误
[KeyTypedEvent.java#L14-L16](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/events/KeyTypedEvent.java)

非 Mac 分支检查 `KEY_RCONTROL || KEY_RMETA`（右 Ctrl / 右 Win），漏掉左 Ctrl。该方法是 ScreenKeyboardInput 的 Tab 导航守卫（skip Ctrl+Tab）：按左 Ctrl+Tab 时返回 false，CatFrame 仍把 Tab 当焦点导航消费掉；且与系统其它地方使用的 `GuiScreen.isCtrlKeyDown()`（同时查左右 Ctrl）语义不一致。

**修复**：直接复用 `GuiScreen.isCtrlKeyDown()`，或改为 `LCONTROL || RCONTROL`（Mac 分支保留 `LMETA || RMETA`）。

### W13. AbstractComponent.isActive() 语义与高版本契约不符
[AbstractComponent.java#L104-L106](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractComponent.java)

高版本 `isActive() = visible && active`；CatFrame 只返回 `active`。依赖 isActive() 的外部代码在"不可见但仍 active"的组件上得到相反结果；CatFrame 自身 `nextFocusPath` 默认实现被迫用 `!isActive() || !isVisible()` 分开判断补救，说明偏差已知但接口契约未对齐。

**修复**：改为 `visible && active`，并审计现有调用点确认无行为反转。

### W14. 事件模型接口缺口：boolean 消费、修饰键、doubleClick、preeditUpdated、mouseMoved 缺失
[GuiEventListener.java#L65-L137](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/events/GuiEventListener.java) 对比 [高版本 GuiEventListener](d:/GAMES/Minecraft/modss/project/CatFrame/markdown/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/gui/components/events/GuiEventListener.java)

CatFrame 事件方法全部为 void/无修饰键/无 doubleClick 签名，与高版本 boolean 消费语义 + `KeyEvent`(scancode+modifiers) + `MouseButtonEvent`(doubleClick) + `PreeditEvent` 不兼容。直接阻断的交互：Shift+点击反向循环（CycleButton）、Ctrl+词跳转/整词删除（EditBox）、双击选词、鼠标拖选、IME 预编辑。

**修复**：新增"事件载荷"章节披露差异及受影响交互，1.7.10 可行替代：KeyTypedEvent 修饰键查询通道（已有）、双击时间窗检测、mouseMoved 经鼠标坐标缓存。优先级中等，至少保证 Ctrl 组合键语义一致（见 W12）。

### W15. EditBox 系接口缺口：水平滚动、双击选词、拖选、responder/suggestion/formatter/只读模式
[AbstractEditBox.java#L102-L107](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractEditBox.java) 对比 [高版本 EditBox.java#L83-L160](d:/GAMES/Minecraft/modss/project/CatFrame/markdown/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/gui/components/EditBox.java)

无水平滚动（displayPos/scrollTo）：超长文本被 trimStringToWidth 截断显示，光标在截断区外时按 clipped 子串计算位置，显示位置与编辑位置脱节；缺双击选词、鼠标拖选、Ctrl+方向键词跳转、Ctrl+Backspace/Delete 整词删除、setResponder、suggestion、TextFormatter、isEditable 只读模式。

**修复**：优先补水平滚动（displayPos 前移渲染）与只读模式；Ctrl 词级操作依赖 W14 修饰键通道。

### W16. AbstractSelectionList 缺口：swap/clearEntriesExcept/nextEntry 等缺失，Entry.isFocused() 恒 false
[AbstractSelectionList.java#L73-L127](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractSelectionList.java)

高版本以下成员缺失：`swap`（拖拽换序）、`clearEntriesExcept`、`removeEntryFromTop`、`removeEntries`、`nextEntry(ScreenDirection, Predicate, startEntry)`（方向键候选导航）、`getRowTop/getRowBottom`、`getChildAt` 覆写、narration 方法。另：`setSelected` 无条件 scrollToEntry（高版本仅键盘或越界时滚动）；`Entry.isFocused()` 恒 false（高版本回查 `list.getFocused() == this`）。

**修复**：优先补 `nextEntry`（方向键导航完整性）与 `Entry.isFocused()` 回查（选中高亮与焦点状态一致性）。

### W17. CyclingButton 与高版本 CycleButton 的语义缺口
[CyclingButton.java#L92-L110](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/CyclingButton.java)

缺口：Shift+点击反向循环（onPress 无修饰键）、`resetValue()`（ResettableOptionWidget 契约）、Alt 值列表（withValues 的三参重载）、DisplayState(NAME_AND_VALUE/VALUE/HIDE)、SpriteSupplier、TooltipSupplier；`updateValue` 不更新 message（高版本 setMessage 同步，外部读 getMessage() 得到旧标签）。

**修复**：至少补 Shift 反向（经 W12 修饰键通道）与 `resetValue()`。

### W18. Tab 接口契约弱化：visitChildren 类型不安全、doLayout 默认空操作
[Tab.java#L125-L161](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/Tab.java) 对比 [高版本 Tab](d:/GAMES/Minecraft/modss/project/CatFrame/markdown/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/gui/components/tabs/Tab.java)

高版本 `visitChildren`/`doLayout` 是必须实现的抽象方法（布局与朗读遍历依赖）；CatFrame 做成默认空操作，且 `visitChildren(Consumer<Object>)` 参数为 Object（类型不安全，靠 visitComponents 二次过滤）。双风格接口虽兼容 1.7.10 遗留代码，但高版本风格契约对实现方不再强制——TabManager 若只调用默认空实现，布局与焦点遍历静默失效。

**修复**：将 `visitComponents(Consumer<GuiEventListener>)` 提升为主契约，TabManager 对空实现打日志/告警。

### W19. 存根未清理：IconedButton 不可实例化、LoadingDotsWidgets 空类、SimpleEditBox 薄包装
[IconedButton.java#L5-L9](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/IconedButton.java) · [LoadingDotsWidgets.java#L3-L5](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/LoadingDotsWidgets.java) · [SimpleEditBox.java#L3-L8](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/SimpleEditBox.java)

- `IconedButton`：10 行，仅 protected 构造器转发，**不可从包外实例化**，与 SpriteIconedButton（11.3KB）职责重叠；
- `LoadingDotsWidgets`：6 行空类，而高版本 LoadingDotsWidget 有完整渲染（消息 + 动画点 + isActive()=false + 不参与焦点）；顶层已有 LoadingDotsText 工具但控件未接；
- `SimpleEditBox`：9 行薄包装，可用但未在报告中说明。

**修复**：IconedButton 删除或转由 SpriteIconedButton 承担；LoadingDotsWidgets 参照高版本补渲染与 isActive()=false/nextFocusPath()=null 语义。

### W20. Narration 无障碍体系完全缺失（高版本 6 文件）
[narration 包](d:/GAMES/Minecraft/modss/project/CatFrame/markdown/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/gui/narration)

高版本 narration 包实际 6 个文件（NarratableEntry、NarratedElementType、NarrationElementOutput、NarrationSupplier、NarrationThunk、ScreenNarrationCollector）；CatFrame 只在 `Tab.getTabExtraNarration()` 留了入口，底层完全没建体系。旧报告估算"约 15 个文件"失真（NarrationThumbnail/ScreenNarrator 并不存在）。

**修复**：最低限度实现 NarratableEntry + NarrationElementOutput + NarratedElementType，在 AbstractComponent 加 `updateNarration()` 默认空实现，即可支撑朗读挂钩。

### W21. AbstractSliderButton 缺失（滑块调节无替代）
高版本 5.5KB。选项类 UI（音量/灵敏度/视距）高频使用，CatFrame 无任何对应。**建议近期实现**（约 160 行：轨道渲染 + 拖动映射 + 键盘左右调节）。

### W22. 多行只读文本组件缺失（MultiLineTextWidget/FocusableTextWidget/FittingMultiLineTextWidget）
高版本各 2-5KB。说明性/帮助文本只能单行截断或手写布局。Checkbox 的两行标签能力（S11）也依赖此族。

### W23. IME preedit 通道与 ActiveTextCollector 缺失
- 高版本 EditBox 原生支持 IME 预编辑覆盖层（IMEPreeditOverlay + preeditUpdated 事件）；CatFrame 已引入 IngameIME 依赖（lib/IngameIME-1.7.10-2.0.10-dev.jar）但事件接口无 preedit 入口，AbstractEditBox.getCursorPosition() 的"IME 候选窗锚点"用途只能靠外部轮询（且 W10 已断）；
- ActiveTextCollector（高版本 8.3KB）是文本点击样式定位/悬停高亮根基，CatFrame 组件点击回调只能回传整体样式。

### W24. TabBar 键盘导航契约整体未接线：keyPressedNav/mouseClickedNav/drawNavButtons 全仓无调用方
[TabBar.java#L512-L542](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabBar.java)

`keyPressedNav`/`mouseClickedNav`/`drawNavButtons` 全仓 grep **无任何调用方**（仅 javadoc 引用）——高版本 TabNavigationBar 的 Ctrl+Tab/Ctrl+数字经容器事件分发接入，CatFrame 的快捷键契约在真实输入管线中完全缺失（即使修好 W2 的映射公式也无宿主接线）。

**修复**：在 Screen 或容器内接入 keyPressedNav（依赖 C2 修复后的键盘路径）；映射改 `keyCode - 2`（W2）；补 per-tab 激活态跳过与焦点集成。

### W25. TabBar 位置 API 与渲染/命中检测不一致，经 ILayout 重定位即错位
[TabBar.java#L140-L164](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabBar.java) · [TabBar.java#L441](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabBar.java) · [TabBar.java#L488](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabBar.java)

实现 ILayout 但定位行为自相矛盾：`setX` 按 dx 平移 tabButtons，`setY` 却完全不平移按钮；`drawNavButtons` 的背景/分隔线硬编码在 `(0,0,navWidth,NAV_HEIGHT)`，不跟随 layoutX/layoutY；`mouseClickedNav` 的 `mouseY < 0 || mouseY >= NAV_HEIGHT` 门限假设导航栏固定在 y=0。任何宿主布局（如 HeaderFooterLayout）通过 setX/setY 重定位后，按钮、背景、点击区域三者互相错位——按钮点到但背景不在、或按钮看不到点不到。

**修复**：`drawNavButtons`/`mouseClickedNav` 改用 layoutX/layoutY 偏移绘制与判定；`setY` 与 `setX` 行为必须对称（统一坐标基准）。

### W26. F1 隐藏 HUD 时 Toast 显示语义与高版本相反
[ToastManager.java#L102-L106](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/ToastManager.java)

注释声称"仅在 F1 隐藏 HUD 且没有打开任何界面时才跳过"，但 26.1.2 参照是 `if (!options.hideGui) return;`——只要 F1 按下就隐藏 Toast，与是否打开界面无关。触发：游戏内按 F1 打开界面（如物品栏）时，CatFrame 仍在界面之上绘制 Toast，行为相反。

**修复**：`if (mc.gameSettings.hideGUI) return;`

### W27. ToastManager 缺 getNotificationDisplayTimeMultiplier → "通知显示时长"选项语义缺失
[ToastManager.java#L54-L95](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/ToastManager.java) · [SystemToast.java#L80-L89](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/SystemToast.java)

高版本 SystemToast.update 用 `id.displayTime * manager.getNotificationDisplayTimeMultiplier()`；CatFrame 无该方法，直接用 `id.displayTime`——高版本 accessibility 可调选项"通知显示时长"的语义缺失。

**修复**：ToastManager 增加 `getNotificationDisplayTimeMultiplier()`（读 GameSettings 或返回 1.0 并文档化），SystemToast.update 乘上它。

### W28. SystemToast 宽度按未换行全文计算，长消息 Toast 溢出屏幕
[SystemToast.java#L44-L47](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/SystemToast.java)

`splitText` 按 180px 折行，但 `width = max(160, 30 + max(titleWidth, 整条消息宽度))` 用未折行全文宽度——400px 宽消息折成 3 行后 Toast 仍 430px 宽，右侧大片空白甚至溢出屏幕。高版本用折行后**最大行宽**计算。

**修复**：遍历 messageLines 取最大行宽参与宽度计算（`30 + max(titleWidth, maxLineWidth)`）。

### W29. TabButton isSelected 状态源不一致 + 焦点态缺失
[TabButton.java#L77-L87](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/TabButton.java) · [TabButton.java#L165-L186](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/TabButton.java)

高版本 `isSelected()` 实时查 `tabManager.getCurrentTab() == tab`（单一事实源）；CatFrame 为本地字段，仅 `drawNavButtons` 每帧同步——绕过 TabBar 直接 `TabManager.setCurrentTab` 的宿主会出现按钮状态漂移。纹理选择用 `isHovered` 而非 `isHoveredOrFocused`（TabBar 无焦点体系故焦点态恒灭）；缺高版本静音 `playDownSound`（切 tab 无声）与选中下划线。

**修复**：`isSelected()` 改为回查持有的 TabManager（构造器注入，与高版本同构），消除帧同步依赖。

### W30. ContainerObjectSelectionList.Entry.setFocused 焦点状态机错误
[ContainerObjectSelectionList.java#L79-L87](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ContainerObjectSelectionList.java)

高版本对旧子组件 `setFocused(false)`、新子组件 `setFocused(true)`；CatFrame 对**两者都调 `setActive(true)`**——旧子组件永不失活、新子组件 focused 标志永不被置位。条目内子组件（按钮/EditBox）随焦点在条目间移动时完全得不到得焦/失焦通知。

**修复**：
```java
if (this.focused != null) { this.focused.setFocused(false); }
if (focused != null) { focused.setFocused(true); }
this.focused = focused;
```

### W31. ScreenRectangle 缺 12 个矩形运算方法（确认），但影响断言修正
[ScreenRectangle.java#L32-L70](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/navigation/ScreenRectangle.java) vs [高版本 123 行](d:/GAMES/Minecraft/modss/project/CatFrame/markdown/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/gui/navigation/ScreenRectangle.java)

确认缺失 `intersection`（可空交集）、`containsPoint`、`overlaps`、`overlapsInAxis`、`getCenterInAxis`、`getBoundInDirection`、`getBorder`、`step`、`empty`、`of(ScreenAxis,…)` 及两个矩阵变换。**但**全仓 grep 无消费者——`ContainerEventHandler.arrowNavigate` 用内联 `centre/isInDirection/directionScore` 自行计算，焦点导航并不依赖缺失方法（上轮"焦点几何导航依赖其边界计算"的影响断言过重）。

**修复**：按高版本补齐纯整数方法（intersection/containsPoint/overlapsInAxis/getBoundInDirection/getBorder/getCenterInAxis/step/empty/of）；矩阵变换在引入 vecmath 适配后再做，标注 TODO。

### W32. GuiButtonAdapter 点击只播音效不触发动作；与 buttonList 混用时音效双重播放
[GuiButtonAdapter.java#L54-L60](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/GuiButtonAdapter.java)

`mouseClicked` 调 `delegate.mousePressed()` 后只播放音效，**从不触发 `actionPerformed`**——原版动作链路是 `mousePressed → func_146113_a(音效) → actionPerformed`（[recompSrc GuiScreen L237-L258](d:/GAMES/Minecraft/modss/project/CatFrame/build/tmp/recompSrc/net/minecraft/client/gui/GuiScreen.java)）。仅加入组件树时按钮动作永不执行（功能失效）；同时留在 buttonList 混用时原版路径 + adapter 各播一次音效（双重播放）。

**修复**：adapter 点击后调用宿主屏幕的 actionPerformed（或等价回调），仅播一次音效；文档明示"已入 buttonList 的按钮不应再包 adapter"。

### W33. Toast 渲染 GL 状态在屏幕路径下泄漏（blendFunc/纹理绑定不还原）
[SystemToast.java#L94-L105](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/SystemToast.java) · [ToastManager.java#L276-L289](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/ToastManager.java) · [OverlayManager.java#L195-L203](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/overlay/OverlayManager.java)

SystemToast.renderContent 设 `glBlendFunc` 后仅 `glDisable(GL_BLEND)`，blendFunc 不还原；ToastManager.render 不恢复纹理绑定；OverlayManager 的屏幕路径 `renderAll` 完全不恢复任何 GL 状态（HUD 路径 renderHud 恢复 3 项但不含 blendFunc）。同一帧后续绘制的原版/他模组 HUD 元素可能被污染（与上轮 S8 同类，Toast 路径未纳入）。

**修复**：ToastInstance.render 用 `glPushAttrib(GL_ENABLE_BIT|GL_CURRENT_BIT|GL_COLOR_BUFFER_BIT|GL_TEXTURE_BIT)/glPopAttrib` 包裹，或让 renderAll 与 renderHud 统一恢复。

### W34. 事件桥 Pre 取消可压制原版全局按键派发 func_152348_aa
[MixinGuiScreenEventBridge.java#L51-L61](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/mixin/middle/MixinGuiScreenEventBridge.java)

键盘事件 Pre 发布在公共 EVENT_BUS，任一订阅者取消即跳过整个 `handleKeyboardInput()`——不仅 CatFrame 拆分派发和 keyTyped（含 Esc 关屏），还包括原版 `func_152348_aa()`（[recompSrc L373](d:/GAMES/Minecraft/modss/project/CatFrame/build/tmp/recompSrc/net/minecraft/client/gui/GuiScreen.java)：F2 截图、F3 调试等全局按键）。影响面覆盖所有打开中的屏幕（含原版/他模组 GUI），1.7.10 生态其他模组不认识该事件、无从防御。上轮 W11 的补充（注入点本身经验证唯一且安全，见 3.1）。

**修复**：在事件 javadoc 显式标注"取消将同时压制 func_152348_aa（F2/F3 全局按键）"；并考虑仅对 CatFrameInputScreen 实例发 Pre。

### W35. Toast 默认 TOP_RIGHT 与 1.7.10 模组生态右上角元素重叠风险（原版药水 HUD 已排除）
[Toast.java#L111-L113](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/Toast.java)

经 recompSrc 全文检索证实：1.7.10 原版 GuiIngame **无**右上角药水效果图标段（该功能 1.8+ 才有），不与原版重叠；但 1.7.10 模组生态（NEI/耐久显示/信息类 HUD 等）大量占用右上角。Toast 在 RenderGameOverlayEvent.Post 与 DrawScreenEvent.Post（LOWEST）渲染，z 序高于一切，滑入动画从右缘进入（约 600ms）会遮罩右上角区域。

**修复**：文档注明默认角冲突面；提供按锚点全局配置的入口。

### W36. 混用 buttonList 与 CatFrame 组件时原版按钮盖住 CatFrame 组件
[Screen.java#L214-L227](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/screens/Screen.java)

drawScreen 先渲染 CatFrame renderables，再 `super.drawScreen()` 画原版 buttonList/labelList——混用场景（子类同时用 addRenderableWidget 与原版 buttonList）下原版按钮绘制在 CatFrame 组件**之上**，与高版本"组件树统一管理"的层级语义不一致。

**修复**：文档注明混用层级，或提供"原版按钮先画"的开关。

---

## 六、Suggestions（CONSIDER）

### S1. Style.hashCode 未包含 font 字段
[Style.java#L465-L468](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/Style.java)：`equals` 比较 font 而 `hashCode` 未含，仅增加哈希冲突。补 font 字段即可。

### S2. Style.Serializer.deserialize 为 stub，serialize/deserialize 不对称
[Style.java#L704-L706](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/Style.java)：`serialize` 输出 `{color=…,bold…}` 格式，`deserialize` 无条件返回 EMPTY，序列化后反序列化静默丢失全部样式。若暂无调用方，标注 @Deprecated/TODO 或实现对称解析。

### S3. FontHelper.width 未计入样式加宽（粗体）
[FontHelper.java#L100-L103](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/FontHelper.java)：`width(Text)` 用无 § 码字符串测量，而 draw 会加 §l 前缀；1.7.10 FontRenderer.getStringWidth() 仅对粗体（§l）每字符额外 +1px（斜体 §o 不影响测量宽度——getStringWidth 的样式分支只识别 l/L/r/R，不处理 §o），CatFrame 测量每粗体字符少 1px，居中会偏移。建议 draw 返回实际绘制宽度供调用方复用。

### S4. OverlayManager.renderTarget 每帧新建 ScaledResolution
[OverlayManager.java#L217](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/overlay/OverlayManager.java)：每帧每目标构造一次并做整数除法链，开销小但可复用（按 displayWidth/Height 变化缓存或提为参数）。

### S5. Screen 根组件位置/可见性 setter 为静默空实现
[Screen.java#L490-L529](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/screens/Screen.java)：setX/setY/setVisible/setActive 均为空实现（getX/getY 恒 0），下游调用静默无效且无告警。删除这组覆写，或在类文档显著标注"根组件位置不可变"并让 setter 抛 UnsupportedOperationException 早暴露误用。

### S6. OverlayManager 全局单例使"暂停游戏"决策跨屏幕、跨模块全局耦合
[OverlayManager.java#L134-L144](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/overlay/OverlayManager.java)：任一模块注册可见且 isPausingGame() 的 SCREEN Overlay 会改变所有 CatFrame 屏幕的暂停行为，且在其上方的非 CatFrame 屏幕（如 NEI）也渲染并请求暂停但无人咨询。为 Overlay 增加宿主屏幕类型范围限定。

### S7. renderHud 的 GL 状态恢复不完整
[OverlayManager.java#L195-L203](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/overlay/OverlayManager.java)：只恢复 glColor4f/GL_TEXTURE_2D/GL_BLEND，若 Overlay 改动深度/裁剪等状态会污染随后绘制的其它模组 HUD 元素。用 glPushAttrib(GL_ENABLE_BIT|GL_CURRENT_BIT|GL_COLOR_BUFFER_BIT)/glPopAttrib 包裹整段渲染。

### S8. drawItemModel 的 glPushAttrib 掩码不含 COLOR_BUFFER_BIT → glBlendFunc 修改泄漏
[GuiGraphicsExtractor.java#L63-L64](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/GuiGraphicsExtractor.java) · [GuiGraphicsExtractor.java#L270-L277](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/GuiGraphicsExtractor.java)：`GL_SAVE_MASK = ENABLE_BIT|TEXTURE_BIT|CURRENT_BIT` 不覆盖 blend 函数（属于 GL_COLOR_BUFFER_BIT），setupItemRenderState 设置的 blendFunc 不会被 popAttrib 还原，隐式依赖"调用方恰好是默认 blend"。掩码补 `GL11.GL_COLOR_BUFFER_BIT`（与 renderEnchantmentGlint 一致）。

### S9. 技术债务明细（TODO/死代码/参数忽略）
[GuiGraphicsExtractor.java#L472](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/GuiGraphicsExtractor.java) · [ItemToast.java#L45-L49](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/ItemToast.java) · [TabBar.java#L461-L473](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabBar.java) · [AbstractComponent.java#L218-L225](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/AbstractComponent.java)

- `setTooltipForNextFrame` 的 Optional<TooltipComponent> 参数被 TODO 忽略——传入的 BundleTooltip 等组件静默丢失；
- ItemToast 的 2 倍缩放物品图标渲染被注释掉（TODO）——当前只绘制文字，与高版本 ItemToast 契约（含物品图标）不完整；
- `TabBar.drawSingleTabButton` 为 @Deprecated 死方法（仅被自身注释为兼容）；`AbstractComponent.render()` @Deprecated 桥接属迁移残留；
- WidgetSprites/ScrollbarSettings 字段语义与 26.1.2 record 核对一致，无漂移。

**修复**：删除 TabBar.drawSingleTabButton 死代码；实现或删除 component 参数；补全 ItemToast 图标渲染；render() 桥接在全部子类迁移到 renderWidget 后移除。

### S10. StringWidget 缺文本溢出模式
[StringWidget.java#L49-L71](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/StringWidget.java)：高版本支持 `setMaxWidth(width, TextOverflow.CLAMPED/SCROLLING)`（滚动文本），CatFrame 仅单行截断。补 TextOverflow 枚举与 setMaxWidth。

### S11. Checkbox 缺两行标签能力
[Checkbox.java#L170-L180](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/Checkbox.java)：高版本经 MultiLineTextWidget 支持 maxRows=2 换行（长选项文本），CatFrame 单行截断。待 W22 的 MultiLineTextWidget 补上后对接。

### S12. Toast.getToken 默认值语义差异需文档化
[Toast.java#L65-L67](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/Toast.java)：CatFrame 默认 `getToken() = this`（每实例唯一 → 不去重），高版本默认 `NO_TOKEN`（显式不去重）。CatFrame 语义更直观但属未披露偏差，建议在 ToastManager 文档中注明去重契约。

### S13. 【已核验】未逐行核对项（第二轮已全部核验）
Button、TabButton、TabBar、TabManager、ToastManager、SystemToast、ObjectSelectionList、ContainerObjectSelectionList、ScrollableLayout、WidgetSprites、ScreenRectangle——**11 项已于第二轮逐行对照 recompSrc 原版源码核验完毕，结论见 3.2 核验表**（无一项完全对齐；相关条目：W24-W36、S14-S22）。

### S14. WidgetSprites 3 参构造回退参数漂移
[WidgetSprites.java#L41-L43](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/WidgetSprites.java)

四态 `get()` 逐分支与 26.1.2 record 完全一致（见 3.1 勘误表第 6 行），但 3 参便捷构造回退配对漂移：CatFrame `highlightedDisabled = highlighted`，高版本为 `disabledFocused = disabled`。使用 3 参构造的组件（Button 等）在"禁用且悬停/聚焦"态会显示错误的纹理分支。按高版本改为 `disabledFocused = disabled`（4 参构造 L51-L57 无需动）。

### S15. SelectionList 族缺 nextFocusPath/narration，Entry.mouseClicked 为空覆写存根
[ObjectSelectionList.java](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ObjectSelectionList.java) · [ContainerObjectSelectionList.java](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ContainerObjectSelectionList.java)

两个 SelectionList 均未覆写 `nextFocusPath`（方向键候选导航不可用，缺口已列 W16）；无 narration 挂钩；`Entry.mouseClicked` 为空覆写存根——条目自身"可点击"契约被静默吞掉。待 C2 键盘路径修复后，`nextFocusPath` 是键盘列表导航的必要条件，建议一并补。

### S16. TabManager 初始 tab 按最小 ID 选择，与按钮注册顺序可能不一致
[TabManager.java#L185-L193](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/tab/TabManager.java)

`initFromRegistry` 注释称"默认第一个注册的"，实现却是 `getSortedTabIds()` 的最小 ID（L187-193）——若 tabId 与注册顺序错位（如先注册 id=3 再注册 id=1），初始选中与第一个按钮不一致；`reinitializeTabs`（L407-413）同样如此。建议初始 tab 取注册序第一个，或文档化"tabId 须连续递增"约束。

### S17. ToastManager 音效播放无每帧限流
[ToastManager.java#L187-L202](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/toast/ToastManager.java)

可见性切换即播放滑入/滑出音效（L64 调用点、L187-202 播放），连续入队（如批量合成产出）时多帧连发——高版本语义为每帧至多播放 1 次。在播放处加"每 tick 至多 1 次"门限即可。

### S18. ScrollableLayout.arrangeElements 双重布局
[ScrollableLayout.java#L82-L85](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ScrollableLayout.java) · [ScrollableLayout.java#L71-L78](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ScrollableLayout.java)

`arrangeElements()` 调 `content.arrangeElements()` 后 `recalculate()`，而 `recalculate()`（L71-78）内部又调一次 `content.arrangeElements()`（L73）——内容被布局两次；对动态尺寸元素（自动换行文本等）两次结果可能不一致，且叠加 W7 的 setScrollAmount 偏移。建议与高版本一致：内容尺寸在布局前确定，`arrangeElements` 只做一次定位。

### S19. ScrollableLayout 缺焦点自动滚动与 ReserveStrategy
[ScrollableLayout.java](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/ScrollableLayout.java)

高版本 `scrollToFocus(FocusNavigationEvent)`（焦点移出可见区自动滚动，依赖 ScreenRectangle 运算）与 `ReserveStrategy`（列表项高度未知时的估算滚动策略）均缺失（全仓 grep 无对应符号）。键盘 Tab 遍历长列表时焦点会移出裁剪区而不可见——依赖 C2 修复后的键盘路径，属后续增强。

### S20. Button 缺 Builder 便捷方法与 Plain 子类
[Button.java](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/components/Button.java)

高版本 Button 有 `builder()`（size/bounds/tooltip/icon 链式入口）与 `Plain`（无边框纯文本）子类；CatFrame 仅普通构造器，`Plain` 能力由 `useVanillaTexture(false)` 开关承担——功能等价但 API 面不齐。按需补 `builder()`。

### S21. 勘误：1.7.10 原版无药水 HUD；GuiButtonAdapter 无 pressed 状态残留
- 经 recompSrc 全文检索证实 1.7.10 GuiIngame **无**右上角药水效果图标段（该功能 1.8+ 才有）——上轮"Toast 与药水 HUD 重叠"担忧已排除（见 W35）；
- GuiButtonAdapter 不设原版 `GuiButton.field_146123_n`（按下状态）：该字段由 `drawButton` 每帧按鼠标坐标重算（[recompSrc GuiButton.java#L84](d:/GAMES/Minecraft/modss/project/CatFrame/build/tmp/recompSrc/net/minecraft/client/gui/GuiButton.java)），**不存在跨帧残留**——但按下视觉反馈也随之失效；真正缺口是 W32 的 actionPerformed 未触发，勿按"状态残留"方向处理。

### S22. 映射表勘误：setInitialFocus/clearFocus 已实现，仅 changeFocus 缺失
[Screen.java#L416-L430](d:/GAMES/Minecraft/modss/project/CatFrame/src/main/java/decok/dfcdvadstf/catframe/ui/screens/Screen.java)

2.1 映射表 Screen 行"缺 setInitialFocus/changeFocus/clearFocus"**过时**：`setInitialFocus()`（protected，初始化入口）、`setInitialFocus(GuiEventListener)`、`clearFocus()` 均已实现（L416-430）；仅 `changeFocus`（相对方向焦点移动，高版本供导航用）缺失。映射表应改为"缺 changeFocus"。

---

## 七、建议投入优先级

```
✅ 已落地（本次核验确认）
  1. 基础控件全齐：Checkbox / ImageWidget / StringWidget / AbstractStringWidget ✅
  2. 编辑框系全齐：AbstractEditBox / MultilineEditBox / MultilineTextField / SimpleEditBox ✅
  3. 布局 / Tab / Toast / 滚动容器 / 事件分发 / 焦点导航 ✅（主体）

第一梯队（🔴 Critical，立即修复）
  4. C2 keyTyped 断链（Screen 下键盘全断）  ← Screen.keyTyped 补 dispatch（C1 已证伪撤销，勿按旧方案实施）
  5. C3 客户端 Mixin 侧过滤（服务器崩溃）    ← JSON client 列表 + 删 getMixins()

第二梯队（🟡 Warning，近期修复）
  6. W1 Scissor 缩放、W2 TabBar Ctrl+数字、W3-W7 编辑/滚动/列表 Bug 群（W8 已证伪撤销）
  7. W9 Overlay 延迟管线优先级倒挂、W10 IME 只扫 Overlay、W11 事件桥收敛、W12 左 Ctrl
  8. W13 isActive() 契约、W14-W17 接口缺口（事件载荷/EditBox/SelectionList/CyclingButton）
  9. W21 AbstractSliderButton 滑块            ← 唯一无替代的高频基础控件
  10. W24-W36 本轮新增（TabBar 接线/定位、Toast 族、GuiButtonAdapter、GL 泄漏、事件桥全局键、buttonList 层级）

第三梯队（🟢 按需投入）
  11. W20 Narration 最小体系（3 文件起步）
  12. W22 多行只读文本组件、W23 IME preedit + ActiveTextCollector
  13. S1-S22 清理与优化项（S13 已核验；S14-S22 为本轮新增）
```

---

## 附：本次扫描范围与未覆盖说明

- **扫描范围**：`src/main/java/decok/dfcdvadstf/catframe/ui/` 全部子包（components、components/events、components/tab、components/contextualbar、layout、toast、tooltip、navigation、overlay、screens 等）+ 相关 Mixin（MixinGuiScreen、MixinGuiScreenEventBridge）+ compact 层（ClientScreenGraphicsHandler、ClientOverlayHandler、IMECompact、GuiGraphicsExtractor）+ mixins.catframe.json / mixin.gradle。
- **参考基准**：`markdown/minecraft-clientOnly-52430b475d-26.1.2-sources/net/minecraft/client/gui/`（deobf 源码）。
- **未覆盖**：原版 1.7.10 GuiScreen/GuiContainer 自身行为（输入时序已用 recompSrc 局部验证，见 3.1）、非 UI 的模型/渲染管线（已有独立文档）。S13 所列 11 项已于第二轮逐行核对完毕（见 3.2），**无遗留未核对项**。
