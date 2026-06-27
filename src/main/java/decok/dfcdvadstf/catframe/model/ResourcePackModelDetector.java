package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听资源管理器重载，检测顶层资源包提供的模型 JSON 和 BlockState JSON 内容。
 * <p>
 * 当玩家加载/切换资源包时，自动扫描已注册命名空间中已知的模型和 blockstate 路径，
 * 使用 {@link IResourceManager#getResource(ResourceLocation)} 获取顶层资源包中的版本，
 * 解析为 {@link ModelJson} / {@link BlockstateJson} 供其他子系统查询。
 * <p>
 * 注册方式完全复用 {@link decok.dfcdvadstf.catframe.compact.vanilla.LanguageReloadListener}
 * 的延迟注册模式：资源管理器不可用时通过一次性 ClientTick 延迟注册。
 */
public class ResourcePackModelDetector implements IResourceManagerReloadListener {

    /** namespace:path → 顶层资源包提供的 ModelJson */
    public static final Map<String, ModelJson> PACK_MODELS = new ConcurrentHashMap<>();
    /** namespace:blockName → 顶层资源包提供的 BlockstateJson */
    public static final Map<String, BlockstateJson> PACK_BLOCKSTATES = new ConcurrentHashMap<>();
    /** 顶层资源包覆盖的模型路径集合 */
    public static final Set<String> PACK_MODEL_PATHS = ConcurrentHashMap.newKeySet();
    /** 顶层资源包覆盖的 blockstate 路径集合 */
    public static final Set<String> PACK_BLOCKSTATE_PATHS = ConcurrentHashMap.newKeySet();

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        CatFrame.logger.debug("ResourcePackModelDetector: resource manager reloaded, scanning...");
        clear();
        scanAllNamespaces(manager);
        CatFrame.logger.info("ResourcePackModelDetector: detected {} model overrides, {} blockstate overrides",
                PACK_MODEL_PATHS.size(), PACK_BLOCKSTATE_PATHS.size());
    }

    private static void clear() {
        PACK_MODELS.clear();
        PACK_BLOCKSTATES.clear();
        PACK_MODEL_PATHS.clear();
        PACK_BLOCKSTATE_PATHS.clear();
    }

    private static void scanAllNamespaces(IResourceManager manager) {
        for (String ns : VanillaModelManager.namespaces) {
            scanNamespace(manager, ns);
        }
    }

    private static void scanNamespace(IResourceManager manager, String ns) {
        // 扫描已知 BlockStates
        Map<String, BlockstateJson> nsBlockstates = VanillaModelManager.loadedBlockstates.get(ns);
        if (nsBlockstates != null) {
            for (String blockName : nsBlockstates.keySet()) {
                ResourceLocation loc = new ResourceLocation(ns, "blockstates/" + blockName + ".json");
                try {
                    IResource topResource = manager.getResource(loc);
                    BlockstateJson bs = VMMDataLoader.blockstateGson.fromJson(
                            new InputStreamReader(topResource.getInputStream()),
                            BlockstateJson.class);
                    if (bs != null) {
                        String key = ns + ":" + blockName;
                        PACK_BLOCKSTATES.put(key, bs);
                        PACK_BLOCKSTATE_PATHS.add(key);
                    }
                } catch (Exception ignored) {
                    // 顶层资源包未覆盖此 blockstate
                }
            }
        }

        // 扫描已知模型路径
        VanillaModelManager.ModelMappings mappings = VanillaModelManager.loadedMappings.get(ns);
        if (mappings != null) {
            if (mappings.blocks != null) {
                for (String modelPath : mappings.blocks.values()) {
                    scanModel(manager, modelPath);
                }
            }
            if (mappings.items != null) {
                for (String modelPath : mappings.items.values()) {
                    scanModel(manager, modelPath);
                }
            }
        }
    }

    private static void scanModel(IResourceManager manager, String modelPath) {
        if (modelPath == null) return;
        String ns, path;
        if (modelPath.contains(":")) {
            ns = modelPath.substring(0, modelPath.indexOf(':'));
            path = modelPath.substring(modelPath.indexOf(':') + 1);
        } else {
            ns = "minecraft";
            path = modelPath;
        }
        ResourceLocation loc = new ResourceLocation(ns, "models/" + path + ".json");
        try {
            IResource topResource = manager.getResource(loc);
            ModelJson model = ModelResolver.GSON.fromJson(
                    new InputStreamReader(topResource.getInputStream()),
                    ModelJson.class);
            if (model != null) {
                String key = ns + ":" + path;
                PACK_MODELS.put(key, model);
                PACK_MODEL_PATHS.add(key);
                CatFrame.logger.debug("ResourcePackModelDetector: detected top-pack model '{}'", key);
            }
        } catch (Exception ignored) {
            // 顶层资源包未覆盖此模型
        }
    }

    // ==================== 注册 ====================

    /**
     * 注册此监听器到 Minecraft 资源管理器。
     * 安全地在 mod init 调用 —— 若资源管理器未就绪，通过一次性 Tick 延迟注册。
     */
    public static void register() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) mc.getResourceManager())
                    .registerReloadListener(new ResourcePackModelDetector());
            CatFrame.logger.info("ResourcePackModelDetector: registered with resource manager");
        } else {
            CatFrame.logger.debug("ResourcePackModelDetector: resource manager not ready, deferring");
            MinecraftForge.EVENT_BUS.register(new Object() {
                @SubscribeEvent
                public void onClientTick(TickEvent.ClientTickEvent event) {
                    if (event.phase == TickEvent.Phase.END) {
                        Minecraft mc2 = Minecraft.getMinecraft();
                        if (mc2 != null && mc2.getResourceManager() instanceof IReloadableResourceManager) {
                            ((IReloadableResourceManager) mc2.getResourceManager())
                                    .registerReloadListener(new ResourcePackModelDetector());
                            CatFrame.logger.info("ResourcePackModelDetector: registered (deferred)");
                            MinecraftForge.EVENT_BUS.unregister(this);
                        }
                    }
                }
            });
        }
    }

    // ==================== 查询 API ====================

    /** 顶层资源包是否覆盖了指定模型？ */
    public static boolean hasModelOverride(String ns, String path) {
        return PACK_MODEL_PATHS.contains(ns + ":" + path);
    }

    /** 顶层资源包是否覆盖了指定 blockstate？ */
    public static boolean hasBlockstateOverride(String ns, String blockName) {
        return PACK_BLOCKSTATE_PATHS.contains(ns + ":" + blockName);
    }

    /** 获取顶层资源包的模型 JSON，或 null */
    public static ModelJson getTopModel(String ns, String path) {
        return PACK_MODELS.get(ns + ":" + path);
    }

    /** 获取顶层资源包的 Blockstate JSON，或 null */
    public static BlockstateJson getTopBlockstate(String ns, String blockName) {
        return PACK_BLOCKSTATES.get(ns + ":" + blockName);
    }

    /** 获取所有被覆盖的模型路径（不可变视图，用于调试） */
    public static Set<String> getOverriddenModelPaths() {
        return Collections.unmodifiableSet(PACK_MODEL_PATHS);
    }

    /** 获取所有被覆盖的 blockstate 路径（不可变视图，用于调试） */
    public static Set<String> getOverriddenBlockstatePaths() {
        return Collections.unmodifiableSet(PACK_BLOCKSTATE_PATHS);
    }
}
