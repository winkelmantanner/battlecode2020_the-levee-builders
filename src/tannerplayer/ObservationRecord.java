// This class stores the most recent observation from a tile.

package tannerplayer;
import battlecode.common.*;

public strictfp class ObservationRecord {
    public int elevation;
    public int turnOfObservation;

    public ObservationRecord(final RobotController rc, final MapLocation location) throws GameActionException {
      // throws if not rc.canSenseLocation(location) 
      elevation = rc.senseElevation(location);
      turnOfObservation = rc.getRoundNum();
    }


}
    