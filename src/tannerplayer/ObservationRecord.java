// This class stores the most recent observation from a tile.

package tannerplayer;
import battlecode.common.*;

public strictfp class ObservationRecord {
    public int elevation;
    public int turnOfObservation;
    public boolean was_flooded;
    public RobotInfo building_if_any;

    public ObservationRecord(final RobotController rc, final MapLocation location) throws GameActionException {
      // throws if not rc.canSenseLocation(location) 
      elevation = rc.senseElevation(location);
      turnOfObservation = rc.getRoundNum();
      was_flooded = rc.senseFlooding(location);
      building_if_any = rc.senseRobotAtLocation(location);
      if(building_if_any != null
        && !(
             building_if_any.getType() == RobotType.HQ
          || building_if_any.getType() == RobotType.REFINERY
          || building_if_any.getType() == RobotType.VAPORATOR
          || building_if_any.getType() == RobotType.NET_GUN
          || building_if_any.getType() == RobotType.DESIGN_SCHOOL
          || building_if_any.getType() == RobotType.FULFILLMENT_CENTER
      )) {
        building_if_any = null; // its not a building
      }
    }


}
    