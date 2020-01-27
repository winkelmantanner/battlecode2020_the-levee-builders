package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;




abstract public strictfp class Building extends Robot {

    Building(RobotController rc) {
      super(rc);
    }


    



    final int NUM_MINERS_TO_BUILD_INITIALLY = 7; // used by HQ
    final int TURN_TO_BUILD_ANOTHER_MINER = 12345/*NEVER*/; // used by HQ
    int num_miners_built = 0; // used by HQ

    int num_landscapers_built = 0; // design school
    int num_drones_built = 0; // fulfillment center

    boolean shootOpponentDroneIfPossible() throws GameActionException {
        // only call on net gun and HQ
        int min_dist_squared = rc.getType().sensorRadiusSquared;
        RobotInfo target_rbt = null;
        for(RobotInfo rbt : rc.senseNearbyRobots(
            rc.getType().sensorRadiusSquared,
            rc.getTeam().opponent()
        )) {
            if(rbt.getType() == RobotType.DELIVERY_DRONE
                && rc.canShootUnit(rbt.ID)
                && rc.getLocation().distanceSquaredTo(rbt.location) < min_dist_squared
            ) {
                target_rbt = rbt;
                min_dist_squared = rc.getLocation().distanceSquaredTo(rbt.location);
                if(Clock.getBytecodesLeft() < 1000) {
                    break;
                }
            }
        }
        if(target_rbt != null) {
            rc.shootUnit(target_rbt.ID);
            System.out.println("I shot");
            return true;
        }
        return false;
    }

    



}