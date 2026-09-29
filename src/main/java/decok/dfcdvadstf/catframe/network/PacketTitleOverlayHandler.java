package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.Title;

/**
 * <p>
 * Client-side handler — receives {@link PacketTitleOverlay} from the server and
 * calls the existing {@link Title} API to trigger overlay rendering.
 * </p>
 */
public class PacketTitleOverlayHandler implements IMessageHandler<PacketTitleOverlay, IMessage> {

    @Override
    public IMessage onMessage(PacketTitleOverlay message, MessageContext ctx) {
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
        return null;
    }
}
