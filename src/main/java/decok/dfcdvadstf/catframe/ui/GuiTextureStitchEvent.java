package decok.dfcdvadstf.catframe.ui;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * UI 图集缝合事件（渲染三域架构：UI 域素材由 CatFrame 自建 gui 图集管理，
 * 走独立事件链，不挂在 blocks 图集的原版缝合同链上；见
 * 《渲染三域架构-收集分流方案.md》2.2 定案）。
 * <p>
 * 三阶段生命周期（由 {@link decok.dfcdvadstf.catframe.adapter.vanilla.model.GuiTextureStitchHandler}
 * 在原版 {@code TextureStitchEvent} 的资源同步点驱动）：
 * <ol>
 *   <li>{@link Pre} —— 收集：消费 {@code catframe:ui} 图集定义（{@code atlases/ui.json}
 *       的 sources）产出 sprite 引用；其他模组可订阅本阶段增删素材；</li>
 *   <li>{@link On} —— 缝合：布局（复用 TextureStitcher，无 mipmap）+ GL 上传；</li>
 *   <li>{@link Post} —— 发布：UI 侧 sprite 查表就绪，CatFrame 自家 UI 绘制可取 UV
 *       批量提交。</li>
 * </ol>
 * <p>
 * 阶段 A 骨架期：事件已定义并触发，收集/缝合/发布消费端由阶段 B 填充。
 *
 * <p>GUI atlas stitch lifecycle event (Pre/On/Post), driven independently of the
 * vanilla blocks/items stitch chain. UI-domain sprites are managed by the
 * CatFrame-built GUI atlas; Pre collects, On stitches and uploads, Post publishes
 * the sprite lookup table.
 */
public class GuiTextureStitchEvent extends Event {

    /** 收集阶段：catframe:ui 图集定义的 sources 产出 sprite 引用（可订阅增删）。 */
    public static class Pre extends GuiTextureStitchEvent {
    }

    /** 缝合阶段：布局 + GL 上传（无 mipmap）。 */
    public static class On extends GuiTextureStitchEvent {
    }

    /** 发布阶段：UI 侧 sprite 查表就绪。 */
    public static class Post extends GuiTextureStitchEvent {
    }
}
