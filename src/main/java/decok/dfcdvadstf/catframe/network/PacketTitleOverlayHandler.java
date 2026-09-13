package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.Title;

/**
 * <p>
 * 客户端处理器 —— 接收来自服务端的 {@link PacketTitleOverlay} 并调用已有的
 * {@link Title} / {@link decok.dfcdvadstf.catframe.ui.ActionBar} 公共 API
 * 触发覆盖层渲染。
 * </p>
 * <p>
 * Client-side handler — receives {@link PacketTitleOverlay} from the server and
 * calls the existing {@link Title} / {@link decok.dfcdvadstf.catframe.ui.ActionBar}
 * public API to trigger overlay rendering.
 * </p>
 */
public class PacketTitleOverlayHandler implements IMessageHandler<PacketTitleOverlay, IMessage> {

    @Override
    public IMessage onMessage(PacketTitleOverlay message, MessageContext ctx) {
        // All Title/ActionBar state lives in client singletons; the handler simply
        // delegates to the public API which touches those singletons on the client thread.
        // 所有 Title/ActionBar 状态都在客户端单例中；处理器直接委托给公共 API，
        // 由其在客户端线程上操作这些单例。
        switch (message.getAction()) {
            case TITLE:
                Title.show(Text.fromJson(message.getTextJson()));
                break;
            case SUBTITLE:
                Title.subtitle(Text.fromJson(message.getTextJson()));
                break;
            case ACTIONBAR:
                Title.actionbar(Text.fromJson(message.getTextJson()));
                break;
            case TIMES:
                Title.times(message.getFadeIn(), message.getStay(), message.getFadeOut());
                break;
            case CLEAR:
                Title.clear();
                break;
            case RESET:
                Title.reset();
                break;
        }
        return null; // no reply / 无需回复
    }
}
