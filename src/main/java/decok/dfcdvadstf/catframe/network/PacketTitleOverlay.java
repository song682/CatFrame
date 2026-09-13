package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * <p>
 * 服务端→客户端 Title / ActionBar 覆盖层包。携带一个 action 类型标识、
 * 可选的 JSON 文本内容和可选的计时参数，由客户端处理器触发已有的
 * {@code TitleOverlay} / {@code ActionBarOverlay} 渲染。
 * </p>
 * <p>
 * Server → Client packet for Title / ActionBar overlay. Carries an action type,
 * optional JSON text content and optional timing parameters; the client handler
 * triggers the existing {@code TitleOverlay} / {@code ActionBarOverlay} rendering.
 * </p>
 *
 * <h3>Wire format / 线路格式</h3>
 * <ol>
 *   <li>{@code byte} — action ordinal (see {@link Action})</li>
 *   <li>{@code UTF8 string} — JSON text (empty string for CLEAR / RESET / TIMES)</li>
 *   <li>{@code int} — fadeIn ticks (meaningful only for TIMES)</li>
 *   <li>{@code int} — stay ticks (meaningful only for TIMES)</li>
 *   <li>{@code int} — fadeOut ticks (meaningful only for TIMES)</li>
 * </ol>
 */
public class PacketTitleOverlay implements IMessage {

    /**
     * Action types for the title overlay packet.
     * <p>标题覆盖层包的动作类型。</p>
     */
    public enum Action {
        TITLE,
        SUBTITLE,
        ACTIONBAR,
        TIMES,
        CLEAR,
        RESET
    }

    private Action action;
    private String textJson;
    private int fadeIn;
    private int stay;
    private int fadeOut;

    /** No-arg constructor required by Forge / Forge 要求的无参构造 */
    public PacketTitleOverlay() {
    }

    public PacketTitleOverlay(Action action, String textJson, int fadeIn, int stay, int fadeOut) {
        this.action = action;
        this.textJson = textJson;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    /** Convenience: text-only actions / 便捷：纯文本动作 */
    public static PacketTitleOverlay text(Action action, String textJson) {
        return new PacketTitleOverlay(action, textJson, 0, 0, 0);
    }

    /** Convenience: no-arg actions / 便捷：无参动作 */
    public static PacketTitleOverlay simple(Action action) {
        return new PacketTitleOverlay(action, "", 0, 0, 0);
    }

    /** Convenience: times-only action / 便捷：纯计时动作 */
    public static PacketTitleOverlay times(int fadeIn, int stay, int fadeOut) {
        return new PacketTitleOverlay(Action.TIMES, "", fadeIn, stay, fadeOut);
    }

    // ──── Getters ────

    public Action getAction() { return action; }
    public String getTextJson() { return textJson; }
    public int getFadeIn() { return fadeIn; }
    public int getStay() { return stay; }
    public int getFadeOut() { return fadeOut; }

    // ──── IMessage ────

    @Override
    public void fromBytes(ByteBuf buf) {
        action = Action.values()[buf.readByte()];
        textJson = ByteBufUtils.readUTF8String(buf);
        fadeIn = buf.readInt();
        stay = buf.readInt();
        fadeOut = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        ByteBufUtils.writeUTF8String(buf, textJson);
        buf.writeInt(fadeIn);
        buf.writeInt(stay);
        buf.writeInt(fadeOut);
    }
}
