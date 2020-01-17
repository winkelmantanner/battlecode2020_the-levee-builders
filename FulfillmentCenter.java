package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;



public strictfp class FulfillmentCenter extends Building {
    FulfillmentCenter(RobotController rc) {
        super(rc);
        this.rc = rc;
    }
    public void runTurn() throws GameActionException {
        if(rc.getTeamSoup() > RobotType.DELIVERY_DRONE.cost * (1 + num_drones_built)) {
            RobotInfo [] nearby_enemy_robots = rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam().opponent()
            );
            boolean is_safe_to_build_drones = true;
            for(RobotInfo rbt : nearby_enemy_robots) {
                if(rbt.type == RobotType.NET_GUN) {
                    is_safe_to_build_drones = false;
                }
            }
            if(is_safe_to_build_drones) {
                for (Direction dir : directions) {
                    boolean did_build = tryBuild(RobotType.DELIVERY_DRONE, dir);
                    if(did_build)
                        num_drones_built++;
                }
            }
        }
    }
}