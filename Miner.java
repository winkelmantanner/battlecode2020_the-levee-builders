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
        RobotType.DESIGN_SCHOOL,
        RobotType.FULFILLMENT_CENTER,
        RobotType.DESIGN_SCHOOL,
        RobotType.VAPORATOR,
        RobotType.NET_GUN,
        RobotType.DESIGN_SCHOOL
    };
    // the following integers must both be kept accurate
    int buildSequenceIndex = 0;
    int numBuildingsBuilt = 0;

    MapLocation where_i_found_soup = null;
    int num_rounds_going_to_where_i_found_soup = 0;

    boolean [] hq_might_be = {
        true,
        true,
        true
    };
    boolean has_built_rush_design_school = false;
    public void runTurn() throws GameActionException {
        updateLocOfHQ();
        boolean should_mine = true;
        if(roundNumCreated <= 2 // we are the first miner built
            || rc.getRoundNum() >= 1000 // its terraforming time
        ) {
            if(rc.getTeamSoup() > min(
                (25 * (numBuildingsBuilt * numBuildingsBuilt))
                    + RobotType.DESIGN_SCHOOL.cost
                ,
                RobotType.VAPORATOR.cost
            )) {
                should_mine = false;
                Direction build_dir = null;
                if(locOfHQ != null) {
                    for(Direction dir : directions) {
                        MapLocation ml = rc.adjacentLocation(dir);
                        int distance_squared_from_hq = ml.distanceSquaredTo(locOfHQ);
                        if(buildSequenceIndex >= minerBuildSequence.length
                            || (
                                distance_squared_from_hq + 5 < RobotType.DESIGN_SCHOOL.sensorRadiusSquared
                                && max_difference(ml, locOfHQ) >= 3 - (((double)rc.getTeamSoup() - 200) / 150)
                            )
                        ) {
                            build_dir = dir;
                            break;
                        }
                    }
                }
                if(build_dir != null) {
                    // only one miner should build so that we can control what is built
                    MapLocation build_loc = rc.adjacentLocation(build_dir);
                    RobotType type_to_build = null;
                    boolean should_build_refinery = false;
                    if(null == locOfRefinery) {
                        boolean friendly_landscaper_adj_to_hq = false;
                        boolean enemy_landscaper_adj_to_hq = false;
                        for(RobotInfo rbt : rc.senseNearbyRobots()) {
                            if(rbt != null
                                && rbt.type == RobotType.LANDSCAPER
                                && 1 == max_difference(rbt.location, locOfHQ)
                            ) {
                                if(rbt.team == rc.getTeam()) {
                                    friendly_landscaper_adj_to_hq = true;
                                } else {
                                    enemy_landscaper_adj_to_hq = true;
                                }
                            }
                        }
                        should_build_refinery = friendly_landscaper_adj_to_hq;
                    }
                    if(should_build_refinery) {
                        type_to_build = RobotType.REFINERY;
                    } else if(buildSequenceIndex < minerBuildSequence.length) {
                        type_to_build = minerBuildSequence[buildSequenceIndex];
                    } else {
                        type_to_build = getTerraformingStageBuildingToBuild();
                    }
                    if(tryBuild(type_to_build, build_dir)) {
                        numBuildingsBuilt++;
                        if(buildSequenceIndex < minerBuildSequence.length
                            && minerBuildSequence[buildSequenceIndex] == type_to_build
                        ) {
                            buildSequenceIndex++;
                        }
                    }
                }
                if(locOfHQ != null && Math.random() < 0.75) {
                    hybridStep(locOfHQ);
                }
            }
        // } else if(roundNumCreated < 6) {
        //     // we are the second miner built and its not terraforming time
        //     int next_stop_index = 0;
        //     while(next_stop_index < hq_might_be.length && !hq_might_be[next_stop_index]) {
        //         next_stop_index++;
        //     }
        //     if(rc.getRoundNum() < 150
        //         && !has_built_rush_design_school
        //         && (
        //             next_stop_index < hq_might_be.length
        //             || opp_hq_loc != null
        //         )
        //     ) {
        //         should_mine = false;
        //         RobotInfo opp_hq = null;
        //         for(RobotInfo rbt : getNearbyOpponentUnits()) {
        //             if(rbt.type == RobotType.HQ) {
        //                 opp_hq = rbt;
        //             }
        //         }
        //         if(opp_hq == null) {
        //             if(max_difference(rc.getLocation(), getWhereOppHqMightBe()[next_stop_index]) <= 1) {
        //                 hq_might_be[next_stop_index] = false;
        //             }
        //         } else { // opp_hq != null
        //             opp_hq_loc = opp_hq.location;
        //             for(Direction dir : directions) {
        //                 MapLocation build_loc = rc.getLocation().add(dir);
        //                 if(1 == max_difference(build_loc, opp_hq_loc)
        //                     && rc.isReady()
        //                     && rc.canBuildRobot(RobotType.DESIGN_SCHOOL, dir)
        //                 ) {
        //                     rc.buildRobot(RobotType.DESIGN_SCHOOL, dir);
        //                     has_built_rush_design_school = true;
        //                 }
        //             }
        //         }

        //         if(opp_hq_loc == null) {
        //             bugPathingStep(getWhereOppHqMightBe()[next_stop_index]);
        //         } else {
        //             if(!wall_BFS_step(opp_hq_loc)) {
        //                 if(Clock.getBytecodesLeft() > 3000) {
        //                     bugPathingStep(opp_hq_loc);
        //                 }
        //             }
        //         }
        //     } else {
        //         should_mine = true;
        //     }
        }
    
        if(should_mine) {

            buildRefineryIfApplicable();
            
            // move onto the soup so as not to block the view of other miners
            if(0 == rc.getSoupCarrying()) {
                moveOnTopOfSoupIfPossible();
            }
            
            for (Direction dir : directions_including_center)
                if(tryMine(dir)) {
                    where_i_found_soup = rc.adjacentLocation(dir);
                }
            for (Direction dir : directions) {
                if(tryRefine(dir)) {
                    num_rounds_going_to_where_i_found_soup = 0;
                }
            }

            boolean has_moved_toward_soup = false;
            if(rc.getSoupCarrying() < RobotType.MINER.soupLimit) {
                has_moved_toward_soup = tryMoveToward(X.SOUP);
            }
            if(!has_moved_toward_soup) {
                if(rc.getSoupCarrying() > 0) {
                    if(locOfRefinery == null) {
                        goToHQ();
                    } else if(Math.random() < 0.95) {
                        hybridStep(locOfRefinery);
                    }
                } else if(where_i_found_soup != null && rc.isReady()) { // rc.getSoupCarrying() == 0
                    if(num_rounds_going_to_where_i_found_soup < 15) {
                        if(Math.random() < 1) {
                            // current_dir is used by tryGoSomewhere
                            current_dir = rc.getLocation().directionTo(where_i_found_soup);
                        }
                        num_rounds_going_to_where_i_found_soup++;
                    } else {
                        where_i_found_soup = null;
                        num_rounds_going_to_where_i_found_soup = 0;
                    }
                    if(where_i_found_soup != null && max_difference(rc.getLocation(), where_i_found_soup) <= 1) {
                        where_i_found_soup = null;
                    }
                }
            }
        } else { // !should_mine
            for (Direction dir : directions) {
                if(tryRefine(dir)) {
                    num_rounds_going_to_where_i_found_soup = 0;
                }
            }
        }
        tryGoSomewhere();
    }

    boolean buildRefineryIfApplicable() throws GameActionException {

        Direction build_dir = null;
        for(Direction dir : directions) {
            MapLocation ml = rc.adjacentLocation(dir);
            if(rc.canSenseLocation(ml)
                && rc.senseSoup(ml) > 0
                && rc.canBuildRobot(RobotType.REFINERY, dir)
            ) {
                build_dir = dir;
            }
        }
        if(build_dir != null) {
            int amount_of_soup_nearby = count_soup_with_BFS(rc.adjacentLocation(build_dir));
            if(amount_of_soup_nearby
                > RobotType.REFINERY.cost
                    * (3 + (4 * numBuildingsBuilt))
                && rc.getTeamSoup() >= RobotType.REFINERY.cost
            ) {
                boolean there_already_is_a_refinery = false;
                for(RobotInfo rbt : rc.senseNearbyRobots()) {
                    if(rbt.type == RobotType.REFINERY
                        || rbt.type == RobotType.HQ
                    ) {
                        there_already_is_a_refinery = true;
                    }
                }
                if(!there_already_is_a_refinery) {
                    System.out.println("I counted " + String.valueOf(amount_of_soup_nearby) + " soup so I built a refinery");
                    rc.buildRobot(RobotType.REFINERY, build_dir);
                    numBuildingsBuilt++;
                    return true;
                }
            }
        }
        return false;
    }

    boolean moveOnTopOfSoupIfPossible() throws GameActionException {
        if(rc.isReady()
            && rc.canSenseLocation(rc.getLocation())
            && 0 == rc.senseSoup(rc.getLocation())
        ) {
            for(Direction dir : directions) {
                MapLocation l = rc.adjacentLocation(dir);
                if(rc.canSenseLocation(l)
                    && 0 < rc.senseSoup(l)
                    && rc.canMove(dir)
                    && canSafeMove(dir)
                ) {
                    rc.move(dir);
                    return true;
                }
            }
        }
        return false;
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