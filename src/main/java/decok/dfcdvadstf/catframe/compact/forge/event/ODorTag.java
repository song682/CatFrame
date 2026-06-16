package decok.dfcdvadstf.catframe.compact.forge.event;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.compact.forge.tags.OreDict2Tag;
import net.minecraftforge.oredict.OreDictionary;

public class ODorTag {

    public static void onInit() {
        CatFrame.logger.info("Converting all existing OreDict entries to CatFrame Tags...");
        OreDict2Tag.convertAllOreDictToTags();
    }

    @SubscribeEvent
    public static void onOreRegistered(OreDictionary.OreRegisterEvent event){
        String oreName = event.Name;
        CatFrame.logger.info("Ore registered: " + oreName);
        OreDict2Tag.convertOreDictToTags(oreName);
    }
}
