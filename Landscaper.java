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

    int num_turns_unable_to_deposit_adj_to_hq = 0;

    boolean digFromLowestAdjTile() throws GameActionException {
        // digs from lowest adjacent tile not occupied by any robot except drone
        boolean did_dig = false;
        // find direction to lowest adjacent tile that we can dig
        Direction lowest_unoccupied_dir = null;
        int min_diggable_elev = 30000;
        for(Direction dir : directions) {
            MapLocation l = rc.getLocation().add(dir);
            if(isValid(l)
                && rc.canSenseLocation(l)
            ) {
                RobotInfo rbt_at_l = rc.senseRobotAtLocation(l);
                if((locOfHQ == null
                        || max_difference(l, locOfHQ) >= 2
                    )
                    && rc.canDigDirt(dir)
                    && (rbt_at_l == null  // don't dig on occupied tiles
                        || rbt_at_l.type == RobotType.DELIVERY_DRONE)
                    && rc.senseElevation(l) < min_diggable_elev
                ) {
                    lowest_unoccupied_dir = dir;
                    min_diggable_elev = rc.senseElevation(l);
                }
            }
        }
        // dig lowest adjacent tile if possible
        if(lowest_unoccupied_dir != null) {
            rc.digDirt(lowest_unoccupied_dir);
            did_dig = true;
            // System.out.println("I dug dirt");
        }
        return did_dig;
    }

    class BuildingAdjacentData {
        public int min_adj_elevation = 30000;
        public MapLocation min_adj_elev_loc = null;
        public boolean is_adj_to_enemy_landscaper = false;
        BuildingAdjacentData(MapLocation loc_of_building, RobotController rc) throws GameActionException {
            for(int dx = -1; dx <= 1; dx++) {
                for(int dy = -1; dy <= 1; dy++) {
                    MapLocation ml = loc_of_building.translate(dx, dy);
                    if((dx != 0 || dy != 0)
                        && rc.canSenseLocation(ml)
                        && (!isIsolatedDueToMapEdge(ml, loc_of_building)
                            || rc.senseFlooding(ml)
                        )
                    ) {
                        int elev = rc.senseElevation(ml);
                        if(elev < min_adj_elevation) {
                            min_adj_elevation = elev;
                            min_adj_elev_loc = ml;
                        }
                        RobotInfo rbt_at_loc = rc.senseRobotAtLocation(ml);
                        if(rbt_at_loc != null
                            && rbt_at_loc.team == rc.getTeam().opponent()
                            && rbt_at_loc.type == RobotType.LANDSCAPER
                        ) {
                            is_adj_to_enemy_landscaper = true;
                        }
                    }
                }
            }
        }
    }




    public void runTurn() throws GameActionException {
        updateLocOfHQ();

        // top priority: deposit on opponent buildings
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
            rc.depositDirt(dir_to_deposit_to_bury_opponent_building);
        }

        // If we have seen the opp HQ, move/dig toward it
        if(opp_hq_loc != null) {
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
                    rc.digDirt(rc.getLocation().directionTo(locOfHQ));
                }
            } else if(rc.canSenseLocation(target_tile)
                && rc.senseElevation(target_tile) - rc.senseElevation(rc.getLocation()) > 3
                && max_difference(target_tile, opp_hq_loc) == 1
                && rc.canDigDirt(rc.getLocation().directionTo(opp_hq_loc))
            ) {
                // dig through opp levee
                // NOT TESTED AND PROBABLY DOESN'T WORK
                System.out.println("Trying to dig through opp levee");
                rc.digDirt(rc.getLocation().directionTo(opp_hq_loc));
            } else {
                hybridStep(opp_hq_loc);
            }
        }

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

        if(locOfHQ != null) {
            // get adjacent to HQ
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation().add(dir);
                if(isValid(ml)
                  && max_difference(locOfHQ, ml) == 1
                  && canSafeMove(dir)
                ) {
                    if(1 == max_difference(locOfHQ, rc.getLocation())) {
                        if(Math.random() < 0.01) {
                            rc.move(dir);
                        }
                    } else {
                        rc.move(dir);
                    }
                }
            }

            // remove dirt from on top of HQ
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation().add(dir);
                if(ml.equals(locOfHQ)
                  && rc.canDigDirt(dir)
                ) {
                    rc.digDirt(dir);
                    System.out.println("DUG DIRT OFF HQ");
                }
            }

            // determine if we can deposit adj to HQ and if so, where
            boolean can_deposit_adj_to_hq = false;
            Direction dir_we_can_deposit_adj_to_hq = null;
            int elev_of_dir_we_can_deposit_adj_to_hq = 32123;
            for(Direction dir : directions_including_center) {
                MapLocation l = rc.getLocation().add(dir);
                if(rc.canDepositDirt(dir)
                  && max_difference(l, locOfHQ) == 1
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

            // dig from the lowest adjacent tile that is not occupied by a robot
            if(rc.getDirtCarrying() < MAX_ELEVATION_STEP) {
                digFromLowestAdjTile();
            }

            if(can_deposit_adj_to_hq
                && rc.isReady()
            ) {
                num_turns_unable_to_deposit_adj_to_hq = 0;
                if(max_difference(locOfHQ, rc.getLocation()) == 1) {
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
                    if(rc.canSenseLocation(rc.getLocation())
                        && rc.senseElevation(rc.getLocation()) < 200
                        && rc.senseElevation(rc.getLocation()) <= hq_adj_data.min_adj_elevation
                    ) {
                        // we know we have dirt since can_deposit_adj_to_hq
                        rc.depositDirt(Direction.CENTER);
                    } else if(rc.canSenseLocation(rc.getLocation())
                        && 200 <= rc.senseElevation(rc.getLocation())
                    ) {
                        rc.depositDirt(dir_we_can_deposit_adj_to_hq);
                    }
                }
            }

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
                && num_turns_unable_to_deposit_adj_to_hq < 30
            ) {
                num_turns_unable_to_deposit_adj_to_hq++;
                goToHQ();
            } else {
                Direction d = randomDirection();
                if(rc.canDepositDirt(d)
                    && null == rc.senseRobotAtLocation(rc.adjacentLocation(d))
                    && Math.random() < 0.5
                ) {
                    rc.depositDirt(d);
                }
            }
        }
        tryGoSomewhere();
    }




    boolean isIsolatedDueToMapEdge(
        final MapLocation location_of_interest,
        final MapLocation location_of_building
    ) {
        boolean is_isolated = true;
        for(Direction d : directions) {
            MapLocation adj = location_of_interest.add(d);
            if(rc.onTheMap(adj)
                && max_difference(adj, location_of_building) >= 2
            ) {
                is_isolated = false;
                break;
            }
        }
        return is_isolated;
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
                rc.digDirt(dir);
                return true;
            }
        }
        return false;
    }


    boolean tryDeposit(final Direction dir) throws GameActionException {
        if(rc.canDepositDirt(dir) && !rc.getLocation().add(dir).equals(locOfHQ)) {
            rc.depositDirt(dir);
            return true;
        }
        return false;
    }


}