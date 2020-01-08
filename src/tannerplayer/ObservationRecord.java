// This class stores the most recent observation from a tile.

package tannerplayer;
import battlecode.common.*;

public strictfp class ObservationRecord {
    public int elevation;
    public int turnOfObservation;
    public boolean was_flooded;
    public RobotInfo building_if_any;
    public int crude_soup;

    public ObservationRecord(final RobotController rc, final MapLocation location) throws GameActionException {
      // throws if not rc.canSenseLocation(location) 
      elevation = rc.senseElevation(location);
      turnOfObservation = rc.getRoundNum();
      was_flooded = rc.senseFlooding(location);
      building_if_any = rc.senseRobotAtLocation(location);
      crude_soup = rc.senseSoup(location);
      if(building_if_any != null) {
        switch(building_if_any.getType()) {
          case MINER:
          case LANDSCAPER:
          case DELIVERY_DRONE:
          case COW:
            building_if_any = null; // its not a building
            break;
        }
      }
    }


}
    