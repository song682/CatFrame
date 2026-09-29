package decok.dfcdvadstf.catframe.adapter.vanilla.team;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * <p>
 * Team abstract base — a feature backport of 1.7.10's {@code net.minecraft.scoreboard.Team},
 * mirroring the high-version {@code net.minecraft.world.scores.Team}.<br>
 * On top of the vanilla-only members it adds the high-version name-tag visibility,
 * death-message visibility, collision rule, team colour and member collection.
 * </p>
 * <p>
 * Type mapping:
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
     */
    public boolean isAlliedTo(@Nullable final Team other) {
        return other == null ? false : this == other;
    }

    /**
     * Retrieve the name by which this team is registered in the scoreboard.
     */
    public abstract String getName();

    /**
     * Formats the given member name with this team's prefix/suffix and style.
     * Mirrors the legacy {@code formatString(String)} but returns a {@link Text}.
     */
    public abstract Text getFormattedName(Text teamMemberName);

    /**
     * Whether members of this team can see teammates that are invisible.
     */
    public abstract boolean canSeeFriendlyInvisibles();

    /**
     * Whether friendly fire is allowed within this team.
     */
    public abstract boolean isAllowFriendlyFire();

    /**
     * How this team's name tags are shown to other players.
     */
    public abstract Visibility getNameTagVisibility();

    /**
     * The colour associated with this team.
     */
    public abstract Style.TextFormat getColor();

    /**
     * The user names of all members of this team.
     */
    public abstract Collection<String> getPlayers();

    /**
     * How this team's death messages are shown to other players.
     */
    public abstract Visibility getDeathMessageVisibility();

    /**
     * The collision rule applied between this team's members and others.
     */
    public abstract CollisionRule getCollisionRule();

    /**
     * Entity collision rule between team members and others.
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
         */
        public static CollisionRule byId(final int id) {
            final CollisionRule[] values = values();
            return id >= 0 && id < values.length ? values[id] : values[0];
        }

        /**
         * The translatable display name, e.g. {@code team.collision.always}.
         */
        public Text getDisplayName() {
            return Text.translatable("team.collision." + this.name);
        }

        /**
         * The serialized (persistent/wire) name of this rule.
         */
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * Visibility of name tags / death messages relative to other teams.
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
         */
        public static Visibility byId(final int id) {
            final Visibility[] values = values();
            return id >= 0 && id < values.length ? values[id] : values[0];
        }

        /**
         * The translatable display name, e.g. {@code team.visibility.always}.
         */
        public Text getDisplayName() {
            return Text.translatable("team.visibility." + this.name);
        }

        /**
         * The serialized (persistent/wire) name of this visibility.
         */
        public String getSerializedName() {
            return this.name;
        }
    }
}
