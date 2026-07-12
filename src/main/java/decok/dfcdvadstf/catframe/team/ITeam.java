package decok.dfcdvadstf.catframe.team;

public interface ITeam {
    default boolean isAllowFriendlyFire(int team) {
        return false;
    }
}
