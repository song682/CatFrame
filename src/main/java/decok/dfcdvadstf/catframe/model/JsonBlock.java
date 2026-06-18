package decok.dfcdvadstf.catframe.model;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.render.RenderRequest;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JsonBlock {
    public static final Map<String, IIcon> iconMap = new HashMap<>();
    public static final Map<String, String> iconNameMap = new HashMap<>();
    public static final Map<IBlockJsonModel, Integer> IDMap = new HashMap<>();
    public static final Gson gson = new Gson();
    public static int jsonID = 90000;
    public static List<RenderRequest> renderRequest = new ArrayList<>();

    @SideOnly(Side.CLIENT)
    public static void register(final IBlockJsonModel block, boolean randomRotation, boolean renderItem,
                                boolean autoRotationY, int rotation, String... name) {
        if (FMLLaunchHandler.side().isServer()) {
            throw new IllegalStateException("You can only register blocks model in client.");
        }

        Objects.requireNonNull(block);

        String modId = Loader.instance().activeModContainer().getModId();
        if (Objects.isNull(modId) || modId.isEmpty()) {
            modId = "minecraft";
        }


        int size = name.length;
        ModelJson[] models = new ModelJson[size];
        for (int s = 0; s < size; s++) {
            CatFrame.logger.info("register json block model : {}", name[s]);
            final Path path = Paths.get("assets", modId, "textures", "json", "block",
                    name[s] + ".json");

            try (final InputStream stream = JsonBlock.class.getResourceAsStream(path.toString())) {
                if (Objects.isNull(stream)) {
                    continue;
                }
                final InputStreamReader reader = new InputStreamReader(stream);
                models[s] = gson.fromJson(reader, ModelJson.class);
                if (Objects.isNull(models[s]) || Objects.isNull(models[s].textures)) {
                    continue;
                }

                String finalModId = modId;
                models[s].textures.forEach((textureID, textureName) -> {
                    final String texName = resolveTexturePath(textureName, finalModId);
                    Minecraft.getMinecraft().getTextureMapBlocks().registerIcon(texName);
                    iconNameMap.put(textureID, texName);
                    CatFrame.logger.info("Register texture pool ID : {} name : {}", textureID, textureName);
                });
            } catch (JsonSyntaxException | JsonIOException | IOException e) {
                CatFrame.logger.error(e);
            }
        }
        renderRequest.add(new RenderRequest(jsonID, name, models, autoRotationY, false, rotation, randomRotation, renderItem));
        IDMap.put(block, jsonID);
        jsonID++;
    }

    protected static void registerJsonBlock(RenderRequest RR) {
        int size = RR.models.length;

        Map<String, IIcon> iconMapTemp = new HashMap<String, IIcon>();
        ModelJson MJ;
        for (int s = 0; s < size; s++) {
            MJ = RR.models[s];
            if (Objects.isNull(MJ.textures)) {
                continue;
            }

            MJ.textures.forEach((textureID, textureName) -> {
                if (iconMap.containsKey(textureID)) {
                    iconMapTemp.put(textureID, iconMap.get(textureID));
                } else {
                    CatFrame.logger.info("can't find texture from pool ID : {} name : {}", textureID, textureName);
                }
            });
        }

        // Bake quads for VMM registration (ISBRH no longer stores its own copy)
        // [C9 修复] 使用追加模式 + 空值检查，避免 metadata 空洞时 IndexOutOfBoundsException
        List<List<BakedQuad>> quads = Lists.newArrayList();
        for (int s = 0; s < size; s++) {
            if (RR.models[s] == null || RR.models[s].elements == null) {
                quads.add(new ArrayList<>());
                continue;
            }
            List<BakedQuad> bakedQuadsTemp = new ArrayList<>();
            for (ModelJson.Element e : RR.models[s].elements) {
                bakedQuadsTemp.addAll(BlockJsonModelBake.bakeElement(e, iconMapTemp));
            }
            quads.add(bakedQuadsTemp);
        }

        // Register ISBRH — delegates rendering to VMM + UniformRenderPipeline
        RenderingRegistry.registerBlockHandler(RR.ID, new RenderJsonBlockModel(RR.ID, RR.renderItem));

        // --- New path: also register in BlockStateModel system ---
        // Find the block that uses this render ID
        for (Map.Entry<IBlockJsonModel, Integer> entry : IDMap.entrySet()) {
            if (entry.getValue().equals(RR.ID)) {
                Block block = (Block) entry.getKey();
                Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
                for (int s = 0; s < size; s++) {
                    partMap.put(s, BlockStateModelPart.fromQuads(quads.get(s)));
                }
                BlockStateModelPart fallback = partMap.get(0);
                if (fallback == null && !partMap.isEmpty()) {
                    fallback = partMap.values().iterator().next();
                }
                VanillaModelManager.ModelRegistration.registerBlockModel(block,
                        new MetadataBlockModel(partMap, fallback));

                // Register per-metadata rotation & overlay flags
                if (RR.autoOverlay) {
                    VanillaModelManager.ModelRegistration.markAutoOverlay(block);
                }
                if (RR.randomRotation) {
                    VanillaModelManager.ModelRegistration.markRandomRotation(block);
                    VanillaModelManager.ModelRegistration.registerBlockRotation(block, 0, 0);
                } else {
                    for (int meta = 0; meta < size; meta++) {
                        int rot = RR.autoRotationY ? 90 * (meta % 4) : RR.rotation;
                        VanillaModelManager.ModelRegistration.registerBlockRotation(block, meta, rot);
                    }
                }
                break;
            }
        }
    }

    public static void event() {
        for (RenderRequest request : renderRequest) {
            registerJsonBlock(request);
        }
    }

    /**
     * Resolve a texture path to a usable icon name.
     * Supports formats:
     * "modid:blocks/name" -> "name"
     * "minecraft:blocks/name" -> "name"
     * "blocks/name" -> "name"
     * "name" -> "name"
     */
    public static String resolveTexturePath(String texturePath, String modId) {
        if (texturePath == null) return "";
        // Remove namespace prefix
        if (texturePath.contains(":")) {
            String namespace = texturePath.substring(0, texturePath.indexOf(':'));
            texturePath = texturePath.substring(texturePath.indexOf(':') + 1);
            // If it's not minecraft namespace, keep modid prefix for custom textures
            if (!namespace.equals("minecraft") && !namespace.equals(modId)) {
                texturePath = namespace + ":" + texturePath;
            }
        }
        // Remove "blocks/" prefix for vanilla icon resolution
        if (texturePath.startsWith("blocks/")) {
            texturePath = texturePath.substring("blocks/".length());
        }
        return texturePath;
    }

    /**
     * Register textures needed by the vanilla model manager.
     * Call during TextureStitchEvent.Pre.
     */
    public static void registerVanillaTextures(TextureMap map) {
        VanillaModelManager.TextureManagement.registerTextures(map);
    }

    /**
     * Called after texture stitching to collect IIcon references for vanilla models.
     */
    public static void onTextureStitchPost(TextureMap map) {
        // Populate iconMap from the texture map
        iconNameMap.forEach((id, name) -> {
            IIcon icon = map.getAtlasSprite(name);
            if (icon != null) {
                iconMap.put(id, icon);
            }
        });

        // Trigger vanilla model baking
        VanillaModelManager.TextureManagement.onTextureStitchPost(map);
    }
}
