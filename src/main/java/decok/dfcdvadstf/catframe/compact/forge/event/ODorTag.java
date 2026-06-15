package decok.dfcdvadstf.catframe.compact.forge.event;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.compact.forge.tags.OreDict2Tag;
import net.minecraftforge.oredict.OreDictionary;

public class ODorTag {

    @SubscribeEvent
    public static void onInit() {
        CatFrame.logger.info("Converting all existing OreDict entries to CatFrame Tags...");
        OreDict2Tag.convertAllOreDictToTags();
    }

    @SubscribeEvent
    public static void onOreRegistered(OreDictionary.OreRegisterEvent event){
        String[] universalOreDicts={
                // ingot
                "ingotCopper", "ingotBronze", "ingotTin", "ingotAluminum", "ingotAluminium",
                "ingotAluminumBrass", "ingotAluminiumBrass", "ingotNetherite", "ingotCobalt",
                "ingotSilver", "ingotLead", "ingotNickel",
                // ore
                "oreCopper", "oreTin", "oreAluminum", "oreAluminium", "oreCobalt",
                "oreSilver", "oreLead", "oreNickel",
                // block
                "blockCopper", "blockBronze", "blockTin", "blockAluminum", "blockAluminium",
                "blockAluminumBrass", "blockAluminiumBrass", "blockNetherite", "blockCobalt",
                "blockSilver", "blockLead", "blockNickel",
        };
        OreDict2Tag.convertOreDictToTags(universalOreDicts);
    }
}
