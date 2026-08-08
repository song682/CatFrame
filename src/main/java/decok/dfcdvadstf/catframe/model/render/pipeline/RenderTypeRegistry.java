package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 渲染分组注册表 —— 运行时 render type 注册中心，对标原版 26w+ 延迟渲染管线中的
 * render type registry。
 * <p>
 * 取代改造前的 {@code RenderType} enum：内建分组（见 {@link #BLOCK_ATLAS_SOLID} 等静态字段）
 * 与第三方注册的分组统一以 {@link RenderTypeKey} 条目句柄暴露 —— submit 路径直接持
 * 条目引用（O(1) 零查找），flush 路径按<b>显式排序键</b>只读快照迭代。
 * <p>
 * <b>flush 顺序</b>：按 {@link RenderTypeKey#sortKey()} 升序（越小越先 flush），
 * 同键按注册顺序稳定排列 —— 精确复刻改造前 flush 序
 * （BLOCK_SOLID=0 → ITEM_SOLID=1 → BLOCK_TRANSLUCENT=2 → ITEM_TRANSLUCENT=3），
 * 保证半透明正确叠加在不透明之上。
 * <p>
 * <b>关于剔除（cull）</b>：面剔除开关不编码进分组，而是随每个
 * {@link RenderSubmit#disableCull} 携带（物品路径关剔除、方块路径保持），因为
 * 同一图集 + 混合分组下方块 GUI 与方块物品的剔除策略不同。flush 时按分组内提交项的
 * 剔除标志统一设置（作用域内提交项同质）。
 * <p>
 * <b>线程安全（v0.5+ 契约）</b>：内部 {@link CopyOnWriteArrayList} 存储条目（注册/更新
 * 可在任意线程执行，复合操作同步于类锁）；每次变更后重排并发布不可变快照
 * （{@code volatile} 数组），flush 路径只读快照、不排序 —— 后台烘焙线程的并发注册
 * 不会破坏渲染线程的迭代。
 * <p>
 * Runtime registry replacing the pre-refactor {@code RenderType} enum. Built-in
 * groups plus third-party registrations share one {@link RenderTypeKey} handle
 * model; flush iterates a volatile immutable snapshot ordered by sort key.
 */
public final class RenderTypeRegistry {

    /** 内建排序键：方块图集 · 不透明（0，最先 flush）。 */
    public static final int SORT_BLOCK_SOLID = 0;
    /** 内建排序键：物品图集 · 不透明（1）。 */
    public static final int SORT_ITEM_SOLID = 1;
    /** 内建排序键：方块图集 · 半透明（2）。 */
    public static final int SORT_BLOCK_TRANSLUCENT = 2;
    /** 内建排序键：物品图集 · 半透明（3，最后 flush）。 */
    public static final int SORT_ITEM_TRANSLUCENT = 3;

    /** 内建分组：方块图集 · 不透明（方块世界内联、方块物品手持等）。 */
    public static final RenderTypeKey BLOCK_ATLAS_SOLID;
    /** 内建分组：物品图集 · 不透明（普通物品手持/展示等无混合场景）。 */
    public static final RenderTypeKey ITEM_ATLAS_SOLID;
    /** 内建分组：方块图集 · 半透明（方块 GUI、方块物品 GUI/掉落/展示框）。 */
    public static final RenderTypeKey BLOCK_ATLAS_TRANSLUCENT;
    /** 内建分组：物品图集 · 半透明（物品 GUI/掉落/展示框）。 */
    public static final RenderTypeKey ITEM_ATLAS_TRANSLUCENT;

    /** 注册条目存储（写路径并发安全；遍历只在重建快照时进行）。 */
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    /** 排序快照（volatile 发布）：flush 路径只读迭代，注册表变更时重建。 */
    private static volatile RenderTypeKey[] orderedSnapshot = new RenderTypeKey[0];

    static {
        BLOCK_ATLAS_SOLID = register(
                "block_atlas_solid", TextureMap.locationBlocksTexture, false, SORT_BLOCK_SOLID);
        ITEM_ATLAS_SOLID = register(
                "item_atlas_solid", TextureMap.locationItemsTexture, false, SORT_ITEM_SOLID);
        BLOCK_ATLAS_TRANSLUCENT = register(
                "block_atlas_translucent", TextureMap.locationBlocksTexture, true, SORT_BLOCK_TRANSLUCENT);
        ITEM_ATLAS_TRANSLUCENT = register(
                "item_atlas_translucent", TextureMap.locationItemsTexture, true, SORT_ITEM_TRANSLUCENT);
    }

    private RenderTypeRegistry() {
    }

    /**
     * 注册（或更新）一个渲染分组。
     * <p>
     * 重复 id 视为更新：保留原注册序号（排序 tie-break 稳定），字段更新后重排快照。
     * 第三方模组可在客户端 init 阶段注册新分组（含自定义排序位置）。
     *
     * @param id        分组稳定标识（唯一注册键，如 {@code catframe:block_solid}）
     * @param atlas     绑定的纹理图集（blocks / items atlas）
     * @param blend     是否需要开启 alpha 混合（半透明）
     * @param sortKey   显式排序键：越小越先 flush；同键按注册顺序稳定排列
     * @return 注册表条目句柄（后续可直接用于提交与 flush 分组）
     */
    public static RenderTypeKey register(ResourceLocation id, ResourceLocation atlas,
                                         boolean blend, int sortKey) {
        if (id == null) {
            throw new IllegalArgumentException("render type id must not be null");
        }
        synchronized (RenderTypeRegistry.class) {
            for (Entry e : ENTRIES) {
                if (e.id.equals(id)) {
                    // 重复 id：更新字段（注册序号不变）→ 重排快照
                    e.update(atlas, blend, sortKey);
                    rebuildSnapshot();
                    return e;
                }
            }
            Entry entry = new Entry(id, ENTRIES.size(), atlas, blend, sortKey);
            ENTRIES.add(entry);
            rebuildSnapshot();
            return entry;
        }
    }

    /**
     * {@link #register(ResourceLocation, ResourceLocation, boolean, int)} 的便捷重载，
     * id 以 {@code catframe:<name>} 形式构造（与改造前 enum 的 {@code id()} 语义一致）。
     */
    public static RenderTypeKey register(String id, ResourceLocation atlas,
                                         boolean blend, int sortKey) {
        return register(new ResourceLocation("catframe", id), atlas, blend, sortKey);
    }

    /**
     * 按排序键升序遍历当前快照（只读，不排序不拷贝）。
     * 供 {@link SubmitNodeStorage#groups()} 与 flush 路径消费；快照由注册表在变更时发布。
     */
    public static void forEachInOrder(Consumer<RenderTypeKey> action) {
        RenderTypeKey[] snapshot = orderedSnapshot;
        for (RenderTypeKey key : snapshot) {
            action.accept(key);
        }
    }

    /**
     * 当前排序快照数组（只读约定：调用方不得修改数组或元素）。
     * 供同包槽位仲裁（handler 认领）等需按索引对齐的只读场景使用。
     */
    static RenderTypeKey[] orderedSnapshot() {
        return orderedSnapshot;
    }

    /**
     * 依据图集选择与混合需求解析内建分组（行为与改造前 {@code RenderType.of()} 一致）。
     *
     * @param blockAtlas  true=使用 blocks atlas，false=使用 items atlas
     * @param translucent true=需要混合（半透明），false=不透明
     */
    public static RenderTypeKey of(boolean blockAtlas, boolean translucent) {
        if (blockAtlas) {
            return translucent ? BLOCK_ATLAS_TRANSLUCENT : BLOCK_ATLAS_SOLID;
        }
        return translucent ? ITEM_ATLAS_TRANSLUCENT : ITEM_ATLAS_SOLID;
    }

    /** 变更后重排：sortKey 升序 + 注册序号 tie-break，发布为不可变快照。 */
    private static void rebuildSnapshot() {
        List<Entry> sorted = new ArrayList<>(ENTRIES);
        sorted.sort(Comparator.comparingInt(Entry::sortKey)
                .thenComparingInt(e -> e.registrationOrder));
        orderedSnapshot = sorted.toArray(new RenderTypeKey[0]);
    }

    /**
     * 注册表条目：可变（重复 id 注册 = 更新字段并重排快照），
     * 注册序号一经分配不再变化（同 sortKey 时稳定 tie-break）。
     */
    private static final class Entry implements RenderTypeKey {
        private final ResourceLocation id;
        /** 首次注册序号：排序 tie-break（同 sortKey 时按注册顺序稳定排列）。 */
        private final int registrationOrder;
        private volatile ResourceLocation atlas;
        private volatile boolean blend;
        private volatile int sortKey;

        Entry(ResourceLocation id, int registrationOrder,
              ResourceLocation atlas, boolean blend, int sortKey) {
            this.id = id;
            this.registrationOrder = registrationOrder;
            this.atlas = atlas;
            this.blend = blend;
            this.sortKey = sortKey;
        }

        void update(ResourceLocation atlas, boolean blend, int sortKey) {
            this.atlas = atlas;
            this.blend = blend;
            this.sortKey = sortKey;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public ResourceLocation atlas() {
            return atlas;
        }

        @Override
        public boolean blend() {
            return blend;
        }

        @Override
        public int sortKey() {
            return sortKey;
        }
    }
}
