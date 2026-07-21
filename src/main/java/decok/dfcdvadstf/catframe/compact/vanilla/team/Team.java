package decok.dfcdvadstf.catframe.compact.vanilla.team;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * <p>
 * 队伍抽象基类 —— 1.7.10 版本 {@code net.minecraft.scoreboard.Team} 的功能补全，
 * 对标高版本 Minecraft 的 {@code net.minecraft.world.scores.Team}。<br>
 * 在原版仅有的 {@code isSameTeam} / {@code getRegisteredName} / {@code formatString} /
 * {@code func_98297_h} / {@code getAllowFriendlyFire} 基础上，补全了高版本新增的
 * 名称可见性、死亡消息可见性、碰撞规则、队伍颜色与成员集合等特性。
 * </p>
 * <p>
 * Team abstract base — a feature backport of 1.7.10's {@code net.minecraft.scoreboard.Team},
 * mirroring the high-version {@code net.minecraft.world.scores.Team}.<br>
 * On top of the vanilla-only members it adds the high-version name-tag visibility,
 * death-message visibility, collision rule, team colour and member collection.
 * </p>
 *
 * <p>
 * 类型映射 / Type mapping:
 * <ul>
 *   <li>{@code Component} / {@code MutableComponent} → {@link Text}</li>
 *   <li>{@code ChatFormatting} → {@link Style.TextFormat}</li>
 * </ul>
 * </p>
 */
public abstract class Team {

    /**
     * Whether the given team is allied to this one. Same as {@code ==} — mirrors the
     * legacy {@code isSameTeam}.
     * <p>给定队伍是否与本队伍为同盟。等价于 {@code ==}，对应旧版 {@code isSameTeam}。</p>
     */
    public boolean isAlliedTo(@Nullable final Team other) {
        return other == null ? false : this == other;
    }

    /**
     * Retrieve the name by which this team is registered in the scoreboard.
     * <p>返回此队伍在计分板中注册的名称（对应旧版 {@code getRegisteredName}）。</p>
     */
    public abstract String getName();

    /**
     * Formats the given member name with this team's prefix/suffix and style.
     * Mirrors the legacy {@code formatString(String)} but returns a {@link Text}.
     * <p>使用本队伍的前缀/后缀与样式格式化给定成员名，对应旧版 {@code formatString(String)}，
     * 但返回 {@link Text}。</p>
     */
    public abstract Text getFormattedName(Text teamMemberName);

    /**
     * Whether members of this team can see teammates that are invisible.
     * <p>本队伍成员是否能看见处于隐身状态的队友（对应旧版 {@code func_98297_h}）。</p>
     */
    public abstract boolean canSeeFriendlyInvisibles();

    /**
     * Whether friendly fire is allowed within this team.
     * <p>本队伍内是否允许友军伤害（对应旧版 {@code getAllowFriendlyFire}）。</p>
     */
    public abstract boolean isAllowFriendlyFire();

    /**
     * How this team's name tags are shown to other players.
     * <p>本队伍的名称标签对其他玩家的可见方式。</p>
     */
    public abstract Visibility getNameTagVisibility();

    /**
     * The colour associated with this team.
     * <p>本队伍关联的颜色。</p>
     */
    public abstract Style.TextFormat getColor();

    /**
     * The user names of all members of this team.
     * <p>本队伍所有成员的用户名集合。</p>
     */
    public abstract Collection<String> getPlayers();

    /**
     * How this team's death messages are shown to other players.
     * <p>本队伍的死亡消息对其他玩家的可见方式。</p>
     */
    public abstract Visibility getDeathMessageVisibility();

    /**
     * The collision rule applied between this team's members and others.
     * <p>本队伍成员与其他实体之间应用的碰撞规则。</p>
     */
    public abstract CollisionRule getCollisionRule();

    /**
     * Entity collision rule between team members and others.
     * <p>队伍成员与其他实体之间的碰撞规则。</p>
     */
    public enum CollisionRule {
        ALWAYS("always", 0),
        NEVER("never", 1),
        PUSH_OTHER_TEAMS("pushOtherTeams", 2),
        PUSH_OWN_TEAM("pushOwnTeam", 3);

        public final String name;
        public final int id;

        CollisionRule(final String name, final int id) {
            this.name = name;
            this.id = id;
        }

        /**
         * Resolve a rule by its network id; out-of-range ids fall back to the first
         * value ({@link #ALWAYS}), matching the high-version {@code OutOfBoundsStrategy.ZERO}.
         * <p>按网络 id 解析规则；越界 id 回退为首个值（{@link #ALWAYS}），
         * 对应高版本的 {@code OutOfBoundsStrategy.ZERO}。</p>
         */
        public static CollisionRule byId(final int id) {
            final CollisionRule[] values = values();
            return id >= 0 && id < values.length ? values[id] : values[0];
        }

        /**
         * The translatable display name, e.g. {@code team.collision.always}.
         * <p>可翻译的显示名称，例如 {@code team.collision.always}。</p>
         */
        public Text getDisplayName() {
            return Text.translatable("team.collision." + this.name);
        }

        /**
         * The serialized (persistent/wire) name of this rule.
         * <p>此规则用于序列化（存档/网络）的名称。</p>
         */
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * Visibility of name tags / death messages relative to other teams.
     * <p>名称标签 / 死亡消息相对于其他队伍的可见性。</p>
     */
    public enum Visibility {
        ALWAYS("always", 0),
        NEVER("never", 1),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams", 2),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam", 3);

        public final String name;
        public final int id;

        Visibility(final String name, final int id) {
            this.name = name;
            this.id = id;
        }

        /**
         * Resolve a visibility by its network id; out-of-range ids fall back to the first
         * value ({@link #ALWAYS}), matching the high-version {@code OutOfBoundsStrategy.ZERO}.
         * <p>按网络 id 解析可见性；越界 id 回退为首个值（{@link #ALWAYS}），
         * 对应高版本的 {@code OutOfBoundsStrategy.ZERO}。</p>
         */
        public static Visibility byId(final int id) {
            final Visibility[] values = values();
            return id >= 0 && id < values.length ? values[id] : values[0];
        }

        /**
         * The translatable display name, e.g. {@code team.visibility.always}.
         * <p>可翻译的显示名称，例如 {@code team.visibility.always}。</p>
         */
        public Text getDisplayName() {
            return Text.translatable("team.visibility." + this.name);
        }

        /**
         * The serialized (persistent/wire) name of this visibility.
         * <p>此可见性用于序列化（存档/网络）的名称。</p>
         */
        public String getSerializedName() {
            return this.name;
        }
    }
}
