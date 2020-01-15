package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;




public strictfp class Building extends Robot {

    Building(RobotController rc) {
      super(rc);
    }


    @SuppressWarnings("unused")
    public void runTurn() {
        beginTurn();
        // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
        try {
            // Here, we've separated the controls into a different method for each RobotType.
            // You can add the missing ones or rewrite this into your own control structure.
            // System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
            switch (rc.getType()) {
                case HQ:                 runHQ();                break;
                case REFINERY:           runRefinery();          break;
                case VAPORATOR:          runVaporator();         break;
                case DESIGN_SCHOOL:      runDesignSchool();      break;
                case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                case NET_GUN:            runNetGun();            break;
            }
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }
        endTurn();
    }



    final int NUM_MINERS_TO_BUILD_INITIALLY = 4; // used by HQ
    final int TURN_TO_BUILD_ANOTHER_MINER = 150; // used by HQ
    int num_miners_built = 0; // used by HQ

    int num_landscapers_built = 0; // design school
    int num_drones_built = 0; // fulfillment center

    boolean shootOpponentDroneIfPossible() throws GameActionException {
        // only call on net gun and HQ
        for(RobotInfo rbt : rc.senseNearbyRobots()) {
            if(rbt.getType() == RobotType.DELIVERY_DRONE && rbt.getTeam() == rc.getTeam().opponent() && rc.canShootUnit(rbt.ID)) {
                rc.shootUnit(rbt.ID);
                System.out.println("I shot");
                return true;
            }
        }
        return false;
    }

    void runHQ() throws GameActionException {
        if(
            (
                num_miners_built < NUM_MINERS_TO_BUILD_INITIALLY
                && rc.getRoundNum() > 2 * num_miners_built // make sure runMiner works with this
            )
            || (
                rc.getRoundNum() > TURN_TO_BUILD_ANOTHER_MINER
                && num_miners_built < 1 + NUM_MINERS_TO_BUILD_INITIALLY
            )
        ) {
            for (Direction dir : directions)
                if(tryBuild(RobotType.MINER, dir)) {
                    num_miners_built++;
                }
        }
        shootOpponentDroneIfPossible();
        tryToConfuseOpponentWithBlockchain();
    }


    void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    void runVaporator() throws GameActionException {

    }


    void runFulfillmentCenter() throws GameActionException {
        if(num_drones_built < 3) {
            for (Direction dir : directions) {
                boolean did_build = tryBuild(RobotType.DELIVERY_DRONE, dir);
                if(did_build)
                    num_drones_built++;
            }
        }
    }




    void runDesignSchool() throws GameActionException {
        updateLocOfHQ();
        Direction dir_to_build = null;
        int dir_to_build_dist_from_hq = 12345;
        for (Direction dir : directions) {
            if(locOfHQ != null) {
                if(rc.canBuildRobot(RobotType.LANDSCAPER, dir)
                    && rc.getTeamSoup() > RobotType.LANDSCAPER.cost * (1.2 + (((double)num_landscapers_built) / 5))
                    && num_landscapers_built < 8
                ) {
                    MapLocation ml = rc.getLocation().add(dir);
                    if(max_difference(ml, locOfHQ) < dir_to_build_dist_from_hq) {
                        dir_to_build = dir;
                        dir_to_build_dist_from_hq = max_difference(rc.getLocation().add(dir_to_build),
                            locOfHQ
                        );
                    }
                }
            } else if(opp_hq_loc != null) {
                if(rc.canBuildRobot(RobotType.LANDSCAPER, dir)
                    && rc.getTeamSoup() >= RobotType.LANDSCAPER.cost * (1 + (((double)num_landscapers_built) / 2))
                ) {
                    MapLocation ml = rc.getLocation().add(dir);
                    if(max_difference(ml, opp_hq_loc) < dir_to_build_dist_from_hq) {
                        dir_to_build = dir;
                        dir_to_build_dist_from_hq = max_difference(rc.getLocation().add(dir_to_build),
                            opp_hq_loc
                        );
                    }
                }
            } else if(rc.getTeamSoup() > RobotType.LANDSCAPER.cost * (1 + num_landscapers_built)) {
                if(rc.canBuildRobot(RobotType.LANDSCAPER, dir)) {
                    dir_to_build = dir;
                }
            }
        }
        if(dir_to_build != null) {
            rc.buildRobot(RobotType.LANDSCAPER, dir_to_build);
            num_landscapers_built++;
        }
    }



    void runNetGun() throws GameActionException {
        shootOpponentDroneIfPossible();
    }


}