package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;

public strictfp class Landscaper extends Unit {

    RobotController rc = null;
    Landscaper(RobotController rbt_controller) {
        super(rbt_controller);
        rc = rbt_controller;
    }

    MapLocation loc_i_dug_from = null;
    void dig(Direction dir) throws GameActionException {
        // make sure that digDirt is never called anywhere else
        // this function keeps the instance variables up to date
        rc.digDirt(dir);
        loc_i_dug_from = rc.adjacentLocation(dir);
        rc.setIndicatorDot(rc.adjacentLocation(dir), 60, 60, 60);
    }


    final int NUM_TURNS_WITHOUT_HQ_ACCESS_BEFORE_TERRAFORMING = 30;

    int num_turns_unable_to_deposit_adj_to_hq = 0;
    int num_turns_unable_to_deposit_adj_to_opp_hq = 0;

    boolean has_attempt_to_BFS_to_hq_due_to_rush = false;

    int num_rounds_trying_to_get_adjacent_to_hq_while_being_rushed = 0;

    Direction getDirectionToDigFrom(final boolean can_dig_adj_to_buildings) throws GameActionException {
        // find direction to lowest adjacent tile that we can dig
        // this function has been observed to take up to 1457 bytecodes
        Direction lowest_unoccupied_dir = null;
        int min_diggable_elev = 30000;
        for(Direction dir : directions) {
            MapLocation l = rc.getLocation().add(dir);
            if(rc.onTheMap(l)
                && isValidDigLoc(l, locOfHQ)
                && rc.canSenseLocation(l)
            ) {
                RobotInfo rbt_at_l = rc.senseRobotAtLocation(l);
                boolean rbt_at_l_is_friendly_building = (
                    rbt_at_l != null
                    && rbt_at_l.team.equals(rc.getTeam())
                    && !rbt_at_l.type.canMove()
                );
                if((locOfHQ == null
                        || max_difference(l, locOfHQ) >= 2
                        || isIsolatedDueToMapEdge(l, locOfHQ)
                    )
                    && rc.canDigDirt(dir)
                    && (rbt_at_l == null  // don't dig on occupied tiles
                        || rbt_at_l.type == RobotType.DELIVERY_DRONE
                        || rbt_at_l_is_friendly_building
                        || (rbt_at_l.team == rc.getTeam().opponent()
                            && rbt_at_l.type.canMove()
                        )
                        || (locOfHQ != null
                            && isIsolatedDueToMapEdge(l, locOfHQ)
                        )
                    )
                    && (rc.senseElevation(l) < min_diggable_elev
                        || rbt_at_l_is_friendly_building
                    )
                ) {
                    boolean l_is_adj_to_buildings = false;
                    if(!can_dig_adj_to_buildings) {
                        for(RobotInfo rbt : rc.senseNearbyRobots(
                            l,
                            2*2,
                            rc.getTeam()
                        )) {
                            if(!rbt.type.canMove()) {
                                l_is_adj_to_buildings = true;
                                break;
                            }
                        }
                    }
                    if(can_dig_adj_to_buildings
                        || !l_is_adj_to_buildings
                    ) {
                        lowest_unoccupied_dir = dir;
                        min_diggable_elev = rc.senseElevation(l);
                        if(rbt_at_l_is_friendly_building) {
                            // prioritize digging on friendly buildings
                            min_diggable_elev -= 1234;
                        }
                    }
                }
            }
        }
        return lowest_unoccupied_dir;
    }
    Direction digFromLowestAdjTile() throws GameActionException {
        return digFromLowestAdjTile(true);
    }
    Direction digFromLowestAdjTile(final boolean can_dig_adj_to_buildings) throws GameActionException {
        // digs from lowest adjacent tile not occupied by any robot except drone
        Direction lowest_unoccupied_dir = getDirectionToDigFrom(can_dig_adj_to_buildings);
        
        // dig lowest adjacent tile if possible
        if(lowest_unoccupied_dir != null) {
            dig(lowest_unoccupied_dir);
            // System.out.println("I dug dirt");
        }
        return lowest_unoccupied_dir;
    }

    // int max_bytecodes = 0;
    class BuildingAdjacentData {
        public int min_adj_elevation = 30000;
        public MapLocation min_adj_elev_loc = null;
        public boolean is_adj_to_enemy_landscaper = false;
        BuildingAdjacentData(MapLocation loc_of_building, RobotController rc) throws GameActionException {
            this(loc_of_building, rc, true);
        }
        BuildingAdjacentData(MapLocation loc_of_building, RobotController rc, final boolean include_tiles_with_buildings) throws GameActionException {
            // int bcbefore = Clock.getBytecodeNum();
            for(int dx = -1; dx <= 1; dx++) {
                for(int dy = -1; dy <= 1; dy++) {
                    MapLocation ml = loc_of_building.translate(dx, dy);
                    if((dx != 0 || dy != 0)
                        && rc.canSenseLocation(ml)
                        && (!isIsolatedDueToMapEdge(ml, loc_of_building)
                            || rc.senseFlooding(ml)
                        )
                    ) {
                        RobotInfo rbt_at_ml = rc.senseRobotAtLocation(ml);
                        if(include_tiles_with_buildings
                            || canSafeDepositUnderRobot(rbt_at_ml)
                        ) {
                            int elev = rc.senseElevation(ml);
                            if(elev < min_adj_elevation) {
                                min_adj_elevation = elev;
                                min_adj_elev_loc = ml;
                            }
                            if(rbt_at_ml != null
                                && rbt_at_ml.team == rc.getTeam().opponent()
                                && rbt_at_ml.type == RobotType.LANDSCAPER
                            ) {
                                is_adj_to_enemy_landscaper = true;
                            }
                        }
                    }
                }
            }
            // int bctaken = Clock.getBytecodeNum() - bcbefore;
            // if(bctaken > max_bytecodes) {
            //     max_bytecodes = bctaken;
            //     System.out.println("max_bytecodes:" + String.valueOf(max_bytecodes));
            // }
        }
    }

// int max_bc = 0;


    public void runTurn() throws GameActionException {
        updateLocOfHQ();

        RobotInfo nearest_enemy_rusher = null;
        for(RobotInfo rbt : rc.senseNearbyRobots(
            rc.getType().sensorRadiusSquared,
            rc.getTeam().opponent()
        )) {
            if(rbt != null
                && (rbt.type == RobotType.LANDSCAPER
                    || rbt.type == RobotType.MINER
                )
            ) {
                nearest_enemy_rusher = rbt;
                break;
            }
        }

        // top priority: get adjacent to HQ
        if(locOfHQ != null) {
            // get adjacent to HQ
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation().add(dir);
                if(isValid(ml)
                  && ml.isAdjacentTo(locOfHQ)
                  && canSafeMove(dir)
                ) {
                    if(rc.getLocation().isAdjacentTo(locOfHQ)) {
                        if(Math.random() < 0.01) {
                            rc.move(dir);
                        }
                    } else {
                        rc.move(dir);
                    }
                }
            }

            if(nearest_enemy_rusher != null
                && nearest_enemy_rusher.location.isAdjacentTo(locOfHQ)
                && !rc.getLocation().isAdjacentTo(locOfHQ)
                && num_rounds_trying_to_get_adjacent_to_hq_while_being_rushed < 30
            ) {
                goToHQ();
                num_rounds_trying_to_get_adjacent_to_hq_while_being_rushed++;
            }
        }

        // deposit on opponent buildings
        Direction dir_to_deposit_to_bury_opponent_building = null;
        RobotInfo rbt_to_bury = null;
        for(Direction dir : directions) {
            MapLocation ml = rc.getLocation().add(dir);
            if(rc.isReady()
                && rc.canDepositDirt(dir)
                && rc.getDirtCarrying() > 0
                && rc.canSenseLocation(ml)
            ) {
                RobotInfo rbt = rc.senseRobotAtLocation(ml);
                if(rbt != null
                    && rbt.team == rc.getTeam().opponent()
                    && !rbt.type.canMove()
                    && (rbt_to_bury == null || rbt_to_bury.type != RobotType.HQ) // prioritize the opponent's HQ
                ) {
                    dir_to_deposit_to_bury_opponent_building = dir;
                    rbt_to_bury = rbt;
                }
            }
        }
        if(dir_to_deposit_to_bury_opponent_building != null) {
            deposit(dir_to_deposit_to_bury_opponent_building);
        }

        // If we have seen the opp HQ, move/dig toward it
        if(opp_hq_loc != null
            && opp_hq_loc.isWithinDistanceSquared(rc.getLocation(), rc.getType().sensorRadiusSquared)
        ) {
            MapLocation target_tile = rc.getLocation().add(rc.getLocation().directionTo(opp_hq_loc));
            
            if(rc.getDirtCarrying() <= 0) {
                // get some dirt
                if(locOfHQ == null
                    || !locOfHQ.equals(rc.adjacentLocation(rc.getLocation().directionTo(locOfHQ)))
                    || !rc.canDigDirt(rc.getLocation().directionTo(locOfHQ))
                ) {
                    // dig from lowest adjacent file that is not occupied
                    digFromLowestAdjTile();
                } else {
                    dig(rc.getLocation().directionTo(locOfHQ));
                }
            } else if(rc.canSenseLocation(target_tile)
                && rc.senseElevation(target_tile) - rc.senseElevation(rc.getLocation()) > 3
                && target_tile.isAdjacentTo(opp_hq_loc)
                && rc.canDigDirt(rc.getLocation().directionTo(opp_hq_loc))
            ) {
                // dig through opp levee
                // NOT TESTED AND PROBABLY DOESN'T WORK
                System.out.println("Trying to dig through opp levee");
                dig(rc.getLocation().directionTo(opp_hq_loc));
            } else if(num_turns_unable_to_deposit_adj_to_opp_hq < NUM_TURNS_WITHOUT_HQ_ACCESS_BEFORE_TERRAFORMING) {
                num_turns_unable_to_deposit_adj_to_opp_hq++;
                hybridStep(opp_hq_loc);
            }

            if(max_difference(opp_hq_loc, rc.getLocation()) <= 2) {
                num_turns_unable_to_deposit_adj_to_opp_hq = 0;
            }
        }

        // if(locOfHQ != null
        //     && 1 == max_difference(rc.getLocation(), locOfHQ)
        // ) {
        //     // remove dirt from on top of HQ
        //     Direction dir_to_hq = rc.getLocation().directionTo(locOfHQ);
        //     MapLocation ml = rc.adjacentLocation(dir_to_hq);
        //     if(ml.equals(locOfHQ)
        //         && rc.canDigDirt(dir_to_hq)
        //     ) {
        //         dig(dir_to_hq);
        //         System.out.println("DUG DIRT OFF HQ");
        //     }
        // }
        ////////
        if(locOfHQ != null) {
            // remove dirt from on top of HQ
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation().add(dir);
                if(ml.equals(locOfHQ)
                  && rc.canDigDirt(dir)
                ) {
                    dig(dir);
                    System.out.println("DUG DIRT OFF HQ");
                }
            }

            if(rc.getRoundNum() == 2000) {
                int bcbefore = Clock.getBytecodeNum();
                System.out.println(String.valueOf(rc.getLocation().isAdjacentTo(locOfHQ)));
                System.out.println(String.valueOf(Clock.getBytecodeNum() - bcbefore));
            }
        }
        ////////



        // dig from the lowest adjacent tile that is not occupied by a robot
        if(rc.getDirtCarrying() < MAX_ELEVATION_STEP) {
            digFromLowestAdjTile(num_turns_unable_to_deposit_adj_to_hq < NUM_TURNS_WITHOUT_HQ_ACCESS_BEFORE_TERRAFORMING);
        }

        if(locOfHQ != null) {


            boolean should_work_on_hq = has_seen_distress_signal
                || rc.getRoundNum() > 1000
                || rc.getLocation().isAdjacentTo(locOfHQ);

            if(should_work_on_hq) {


                // determine if we can deposit adj to HQ and if so, where
                boolean can_deposit_adj_to_hq = false;
                Direction dir_we_can_deposit_adj_to_hq = null;
                int elev_of_dir_we_can_deposit_adj_to_hq = 32123;
                for(Direction dir : directions_including_center) {
                    MapLocation l = rc.getLocation().add(dir);
                    if(rc.canDepositDirt(dir)
                    && l.isAdjacentTo(locOfHQ)
                    && rc.canSenseLocation(l)
                    && rc.senseElevation(l) < elev_of_dir_we_can_deposit_adj_to_hq
                    && !isIsolatedDueToMapEdge(l, locOfHQ)
                    ) {
                        can_deposit_adj_to_hq = true;
                        dir_we_can_deposit_adj_to_hq = dir;
                        elev_of_dir_we_can_deposit_adj_to_hq = rc.senseElevation(l);
                    }
                }

                BuildingAdjacentData hq_adj_data = new BuildingAdjacentData(locOfHQ, rc);

                if(hq_adj_data.is_adj_to_enemy_landscaper) {
                    System.out.println("ENEMY LANDSCAPER; im carrying " + String.valueOf(rc.getDirtCarrying()) + " dirt");
                }

                // int bcbefore = Clock.getBytecodeNum();

                if(can_deposit_adj_to_hq
                    && rc.isReady()
                ) {
                    num_turns_unable_to_deposit_adj_to_hq = 0;
                    if(rc.getLocation().isAdjacentTo(locOfHQ)) {
                        // Direction dir_to_deposit = null;
                        // if(rc.canSenseLocation(rc.getLocation())
                        //     && hq_adj_data.min_adj_elevation >= rc.senseElevation(rc.getLocation())
                        //     && rc.canDepositDirt(Direction.CENTER)
                        // ) {
                        //     dir_to_deposit = Direction.CENTER;
                        // } else {
                        //     int yet_another_min_elev = 12345;
                        //     for(Direction dir : directions_including_center) {
                        //         MapLocation l = rc.getLocation().add(dir);
                        //         if(isValid(l)
                        //             && max_difference(l, locOfHQ) == 1
                        //             && rc.canSenseLocation(l)
                        //             && (rc.senseElevation(l) < hq_adj_data.min_adj_elevation + MAX_ELEVATION_STEP
                        //                 || hq_adj_data.is_adj_to_enemy_landscaper)
                        //             && rc.senseElevation(l) < yet_another_min_elev
                        //         ) {
                        //             dir_to_deposit = dir;
                        //             yet_another_min_elev = rc.senseElevation(l);
                        //         }
                        //     }
                        // }
                        if(
                            (
                                (nearest_enemy_rusher != null
                                    && rc.getRoundNum() < 300
                                )
                                || ((locOfRefinery != null
                                        || hq_adj_data.min_adj_elevation > MAX_ELEVATION_STEP + rc.senseElevation(locOfHQ)
                                        || rc.getRoundNum() > 250
                                    )
                                    && elev_of_dir_we_can_deposit_adj_to_hq < hq_adj_data.min_adj_elevation + MAX_ELEVATION_STEP
                                )
                            )
                            && tryDeposit(dir_we_can_deposit_adj_to_hq)
                        ) {
                            // rc.setIndicatorDot(rc.getLocation().add(dir_we_can_deposit_adj_to_hq), 0, 255, 0);
                            // System.out.println("I deposited dirt " + rc.getLocation().add(dir_we_can_deposit_adj_to_hq).toString());
                        } else if(rc.getDirtCarrying() > 0) {
                            // System.out.println("I MOVED");
                            if(Math.random() < 0.5) {
                                fuzzy_clear();
                            }
                            fuzzy_step(hq_adj_data.min_adj_elev_loc);
                        }
                    } else if(max_difference(locOfHQ, rc.getLocation()) == 2) {
                        for(Direction dir : directions) {
                            MapLocation l = rc.adjacentLocation(dir);
                            if(rc.canMove(dir)
                                && l.distanceSquaredTo(locOfHQ) < rc.getLocation().distanceSquaredTo(locOfHQ)
                                && canSafeMove(dir)
                            ) {
                                rc.move(dir);
                            }
                        }
                        if(rc.isReady()) {
                            if(rc.canSenseLocation(rc.getLocation())
                                && rc.senseElevation(rc.getLocation()) <= min(100, MAX_ELEVATION_STEP + GameConstants.getWaterLevel(rc.getRoundNum() + 100))
                                && rc.senseElevation(rc.getLocation()) <= hq_adj_data.min_adj_elevation
                            ) {
                                // we know we have dirt since can_deposit_adj_to_hq
                                deposit(Direction.CENTER);
                            } else {
                                deposit(dir_we_can_deposit_adj_to_hq);
                            }
                        }
                    }
                }

                // int bctaken = Clock.getBytecodeNum() - bcbefore;
                // if(bctaken > max_bc) {
                //     max_bc = bctaken;
                //     System.out.println("max_bc:" + String.valueOf(max_bc));
                // }
            }

            runToNetGunIfDrones();

            // if(rc.isReady()) {
            //     System.out.println("I didn't use my shovel this round and im " + (im_stuck() ? "stuck" : "not stuck"));
            //     if(!im_stuck()) {
            //         for(Direction d : directions) {
            //             if(canSafeMove(d)) {
            //                 System.out.println(d.toString());
            //             }
            //         }
            //     }
            // }
            if(rc.isReady()
                && should_work_on_hq
                && num_turns_unable_to_deposit_adj_to_hq < NUM_TURNS_WITHOUT_HQ_ACCESS_BEFORE_TERRAFORMING
            ) {
                num_turns_unable_to_deposit_adj_to_hq++;
                // int x = Clock.getBytecodeNum();
                goToHQ();
                // if(Clock.getBytecodeNum() - x > maxbc) {
                //     maxbc = Clock.getBytecodeNum() - x;
                //     System.out.println("maxbc(x):" + String.valueOf(maxbc));
                // }
            } else if(rc.isReady()) {
                if(Math.random() < 0.1
                    && locOfHQ != null
                ) {
                    current_dir = rc.getLocation().directionTo(locOfHQ);
                }
                if(rc.getDirtCarrying() > 0) {
                    // for(Direction dir : directions) {
                    //     MapLocation ml = rc.adjacentLocation(dir);
                    //     if(rc.canSenseLocation(ml)) {
                    //         RobotInfo rbt_at_dir = rc.senseRobotAtLocation(ml);
                    //         if(rbt_at_dir != null
                    //             && rbt_at_dir.team == rc.getTeam()
                    //             && rbt_at_dir.type.canMove() == false
                    //         ) {
                    //             BuildingAdjacentData building_data = new BuildingAdjacentData(rbt_at_dir.location, rc, false);
                    //             if(
                    //                 building_data.min_adj_elevation >= pit_max_elevation
                    //                 && building_data.min_adj_elevation < rc.senseElevation(rbt_at_dir.location) + MAX_ELEVATION_STEP
                    //             ) {
                    //                 if(max_difference(building_data.min_adj_elev_loc, rc.getLocation()) <= 1) {
                    //                     Direction deposit_dir = rc.getLocation().directionTo(building_data.min_adj_elev_loc);
                    //                     if(canSafeDeposit(deposit_dir)) {
                    //                         // System.out.println("Tried to deposit adj to building " + deposit_dir.toString());
                    //                         deposit(deposit_dir);
                    //                     }
                    //                 } else {
                    //                     // System.out.println("step " + building_data.min_adj_elev_loc.toString());
                    //                     if(Math.random() < 0.5) {
                    //                         fuzzy_clear();
                    //                     }
                    //                     fuzzy_step(building_data.min_adj_elev_loc, true);
                    //                 }
                    //                 break;
                    //             }
                    //         }
                    //     }
                    // }
                    if(rc.isReady()) {
                        Direction lowest_nondig_dir = null;
                        int lowest_nondig_deposit_loc_elevation = 12345;
                        Direction lowest_build_dir = null;
                        int lowest_build_dir_elevation = 12345;
                        for(Direction dir : directions_including_center) {
                            MapLocation ml = rc.adjacentLocation(dir);
                            if(rc.canSenseLocation(ml)
                                && rc.senseElevation(ml) > pit_max_elevation
                                && rc.canDepositDirt(dir)
                            ) {
                                
                                if(!ml.equals(loc_i_dug_from)
                                    && !isValidDigLoc(ml, locOfHQ)
                                    && rc.senseElevation(ml) < lowest_nondig_deposit_loc_elevation
                                ) {
                                    RobotInfo rbt = rc.senseRobotAtLocation(ml);
                                    if(rbt == null
                                        || rbt.team != rc.getTeam()
                                        || rbt.type.canMove()
                                    ) {
                                        lowest_nondig_dir = dir;
                                        lowest_nondig_deposit_loc_elevation = rc.senseElevation(ml);
                                    }
                                }
                                if(isValidBuildLoc(ml, locOfHQ)
                                    && rc.senseElevation(ml) < lowest_build_dir_elevation
                                ) {
                                    RobotInfo rbt = rc.senseRobotAtLocation(ml);
                                    if(rbt == null
                                        || rbt.team != rc.getTeam()
                                        || rbt.type.canMove()
                                    ) {
                                        lowest_build_dir = dir;
                                        lowest_build_dir_elevation = rc.senseElevation(ml);
                                    }
                                }
                            }
                        }
                        float water_level_in_200 = GameConstants.getWaterLevel(rc.getRoundNum() + 200);
                        if(lowest_nondig_deposit_loc_elevation <= 1 + water_level_in_200) {
                            deposit(lowest_nondig_dir);
                        } else if(lowest_build_dir_elevation <= 1 + MAX_ELEVATION_STEP + water_level_in_200) {
                            deposit(lowest_build_dir);
                        }
                    }
                }
            }
        }
        tryGoSomewhere();
        checkForDistressSignal();
    }

    boolean canSafeDepositUnderRobot(RobotInfo rbt) {
        return (rbt == null
            || !rbt.team.equals(rc.getTeam())
            || rbt.type == RobotType.DELIVERY_DRONE
        );
    }

    boolean canSafeDeposit(Direction dir) throws GameActionException {
        MapLocation ml = rc.adjacentLocation(dir);
        if(rc.canDepositDirt(dir)
            && rc.canSenseLocation(ml)
        ) {
            RobotInfo rbt = rc.senseRobotAtLocation(ml);
            if(canSafeDepositUnderRobot(rbt)) {
                return true;
            }
        }
        return false;
    }







    boolean trySafeNonobstructiveDig(Direction dir) throws GameActionException {
        MapLocation target_loc = rc.getLocation().add(dir);
        boolean is_safe = true;
        for(Direction ndir : directions) {
            MapLocation adj = target_loc.add(ndir);
            if(rc.canSenseLocation(adj) && rc.senseFlooding(adj)) {
                is_safe = false;
                break;
            }
        }
        if(is_safe) {
            return tryNonobstructiveDig(dir);
        } else {
            return false;
        }
    }

    boolean tryNonobstructiveDig(Direction dir) throws GameActionException {
        MapLocation target_loc = rc.getLocation().add(dir);
        if(rc.canDigDirt(dir) && rc.canSenseLocation(target_loc)) {
            int target_current_elev = rc.senseElevation(target_loc);
            boolean is_ok_to_dig = true;
            for(Direction ndir : directions) {
                MapLocation adj = target_loc.add(ndir);
                if(!(rc.canSenseLocation(adj)
                  && target_current_elev > rc.senseElevation(adj) - MAX_ELEVATION_STEP
                )) {
                    is_ok_to_dig = false;
                    break;
                }
            }
            if(is_ok_to_dig) {
                dig(dir);
                return true;
            }
        }
        return false;
    }


    boolean tryDeposit(final Direction dir) throws GameActionException {
        if(rc.canDepositDirt(dir) && !rc.getLocation().add(dir).equals(locOfHQ)) {
            deposit(dir);
            return true;
        }
        return false;
    }

    void deposit(final Direction dir) throws GameActionException {
        rc.depositDirt(dir);
        rc.setIndicatorDot(rc.adjacentLocation(dir), 255, 5, 5);
    }


}