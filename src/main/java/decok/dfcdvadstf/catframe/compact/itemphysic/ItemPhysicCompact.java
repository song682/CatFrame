package decok.dfcdvadstf.catframe.compact.itemphysic;

import cpw.mods.fml.common.Loader;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.fluids.Fluid;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ItemPhysic 物理掉落物渲染兼容层。
 * <p>
 * 背景：ItemPhysic（coremod）通过 ASM 把 {@code RenderItem.doRender} 替换为
 * {@code ClientPhysic.doRender}，其第一优先调用 {@code ForgeHooksClient.renderEntityItem}
 * —— CatFrame 注册的 Forge IItemRenderer 命中后返回 true，ItemPhysic 自己的物理旋转
 * 渲染被整体短路。让出 ENTITY 会丢失 CatFrame 的 JSON 模型 + {@code display.ground}
 * 自定义，因此本类采用"继续接管渲染、复刻旋转动画"的兼容策略：
 * <ol>
 *   <li>全局旋转增量按 ItemPhysic 同一公式重算（{@code ClientPhysic.tick} 由
 *       ItemPhysic 的 RenderTickEvent 每帧刷新；其静态 rotation 字段因 doRender
 *       不再执行而恒为旧值，不能直接用）；</li>
 *   <li>按 ItemPhysic 规则累加 {@code rotationPitch}：落地归零、流体按密度减速、
 *       方块物品超过 360° 回绕；</li>
 *   <li>应用与 ItemPhysic 对齐的 GL 旋转（2D 物品平躺 + Z 轴自转 + X 轴翻滚，
 *       方块物品绕 Y + X），渲染本体仍走 CatFrame 管线（ground transform 生效）。</li>
 * </ol>
 * <p>
 * 编译期零依赖 ItemPhysic（全部反射），未加载时 {@link #isLoaded()} 返回 false，
 * CatFrame 行为与现状完全一致。
 * </p>
 * <p>
 * ItemPhysic compat for physical dropped-item rendering. CatFrame keeps taking over
 * the ENTITY render (JSON model + {@code display.ground} customization) while replaying
 * ItemPhysic's rotation animation: the global rotation delta is recomputed with
 * ItemPhysic's own formula ({@code ClientPhysic.tick} is refreshed by its
 * RenderTickEvent every frame; its static rotation field is stale because doRender
 * never runs), {@code rotationPitch} accumulates by ItemPhysic's rules (reset on
 * ground / slowed by fluid density / wrapped past 360° for blocks), and the aligned
 * GL rotations are applied before the CatFrame pipeline flushes. Zero compile-time
 * dependency on ItemPhysic (all reflective); without it {@link #isLoaded()} is false
 * and CatFrame behaves exactly as before.
 * </p>
 */
public final class ItemPhysicCompact {

    private ItemPhysicCompact() {
    }

    private static final String CLIENT_PHYSIC = "com.creativemd.itemphysic.physics.ClientPhysic";
    private static final String DUMMY_CONTAINER = "com.creativemd.itemphysic.ItemDummyContainer";
    private static final String SERVER_PHYSIC = "com.creativemd.itemphysic.physics.ServerPhysic";

    /** 反射解析结果缓存（首次调用后固定）。Resolved-once reflection cache. */
    private static boolean resolved = false;
    private static boolean loaded = false;
    private static Field tickField;
    private static Field rotateSpeedField;
    private static Method getFluidMethod;

    /**
     * ItemPhysic 是否已加载（Loader 检测 + 类存在兜底，coremod 的 modid 探测
     * 在个别环境下不可靠）。
     * Whether ItemPhysic is loaded (Loader probe with a class-existence fallback,
     * as coremod modid probing is unreliable in some environments).
     *
     * @return true = 已加载
     */
    public static boolean isLoaded() {
        if (!resolved) {
            resolved = true;
            loaded = resolve();
        }
        return loaded;
    }

    /**
     * 复刻 ItemPhysic 的掉落物旋转：累加 {@code rotationPitch} 并应用 GL 旋转。
     * 必须在 CatFrame 管线 flush 之前调用（旋转作用在当前 GL 矩阵上）。
     * <p>
     * Replays ItemPhysic's dropped-item rotation: accumulates {@code rotationPitch}
     * and applies the GL rotations. Must be called before the CatFrame pipeline
     * flushes (the rotation acts on the current GL matrix).
     *
     * @param item   掉落物实体 / the dropped item entity
     * @param isBlock 是否为方块物品（对齐 ItemPhysic 3D / 2D 两条旋转路径）
     *                whether this is a block item (selects ItemPhysic's 3D / 2D rotation paths)
     */
    public static void applyRotation(EntityItem item, boolean isBlock) {
        if (item == null || !isLoaded()) return;
        try {
            double rotation = (double) (System.nanoTime() - tickField.getLong(null))
                    / 2500000.0 * rotateSpeedField.getFloat(null);
            if (!Minecraft.getMinecraft().inGameHasFocus) {
                rotation = 0;
            }

            if (item.onGround) {
                // 落地静止（对齐 ClientPhysic.renderDroppedItem 的 onGround 分支）
                // Settled on the ground (mirrors the onGround branch of renderDroppedItem)
                item.rotationPitch = 0;
            } else {
                rotation *= 2;
                Fluid fluid = getFluid(item);
                if (fluid != null) {
                    // 流体减速：按密度线性缩放（保留 ItemPhysic 的 int 除法语义）
                    // Slowed by fluid density (keeping ItemPhysic's int-division semantics)
                    rotation /= fluid.getDensity() / 1000 * 10;
                }
                if (isBlock && item.rotationPitch > 360) {
                    item.rotationPitch = 0;
                }
                item.rotationPitch += rotation;
            }

            if (isBlock) {
                // 3D 方块路径（对齐 ClientPhysic.doRender 的 3D 分支）
                // 3D block path (mirrors the 3D branch of ClientPhysic.doRender)
                GL11.glRotatef(item.rotationYaw, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(item.rotationPitch, 1.0F, 0.0F, 0.0F);
            } else {
                // 2D 物品路径（对齐 renderDroppedItem 的 fancy 分支：平躺 + 自转 + 翻滚）
                // 2D item path (mirrors the fancy branch of renderDroppedItem: lie flat + spin + tumble)
                GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(item.rotationYaw, 0.0F, 0.0F, 1.0F);
                GL11.glRotatef(item.rotationPitch, 1.0F, 0.0F, 0.0F);
            }
        } catch (Exception e) {
            // 反射失败：降级为不旋转，不影响 CatFrame 本体渲染
            // Reflective failure: degrade to no rotation, never breaks the render itself
            CatFrame.logger.warn("[ItemPhysicCompact] applyRotation failed: {}", e.toString(), e);
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 解析 ItemPhysic 的字段与方法引用；任一缺失即视为未加载。
     * Resolves ItemPhysic fields/methods; any missing member means not loaded.
     *
     * @return 解析成功
     */
    private static boolean resolve() {
        if (!Loader.isModLoaded("itemphysic") && !classExists(CLIENT_PHYSIC)) {
            return false;
        }
        try {
            tickField = Class.forName(CLIENT_PHYSIC).getField("tick");
            rotateSpeedField = Class.forName(DUMMY_CONTAINER).getField("rotateSpeed");
            getFluidMethod = Class.forName(SERVER_PHYSIC).getMethod("getFluid", EntityItem.class);
            return true;
        } catch (Exception e) {
            CatFrame.logger.warn("[ItemPhysicCompact] resolve failed: {}", e.toString(), e);
            return false;
        }
    }

    /**
     * 反射调用 {@code ServerPhysic.getFluid(EntityItem)}；失败或非 Fluid 返回 null。
     * Reflectively invokes {@code ServerPhysic.getFluid(EntityItem)}; null on failure
     * or non-Fluid result.
     */
    @Nullable
    private static Fluid getFluid(EntityItem item) {
        try {
            Object fluid = getFluidMethod.invoke(null, item);
            return fluid instanceof Fluid ? (Fluid) fluid : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
