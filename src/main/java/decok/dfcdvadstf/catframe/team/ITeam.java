package decok.dfcdvadstf.catframe.team;

public interface ITeam {

    int getTeamID();

    String getName();

    default boolean isAllowFriendlyFire(int teamID) {
        return false;
    }
}
