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
    int round_num_saw_enemy_net_gun = -1234;
    public void runTurn() throws GameActionException {
        updateLocOfHQ();
        if(rc.getTeamSoup() > RobotType.DELIVERY_DRONE.cost * (1 + num_drones_built)) {
            RobotInfo [] nearby_enemy_robots = rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam().opponent()
            );
            for(RobotInfo rbt : nearby_enemy_robots) {
                if(rbt.type == RobotType.NET_GUN) {
                    round_num_saw_enemy_net_gun = rc.getRoundNum();
                }
            }
            if(rc.getRoundNum() - round_num_saw_enemy_net_gun > 30) {
                Direction build_dir = null;
                int min_build_dist_from_hq = 1234;
                for (Direction dir : directions) {
                    MapLocation l = rc.adjacentLocation(dir);
                    if(rc.canBuildRobot(RobotType.DELIVERY_DRONE, dir)
                        && (locOfHQ == null
                            || max_difference(l, locOfHQ) < min_build_dist_from_hq)
                    ) {
                        build_dir = dir;
                        if(locOfHQ != null) {
                            min_build_dist_from_hq = max_difference(l, locOfHQ);
                        }
                    }
                }
                boolean did_build = tryBuild(RobotType.DELIVERY_DRONE, build_dir);
                if(did_build)
                    num_drones_built++;
            }
        }
    }
}