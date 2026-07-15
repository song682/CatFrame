package decok.dfcdvadstf.catframe.team;

public interface ITeam {

    int getTeamID();

    default int getTeamColor() {
        return 0xFFFFFFFF;
    }

    String getName();

    default boolean isAllowFriendlyFire(int teamID) {
        return true;
    }
}
