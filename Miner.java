package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;



public strictfp class Miner extends Unit {
    RobotController rc = null;
    Miner(RobotController rbt_controller) {
        super(rbt_controller);
        rc = rbt_controller;
    }


    // miner
    // only one miner builds the build sequence
    RobotType[] minerBuildSequence = {
        RobotType.REFINERY,
        RobotType.DESIGN_SCHOOL,
        RobotType.FULFILLMENT_CENTER,
        RobotType.DESIGN_SCHOOL,
        RobotType.VAPORATOR,
        RobotType.NET_GUN,
        RobotType.DESIGN_SCHOOL
    };
    int numBuildingsBuilt = 0;

    boolean [] hq_might_be = {
        true,
        true,
        true
    };
    boolean has_built_rush_design_school = false;
    public void runTurn() throws GameActionException {
        beginTurn();
        updateLocOfHQ();
        boolean should_mine = true;
        if(roundNumCreated <= 2) { // we are the first miner built
            Direction build_dir = randomDirection();
            MapLocation build_loc = rc.getLocation().add(build_dir);
            if(max_difference(locOfHQ, build_loc) == 3
                && rc.getTeamSoup() > 3 + RobotType.DESIGN_SCHOOL.cost // allow the scout miner to build design school
            ) {
                // only one miner should build so that we can control what is built
                RobotType type_to_build = null;
                if(numBuildingsBuilt < minerBuildSequence.length) {
                    type_to_build = minerBuildSequence[numBuildingsBuilt];
                } else {
                    type_to_build = randomSpawnedByMiner();
                }
                if(tryBuild(type_to_build, build_dir)) {
                    numBuildingsBuilt++;
                }
            } else if(rc.getRoundNum() % 10 < 3) {
                should_mine = false;
            }
            if(locOfHQ != null && Math.random() < 0.5) {
                bugPathingStep(locOfHQ);
            }
        } else if(roundNumCreated < 6) { // we are the second miner built
            int next_stop_index = 0;
            while(next_stop_index < hq_might_be.length && !hq_might_be[next_stop_index]) {
                next_stop_index++;
            }
            if(rc.getRoundNum() < 150
                && !has_built_rush_design_school
                && (
                    next_stop_index < hq_might_be.length
                    || opp_hq_loc != null
                )
            ) {
                should_mine = false;
                RobotInfo opp_hq = null;
                for(RobotInfo rbt : rc.senseNearbyRobots(
                    RobotType.MINER.sensorRadiusSquared,
                    rc.getTeam().opponent()
                )) {
                    if(rbt.type == RobotType.HQ) {
                        opp_hq = rbt;
                    }
                }
                if(opp_hq == null) {
                    if(max_difference(rc.getLocation(), getWhereOppHqMightBe()[next_stop_index]) <= 1) {
                        hq_might_be[next_stop_index] = false;
                    }
                } else { // opp_hq != null
                    opp_hq_loc = opp_hq.location;
                    for(Direction dir : directions) {
                        MapLocation build_loc = rc.getLocation().add(dir);
                        if(1 == max_difference(build_loc, opp_hq_loc)
                            && rc.isReady()
                            && rc.canBuildRobot(RobotType.DESIGN_SCHOOL, dir)
                        ) {
                            rc.buildRobot(RobotType.DESIGN_SCHOOL, dir);
                            has_built_rush_design_school = true;
                        }
                    }
                }

                if(opp_hq_loc == null) {
                    bugPathingStep(getWhereOppHqMightBe()[next_stop_index]);
                } else {
                    bugPathingStep(opp_hq_loc);
                }
            } else {
                should_mine = true;
            }
        }
    
        if(should_mine) {
            for (Direction dir : directions)
                tryMine(dir);
            for (Direction dir : directions)
                tryRefine(dir);

            boolean has_moved_toward_soup = false;
            if(rc.getSoupCarrying() < RobotType.MINER.soupLimit) {
                has_moved_toward_soup = tryMoveToward(X.SOUP);
            }
            if(!has_moved_toward_soup && rc.getSoupCarrying() > 0) {
                if(locOfRefinery == null) {
                    goToHQ();
                } else {
                    bugPathingStep(locOfRefinery);
                }
            }
        } else { // !should_mine
            for (Direction dir : directions)
                tryRefine(dir);
        }
        tryGoSomewhere();
        endTurn();
    }








    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir) && rc.getSoupCarrying() < RobotType.MINER.soupLimit) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to refine soup in a given direction.  Only refines in our team's buildings.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryRefine(Direction dir) throws GameActionException {
        MapLocation ml = rc.getLocation().add(dir);
        if (rc.isReady()
            && rc.canDepositSoup(dir)
            && rc.canSenseLocation(ml)
            && rc.senseRobotAtLocation(ml).team == rc.getTeam()
        ) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }




}