package decok.dfcdvadstf.catframe.ui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.AtlasDefinitionLoader;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlas;
import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;
import decok.dfcdvadstf.catframe.resources.atlas.CatSpriteLoader;
import decok.dfcdvadstf.catframe.resources.atlas.source.SpriteRef;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI 图集管理器（渲染三域架构：{@code catframe:ui} 图集本体，阶段 B）。
 * <p>
 * 订阅 {@link GuiTextureStitchEvent} 三阶段，驱动 CatFrame 自建 GUI 图集
 * （CatAtlas 纯 UI 工具形态，无 mipmap）：
 * <ol>
 *   <li>{@link GuiTextureStitchEvent.Pre} —— 收集：{@link AtlasDefinitionLoader#collectRefs}
 *       取 {@code catframe:ui} 定义（{@code assets/catframe/atlases/ui.json}，允许其他
 *       模组声明归属）的 sources 产物，逐个 {@link CatSpriteLoader} 解码为
 *       {@link CatSprite}；解码失败用 missingno 占位；</li>
 *   <li>{@link GuiTextureStitchEvent.On} —— 缝合：CatAtlas 布局（复用 TextureStitcher）+
 *       单次 CPU 组装 + GL 上传（<b>无 mipmap</b>，红线），并经 TextureManager 以
 *       {@link #ATLAS_LOCATION}（{@code catframe:atlas/ui}）注册，渲染层
 *       {@code bindTexture} 零结构改动；</li>
 *   <li>{@link GuiTextureStitchEvent.Post} —— 发布：查表就绪（{@link #isReady()}），
 *       CatFrame 自家 UI 绘制可取 UV（阶段 C 消费端）。</li>
 * </ol>
 * <p>
 * 触发时机由 {@code GuiTextureStitchHandler} 桥接：Pre/On 挂原版
 * {@code TextureStitchEvent.Pre}（type 0），Post 挂 {@code Post}（type 1）——
 * UI 图集是独立 GL 纹理，与原版 blocks/items 缝合并行无冲突；Post 保证原版纹理
 * 全部加载完后 UI 查表才就绪。
 * <p>
 * UI 动画（当前素材均为静态，机制预留）：客户端 tick 经 {@link #tickAnimations()}
 * 推进帧并区域重传（CatAtlas 的 glTexSubImage2D 区域更新）。
 *
 * <p>UI atlas manager driving the {@code catframe:ui} stitch lifecycle
 * (Pre collects / On stitches and uploads / Post publishes the lookup).
 */
@SideOnly(Side.CLIENT)
public final class UiTextureAtlasManager {

    /** UI 图集 id（{@code assets/catframe/atlases/ui.json} → {@code catframe:ui}）。 */
    public static final String ATLAS_ID = "catframe:ui";
    /** UI 图集 GL 纹理注册位置（TextureManager 注册键，渲染层 bindTexture 用）。 */
    public static final ResourceLocation ATLAS_LOCATION = new ResourceLocation("catframe", "atlas/ui");

    /** 当前 UI 图集（缝合前为 null）。 */
    private static CatAtlas atlas;
    /** Post 发布完成标志（查表就绪）。 */
    private static volatile boolean ready = false;
    /** Pre 收集产物（On 缝合的输入；每次 stitch 周期重建）。 */
    private static List<CatSprite> pending = new ArrayList<>();

    /** 实例化后注册到事件总线（GuiTextureStitchEvent 订阅者）；静态查表入口直接可用。 */
    public UiTextureAtlasManager() {
    }

    // ==================== GuiTextureStitchEvent 三阶段 ====================

    /** Pre —— 收集：catframe:ui 定义 sources → SpriteRef → CatSprite 解码（缺失用 missingno 占位）。 */
    @SubscribeEvent
    public void onPre(GuiTextureStitchEvent.Pre event) {
        List<SpriteRef> refs = AtlasDefinitionLoader.collectRefs(ATLAS_ID);
        pending = new ArrayList<>(refs.size());
        int missing = 0;
        for (SpriteRef ref : refs) {
            CatSprite sprite = CatSpriteLoader.load(
                    ref.resource(), ref.spriteId().toString(), ATLAS_ID, ref.transform());
            if (sprite == null) {
                // 纹理缺失/解码失败 → missingno 占位（保持发布键完整性，与原版缺失语义一致）
                sprite = CatSprite.missing(ATLAS_ID, ref.spriteId().toString());
                missing++;
            }
            pending.add(sprite);
        }
        ready = false;
        CatFrame.logger.info("[UiAtlas] '{}' collected {} sprites ({} missing, filled with missingno)",
                ATLAS_ID, pending.size(), missing);
    }

    /** On —— 缝合：CatAtlas 布局 + CPU 组装 + GL 上传（无 mipmap），并注册到 TextureManager。 */
    @SubscribeEvent
    public void onOn(GuiTextureStitchEvent.On event) {
        if (atlas == null) {
            atlas = new CatAtlas(ATLAS_ID, false);
        }
        int maxTextureSize = Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), 16384);
        atlas.stitch(pending, maxTextureSize);
        // ITextureObject 契约：注册后渲染层 bindTexture(ATLAS_LOCATION) 即绑定 UI 图集
        Minecraft.getMinecraft().getTextureManager().loadTexture(ATLAS_LOCATION, atlas);
    }

    /** Post —— 发布：查表就绪，CatFrame 自家 UI 绘制可取 UV。 */
    @SubscribeEvent
    public void onPost(GuiTextureStitchEvent.Post event) {
        ready = true;
        CatFrame.logger.info("[UiAtlas] '{}' published: {}x{} | sprites={} | location={}",
                ATLAS_ID, atlas.getAtlasWidth(), atlas.getAtlasHeight(),
                atlas.getSprites().size(), ATLAS_LOCATION);
    }

    // ==================== 查询（阶段 C UI 消费端入口） ====================

    /** Post 发布完成（查表就绪）。 */
    public static boolean isReady() {
        return ready;
    }

    /**
     * 按发布键（完整纹理路径，如 {@code catframe:gui/widgets/button}）查找 UI sprite；
     * 未缝合 / 未找到返回 null（调用方用 {@link #getMissingSprite()} 兜底）。
     */
    @Nullable
    public static CatSprite findSprite(String texturePath) {
        if (atlas == null || texturePath == null) {
            return null;
        }
        return atlas.getSprite(texturePath);
    }

    /** UI 图集内置 missing sprite（紫黑格兜底；缝合后恒非 null）。 */
    @Nullable
    public static CatSprite getMissingSprite() {
        return atlas != null ? atlas.getMissingSprite() : null;
    }

    /** UI 图集 GL 纹理注册位置（渲染层 bindTexture 用）。 */
    public static ResourceLocation getAtlasLocation() {
        return ATLAS_LOCATION;
    }

    /**
     * 动画 tick：每客户端 tick 推进 UI sprite 动画帧，帧切换后由 CatAtlas 做
     * glTexSubImage2D 区域重传。当前素材均为静态（零开销），机制为 M3 预留。
     */
    public static void tickAnimations() {
        if (atlas == null || !ready) {
            return;
        }
        for (Map.Entry<String, CatSprite> entry : atlas.getSprites().entrySet()) {
            CatSprite sprite = entry.getValue();
            if (sprite.isAnimated() && sprite.updateAnimationTick()) {
                atlas.updateAnimationRegion(sprite);
            }
        }
    }
}
