package decok.dfcdvadstf.catframe.ui.components.events;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class KeyTypedEvent {

    private KeyTypedEvent(){}

    public static boolean isWhichKeyPressed(int keyCode){
        return Keyboard.isKeyDown(keyCode);
    }

    public static boolean isControlKeyPressed(){
        return Minecraft.isRunningOnMac ? isWhichKeyPressed(Keyboard.KEY_LCONTROL) || isWhichKeyPressed(Keyboard.KEY_LMETA) : isWhichKeyPressed(Keyboard.KEY_RCONTROL) || isWhichKeyPressed(Keyboard.KEY_RMETA);
    }

    public static boolean isLControlKeyPressed(){
        return Minecraft.isRunningOnMac ? isWhichKeyPressed(Keyboard.KEY_LMETA) : isWhichKeyPressed(Keyboard.KEY_LCONTROL);
    }

    public static boolean isRControlKeyPressed(){
        return Minecraft.isRunningOnMac ? isWhichKeyPressed(Keyboard.KEY_RMETA) : isWhichKeyPressed(Keyboard.KEY_RCONTROL);
    }

    public static boolean isShiftKeyPressed(){
        return isWhichKeyPressed(Keyboard.KEY_LSHIFT) || isWhichKeyPressed(Keyboard.KEY_RSHIFT);
    }

    public static boolean isTabKeyPressed(){
        return isWhichKeyPressed(Keyboard.KEY_TAB);
    }
}
