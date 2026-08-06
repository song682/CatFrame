package decok.dfcdvadstf.catframe.compact.ime;

import city.windmill.ingameime.ClientProxy;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.compact.CompactBase;
import decok.dfcdvadstf.catframe.ui.components.AbstractEditBox;
import decok.dfcdvadstf.catframe.ui.components.AbstractTextAreaWidget;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;

/**
 * <p>
 * IngameIME 兼容层 —— 把 CatFrame 自绘文本框体系（{@link AbstractTextAreaWidget}
 * 及其子类，如 {@link AbstractEditBox} 与 {@code MultilineEditBox}）桥接进
 * IngameIME 的输入管线：任一可见 Overlay 的组件树中出现聚焦的文本区域时，
 * 通过 IngameIME 的 {@code onControlFocus} 进入其 OpenedAuto 状态机（聚焦自动
 * 激活、失焦自动关闭，Home 手动模式不受干扰），并每 tick 同步光标位置，使
 * 预编辑文本/输入模式指示器跟随 CatFrame 文本框。
 * </p>
 * <p>
 * IngameIME compat — bridges CatFrame's self-drawn text-area family
 * ({@link AbstractTextAreaWidget} and subclasses such as
 * {@link AbstractEditBox}
 * and {@code MultilineEditBox}) into the IngameIME input pipeline: a focused
 * text
 * area inside any visible Overlay drives IngameIME's {@code onControlFocus}
 * into
 * its OpenedAuto state machine (focus activates the IME, blur deactivates it,
 * the
 * manual Home toggle is left untouched), and the caret is synced every tick so
 * the
 * pre-edit text / input-mode indicator follows the CatFrame text box.
 * </p>
 * <p>
 * 与 IMEInputBackport 互斥：当 IMEInputBackport 也在场时本层不注册，避免两个 IME
 * 同时激活导致同一段输入被提交两次（两者提交路径不同：IngameIME 走
 * {@code callKeyTyped}，IMEInputBackport 走 {@code IMECommitTarget}）。<br>
 * Mutually exclusive with IMEInputBackport: when it is present this layer does
 * not
 * register, so the two IMEs never activate together and double-commit the same
 * input (IngameIME commits via {@code callKeyTyped}, IMEInputBackport via
 * {@code IMECommitTarget}).
 * </p>
 * <p>
 * 线程模型：所有方法由客户端 tick（主线程）调用；IngameIME 的 {@code ClientProxy}
 * 在其自身的 preInit 中完成初始化，远早于本类的首次 tick，故访问安全。本类在
 * ClientProxy.preInit 注册一次，客户端生命周期内无需注销。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class IgIMECompact {

    /** 单例：注册进 Forge 事件总线的实例 */
    private static final IgIMECompact INSTANCE = new IgIMECompact();

    /** 上一 tick 的聚焦文本区域；用于检测焦点变化 */
    @Nullable
    private AbstractTextAreaWidget lastFocused = null;

    private IgIMECompact() {
    }

    /**
     * IngameIME 已加载且 IMEInputBackport 未加载时注册桥接（客户端初始化调用一次）。
     * Registers the bridge when IngameIME is loaded and IMEInputBackport is not
     * (call once at client init).
     */
    public static void register() {
        if (isLoaded() && !CompactBase.isIMEBackportInstalled()) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
        }
    }

    /**
     * IngameIME 是否已加载（modid = {@code ingameime}）。
     * Whether IngameIME is loaded (modid {@code ingameime}).
     *
     * @return true = 已加载
     */
    public static boolean isLoaded() {
        return CompactBase.isIGIMEInstalled();
    }

    /**
     * 每客户端 tick 驱动焦点桥接与光标同步。
     * Drives the focus bridge and caret sync once per client tick.
     *
     * @param event tick 事件 / tick event
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        // IngameIME 在屏幕关闭时已自行 Disabled（其 MixinMinecraft.displayGuiScreen
        // 注入），此处只清引用，绝不能调 onControlFocus——那会让它重新激活。
        // IngameIME already disabled itself when the screen closed (its
        // MixinMinecraft.displayGuiScreen hook); only drop the reference here —
        // calling onControlFocus would re-activate it.
        if (mc.currentScreen == null) {
            lastFocused = null;
            return;
        }

        AbstractTextAreaWidget area = IMECompact.findFocusedArea();
        if (area != lastFocused) {
            ClientProxy proxy = ClientProxy.INSTANCE;
            if (proxy != null) {
                // 先通知旧控件失焦、再通知新控件聚焦，保证 OpenedAuto 状态机按
                // ActiveControl 匹配正确收尾。
                // Notify the old control's blur first, then the new one's focus,
                // so the OpenedAuto state machine matches on ActiveControl.
                if (lastFocused != null) {
                    proxy.onControlFocus(lastFocused, false);
                }
                if (area != null) {
                    proxy.onControlFocus(area, true);
                }
            }
            lastFocused = area;
        }

        if (area != null) {
            ClientProxy.Screen.setCaretPos(caretX(area), caretY(area));
        }
    }

    /**
     * 光标 X（GUI 逻辑坐标）：组件左沿 + 内边距，单行框再叠加光标前文本宽度。
     * Caret X (GUI units): component left edge + inner padding, plus the
     * caret-prefix text width for single-line boxes.
     *
     * @param area 聚焦文本区域 / focused text area
     * @return 光标 X / caret X
     */
    private static int caretX(AbstractTextAreaWidget area) {
        int x = area.getX() + area.innerPadding();
        if (area instanceof AbstractEditBox) {
            AbstractEditBox box = (AbstractEditBox) area;
            String text = box.getText();
            int cursor = Math.min(box.getCursorPosition(), text.length());
            x += Minecraft.getMinecraft().fontRenderer.getStringWidth(text.substring(0, cursor));
        }
        return x;
    }

    /**
     * 光标 Y（GUI 逻辑坐标）：文本框底沿，对齐 IngameIME 对 vanilla 字段的默认回退。
     * Caret Y (GUI units): bottom edge of the text area, mirroring the IngameIME
     * fallback for vanilla fields.
     *
     * @param area 聚焦文本区域 / focused text area
     * @return 光标 Y / caret Y
     */
    private static int caretY(AbstractTextAreaWidget area) {
        return area.getY() + area.getHeight();
    }
}
