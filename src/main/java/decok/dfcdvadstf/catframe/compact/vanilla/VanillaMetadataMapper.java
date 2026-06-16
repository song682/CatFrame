package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.init.Blocks;

import java.util.HashMap;
import java.util.Map;

public class VanillaMetadataMapper {

    @SideOnly(Side.CLIENT)
    public static void registerVanillaMetadataMappings() {
        // log: low 2 bits = wood type, high 2 bits = rotation (0=y, 1=x, 2=z, 3=bark→y)
        final String[] woods = {"oak", "spruce", "birch", "jungle"};
        final String[] axes = {"y", "x", "z"};
        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.log, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods[meta & 3]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // log2: low 1 bit = wood type, high 2 bits = rotation (same scheme)
        final String[] woods2 = {"acacia", "dark_oak"};
        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.log2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods2[meta & 1]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // leaves: meta & 3 = variant (oak/spruce/birch/jungle), bits 4-15 = decay state
        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.leaves, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", woods[meta & 3]);
            return props;
        });

        // leaves2: meta & 1 = variant (acacia/dark_oak), bits 4-15 = decay state
        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.leaves2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", woods2[meta & 1]);
            return props;
        });

        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.wool, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", "white"); 
            return props;
        });
    }
}
