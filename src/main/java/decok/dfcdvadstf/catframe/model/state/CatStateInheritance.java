package decok.dfcdvadstf.catframe.model.state;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 自动 BlockState 继承 —— 把方块类的继承链映射到 {@link CatStateDefinition} 片段，
 * 子类无需手动重复声明父类的属性与 meta 解码。
 * <p>
 * 模组开发者（或 CatFrame 内置表）先用 {@link #registerBase(Class)} 为某个方块基类
 * 登记「状态定义片段」（属性 + MetaCodec + 可选 dynamic resolver / redirect）；
 * 之后任何继承该基类的方块在注册时（{@code CatModels.Spec.register()} /
 * {@code ModernBlock.register()}）都会经 {@link #resolve(Block)} 自动合并出完整的
 * {@link CatStateDefinition}。
 *
 * <h3>合并语义</h3>
 * <ul>
 *   <li>沿 {@code block.getClass()} 向上收集片段（到 {@link Object} 为止），
 *       按「父类在前、子类在后」拼接属性；同名属性保留首次出现位置、定义以子类为准。</li>
 *   <li>MetaCodec 按片段拼接输出值数组，并按最终静态属性顺序重排（同名覆盖时重映射）。</li>
 *   <li>动态属性标记取并集；dynamic resolver / redirect / multipart 等附加配置
 *       由子类片段覆盖父类片段。</li>
 *   <li>多片段合并时每个片段都必须携带 MetaCodec（否则无法表达各自的 meta 位域），
 *       缺失时 {@link #resolve} 返回 {@code null} 并告警，由调用方回退到显式
 *       {@code .states(...)}。</li>
 * </ul>
 *
 * <p>参考场景：模组方块 {@code MyLogBlock extends BlockRotatedPillar}（原版柱形基类，
 * axis 编码于 meta bit2-3，低 2 位留给子类）。CatFrame 内置表为
 * {@code BlockRotatedPillar} 登记 {@code [axis]} 片段后，MyLogBlock 自动获得
 * {@code axis} 属性及其 meta 解码，无需重复声明。
 */
@SideOnly(Side.CLIENT)
public final class CatStateInheritance {

    private CatStateInheritance() {
    }

    /** 基类片段注册表：方块类 → 状态定义片段（保序，供继承链遍历）。 */
    private static final Map<Class<? extends Block>, Fragment> BASE_FRAGMENTS = new LinkedHashMap<>();

    // ==================== 登记 API ====================

    /**
     * 开始登记一个方块基类的状态定义片段。
     *
     * @param clazz 基类类型（如 {@code BlockRotatedPillar.class}）
     * @return 链式配置器
     */
    public static BaseSpec registerBase(Class<? extends Block> clazz) {
        return new BaseSpec(clazz);
    }

    // ==================== 链式配置器 ====================

    public static final class BaseSpec {
        private final Class<? extends Block> clazz;
        private CatStateDefinition<?> def;
        @Nullable
        private IMetadataBlockstateRedirect redirect;
        @Nullable
        private String redirectNamespace;
        @Nullable
        private ResidentStateModel.DynamicPropertyResolver dynamic;
        private boolean connectionMultipart = false;
        private boolean fullModel = true;

        private BaseSpec(Class<? extends Block> clazz) {
            this.clazz = clazz;
        }

        /** 设置该基类的 typed 状态定义片段（属性 + 可选 MetaCodec + 可选动态属性）。 */
        public BaseSpec states(CatStateDefinition<?> def) {
            this.def = def;
            return this;
        }

        /** 设置该基类自带的 per-meta blockstate 重定向（如按颜色拆分的多文件块）。 */
        public BaseSpec redirect(IMetadataBlockstateRedirect redirect, String namespace) {
            this.redirect = redirect;
            this.redirectNamespace = namespace;
            return this;
        }

        /** 设置该基类的运行时动态属性解析器（stairs shape / pane 连接等）。 */
        public BaseSpec dynamic(ResidentStateModel.DynamicPropertyResolver dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        /** 启用 pane 连接 multipart 模式（per-face 合并烘焙，isFullModel=false）。 */
        public BaseSpec connectionMultipart() {
            this.connectionMultipart = true;
            this.fullModel = false;
            return this;
        }

        /** 覆盖 isFullModel（默认 true；multipart 装饰件可设为 false）。 */
        public BaseSpec fullModel(boolean fullModel) {
            this.fullModel = fullModel;
            return this;
        }

        /** 完成登记：存入 {@link #BASE_FRAGMENTS}，供继承链解析。 */
        public void register() {
            if (def == null) {
                throw new IllegalStateException(
                        "Base fragment for " + clazz.getName() + " requires .states(...) before register()");
            }
            BASE_FRAGMENTS.put(clazz, new Fragment(def, redirect, redirectNamespace,
                    dynamic, connectionMultipart, fullModel));
        }
    }

    // ==================== 继承解析 ====================

    /**
     * 沿方块继承链解析合并后的状态定义与附加配置。
     * <p>
     * 从 {@code block.getClass()} 向上遍历（不含 {@link Object}），收集所有已登记的
     * 基类片段，按「父类在前、子类在后」合并属性 / MetaCodec / 动态标记 / 附加配置，
     * 构建一个 owner 为该方块的 {@link CatStateDefinition}。
     *
     * @param block 目标方块实例
     * @return 合并结果；继承链上没有任何基类片段（或多片段组合缺少 MetaCodec）时返回 {@code null}
     */
    @Nullable
    public static Inherited resolve(Block block) {
        // 1. 收集继承链上的片段（子→父），反转成父→子
        List<Fragment> fragments = new ArrayList<>();
        for (Class<?> c = block.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Fragment f = BASE_FRAGMENTS.get(c);
            if (f != null) fragments.add(f);
        }
        if (fragments.isEmpty()) return null;
        Collections.reverse(fragments);

        // 2. 合并属性：LinkedHashMap 按名去重 —— 保留首次出现位置，定义以子类（后 put）为准
        LinkedHashMap<String, Property<?>> propMap = new LinkedHashMap<>();
        LinkedHashSet<String> dynamicNames = new LinkedHashSet<>();
        for (Fragment f : fragments) {
            for (Property<?> p : f.properties) {
                propMap.put(p.getName(), p);
            }
            for (Property<?> p : f.dynamic) {
                dynamicNames.add(p.getName());
            }
        }
        List<Property<?>> mergedProps = new ArrayList<>(propMap.values());

        // 3. 合并 meta 解码：拼接各片段输出 + 按最终静态属性顺序重排
        //    多片段且任一片段缺 codec 时无法表达位域 → 返回 null 由调用方回退
        CatStateDefinition.MetaCodec mergedCodec = mergeCodecs(fragments, mergedProps, dynamicNames);
        if (mergedCodec == null && fragments.size() > 1) {
            CatFrame.logger.warn(
                    "[CatStateInheritance] {} inherits from {} base fragment(s), but a fragment lacks a "
                            + "MetaCodec so meta decoding cannot be combined. Register the block with an explicit "
                            + ".states(...) instead.",
                    block.getClass().getName(), fragments.size());
            return null;
        }

        // 4. 构建合并后的 CatStateDefinition（owner = 方块实例）
        CatStateDefinition.Builder<Block> builder = new CatStateDefinition.Builder<>(block);
        for (Property<?> p : mergedProps) {
            builder.add(p);
        }
        for (Property<?> p : mergedProps) {
            if (dynamicNames.contains(p.getName())) {
                builder.dynamic(p);
            }
        }
        if (mergedCodec != null) {
            builder.metaCodec(mergedCodec);
        }

        // 5. 附加配置：子类片段覆盖父类片段（fragments 已为父→子序）
        IMetadataBlockstateRedirect redirect = null;
        String redirectNamespace = null;
        ResidentStateModel.DynamicPropertyResolver dynamicResolver = null;
        boolean connectionMultipart = false;
        boolean fullModel = true;
        for (Fragment f : fragments) {
            if (f.redirect != null) {
                redirect = f.redirect;
                redirectNamespace = f.redirectNamespace;
            }
            if (f.dynamicResolver != null) {
                dynamicResolver = f.dynamicResolver;
            }
            if (f.connectionMultipart) {
                connectionMultipart = true;
                fullModel = false;
            }
            fullModel = f.fullModel;
        }

        return new Inherited(builder.create(), redirect, redirectNamespace,
                dynamicResolver, connectionMultipart, fullModel);
    }

    /**
     * 合并各片段的 MetaCodec：输出按片段顺序拼接的静态属性值，再按最终静态属性顺序重排。
     * 任一片段缺 codec 时返回 {@code null}（单片段缺 codec 表示该片段整体走默认笛卡尔积解码）。
     */
    @Nullable
    private static CatStateDefinition.MetaCodec mergeCodecs(List<Fragment> fragments,
                                                            List<Property<?>> mergedProps,
                                                            Set<String> dynamicNames) {
        // 计算拼接偏移，并收集全部 codec
        int spliceLen = 0;
        int[] offsets = new int[fragments.size()];
        CatStateDefinition.MetaCodec[] codecs = new CatStateDefinition.MetaCodec[fragments.size()];
        for (int i = 0; i < fragments.size(); i++) {
            CatStateDefinition.MetaCodec codec = fragments.get(i).codec;
            if (codec == null) return null;
            offsets[i] = spliceLen;
            codecs[i] = codec;
            spliceLen += fragments.get(i).staticProps.length;
        }

        // 最终静态属性顺序（排除动态属性）
        List<Property<?>> mergedStatic = new ArrayList<>();
        for (Property<?> p : mergedProps) {
            if (!dynamicNames.contains(p.getName())) {
                mergedStatic.add(p);
            }
        }

        // 重排映射：最终静态索引 → 拼接输出索引。
        // 同名属性被多个片段声明时，后遍历的片段（子类）覆盖前面的映射。
        int[] remap = new int[mergedStatic.size()];
        for (int fi = 0; fi < fragments.size(); fi++) {
            Fragment f = fragments.get(fi);
            Map<String, Integer> staticIdx = new HashMap<>();
            for (int si = 0; si < f.staticProps.length; si++) {
                staticIdx.put(f.staticProps[si].getName(), si);
            }
            for (int msi = 0; msi < mergedStatic.size(); msi++) {
                Integer si = staticIdx.get(mergedStatic.get(msi).getName());
                if (si != null) {
                    remap[msi] = offsets[fi] + si;
                }
            }
        }

        final int finalSpliceLen = spliceLen;
        return meta -> {
            Comparable<?>[] splice = new Comparable<?>[finalSpliceLen];
            for (int i = 0; i < codecs.length; i++) {
                Comparable<?>[] part = codecs[i].decode(meta);
                System.arraycopy(part, 0, splice, offsets[i], part.length);
            }
            Comparable<?>[] out = new Comparable<?>[remap.length];
            for (int i = 0; i < remap.length; i++) {
                out[i] = splice[remap[i]];
            }
            return out;
        };
    }

    // ==================== 片段 / 结果 ====================

    /**
     * 不可变基类片段：从 {@link CatStateDefinition} 提取属性 / MetaCodec / 动态标记，
     * 附加模型行为配置（redirect / dynamic resolver / multipart）。
     */
    private static final class Fragment {
        final Property<?>[] properties;
        final Property<?>[] staticProps;
        final Property<?>[] dynamic;
        @Nullable
        final CatStateDefinition.MetaCodec codec;
        @Nullable
        final IMetadataBlockstateRedirect redirect;
        @Nullable
        final String redirectNamespace;
        @Nullable
        final ResidentStateModel.DynamicPropertyResolver dynamicResolver;
        final boolean connectionMultipart;
        final boolean fullModel;

        Fragment(CatStateDefinition<?> def,
                 @Nullable IMetadataBlockstateRedirect redirect,
                 @Nullable String redirectNamespace,
                 @Nullable ResidentStateModel.DynamicPropertyResolver dynamicResolver,
                 boolean connectionMultipart,
                 boolean fullModel) {
            this.properties = def.getProperties();
            this.codec = def.getMetaCodec();
            List<Property<?>> dyn = new ArrayList<>();
            List<Property<?>> stat = new ArrayList<>();
            for (Property<?> p : properties) {
                if (def.isDynamic(p)) {
                    dyn.add(p);
                } else {
                    stat.add(p);
                }
            }
            this.dynamic = dyn.toArray(new Property<?>[0]);
            this.staticProps = stat.toArray(new Property<?>[0]);
            this.redirect = redirect;
            this.redirectNamespace = redirectNamespace;
            this.dynamicResolver = dynamicResolver;
            this.connectionMultipart = connectionMultipart;
            this.fullModel = fullModel;
        }
    }

    /**
     * 继承解析结果：合并后的 {@link CatStateDefinition} 与附加模型行为配置。
     * <p>
     * 由 {@link CatModels.Spec} / {@code ModernBlock.register} 消费：未显式配置的
     * 字段用继承结果填充，显式配置的字段保持优先。
     */
    public static final class Inherited {
        /** 合并后的 typed 状态定义（owner = 方块实例，非空）。 */
        public final CatStateDefinition<?> def;
        @Nullable
        public final IMetadataBlockstateRedirect redirect;
        @Nullable
        public final String redirectNamespace;
        @Nullable
        public final ResidentStateModel.DynamicPropertyResolver dynamic;
        public final boolean connectionMultipart;
        public final boolean fullModel;

        Inherited(CatStateDefinition<?> def,
                  @Nullable IMetadataBlockstateRedirect redirect,
                  @Nullable String redirectNamespace,
                  @Nullable ResidentStateModel.DynamicPropertyResolver dynamic,
                  boolean connectionMultipart,
                  boolean fullModel) {
            this.def = def;
            this.redirect = redirect;
            this.redirectNamespace = redirectNamespace;
            this.dynamic = dynamic;
            this.connectionMultipart = connectionMultipart;
            this.fullModel = fullModel;
        }
    }
}
