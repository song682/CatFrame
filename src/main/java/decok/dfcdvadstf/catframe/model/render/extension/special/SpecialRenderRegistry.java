package decok.dfcdvadstf.catframe.model.render.extension.special;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * per-Item 完全自绘 provider 注册表（对称 {@code TintRegistry}）。
 * <p>
 * Copy-on-write provider table：写入稀少（mod init）且加锁；读取发生在渲染路径且从任意
 * 线程看到一个稳定的不可变快照，无需加锁。
 * <p>
 * {@link #registerItem(Item, IItemRenderSpecialProvider)} 除了登记 provider，还会把该
 * item 路由到 {@link RenderJsonItemModel}（{@code MinecraftForgeClient.registerItemRenderer}），
 * 否则一个纯自绘、未注册通用模型的 item 不会进入 CatFrame 物品渲染入口。
 */
@SideOnly(Side.CLIENT)
public final class SpecialRenderRegistry {

    /**
     * Copy-on-write provider table. Writes are rare (mod init) and synchronized;
     * reads happen on the render path from arbitrary threads and see a stable
     * immutable map snapshot without locking.
     */
    private static volatile Map<Item, IItemRenderSpecialProvider> ITEM_PROVIDERS = Collections.emptyMap();

    private SpecialRenderRegistry() {
    }

    // ==================== 注册 API ====================

    /**
     * 注册一个 per-Item 完全自绘 provider，并确保该 item 路由到 CatFrame 物品渲染入口。
     *
     * @param item     目标物品
     * @param provider 自绘 provider
     */
    public static synchronized void registerItem(Item item, IItemRenderSpecialProvider provider) {
        if (item == null || provider == null) return;
        Map<Item, IItemRenderSpecialProvider> copy = new HashMap<>(ITEM_PROVIDERS);
        copy.put(item, provider);
        ITEM_PROVIDERS = copy;
        // 纯自绘 item 可能不在 registeredItemModels 中，需显式接管 Forge 渲染路由
        MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
    }

    /**
     * 注销 provider（不回收 Forge 渲染路由，交由通用注册流程管理）。
     *
     * @param item 目标物品
     */
    public static synchronized void unregisterItem(Item item) {
        if (item == null || !ITEM_PROVIDERS.containsKey(item)) return;
        Map<Item, IItemRenderSpecialProvider> copy = new HashMap<>(ITEM_PROVIDERS);
        copy.remove(item);
        ITEM_PROVIDERS = copy;
    }

    // ==================== Provider lookup ====================

    /** 当前 item 的自绘 provider，未注册时为 {@code null}。 */
    public static IItemRenderSpecialProvider getProvider(Item item) {
        return item == null ? null : ITEM_PROVIDERS.get(item);
    }
}
