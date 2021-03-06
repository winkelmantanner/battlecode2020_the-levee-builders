package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;



public strictfp class DeliveryDrone extends Unit {


    RobotController rc = null;
    DeliveryDrone(RobotController rbt_controller) {
        super(rbt_controller);
        rc = rbt_controller;

        can_move_diagonally = false;

        will_be_attack_drone = (0 == ((rc.getRoundNum() % 7) % 2));
    }

    boolean will_be_attack_drone = false;
    boolean is_attack_drone = false;

    MapLocation random_spot_im_going_to = null;

    int turn_i_was_1_from_hq = -12345;

    boolean has_posted = false;

    MapLocation where_i_found_valid_enemy_drop_loc = null;
    MapLocation where_i_found_valid_cow_drop_loc = null;

    public void runTurn() throws GameActionException {
        updateLocOfHQ();
        can_move_diagonally = ((rc.getRoundNum() % 17) == 0);
        if(locOfHQ != null
            && rc.getLocation().isAdjacentTo(locOfHQ)
        ) {
            turn_i_was_1_from_hq = rc.getRoundNum();
        }
        if(rc.isReady()) {
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation().add(dir);
                if(isValid(ml)
                    && rc.canSenseLocation(ml)
                ) {
                    RobotInfo rbt = rc.senseRobotAtLocation(ml);

                    if(rbt == null) {
                        if(is_valid_enemy_drop_loc(ml)) {
                            where_i_found_valid_enemy_drop_loc = ml;
                        } else if(where_i_found_valid_enemy_drop_loc != null
                            && ml.equals(where_i_found_valid_enemy_drop_loc)
                        ) {
                            where_i_found_valid_enemy_drop_loc = null;
                        }
                        if(is_valid_cow_drop_loc(ml)) {
                            where_i_found_valid_cow_drop_loc = ml;
                        } else if(where_i_found_valid_cow_drop_loc != null
                            && ml.equals(where_i_found_valid_cow_drop_loc)
                        ) {
                            where_i_found_valid_cow_drop_loc = null;
                        }
                    }

                    if(rbt != null
                        && rbt.team != rc.getTeam()
                        && rc.canPickUpUnit(rbt.ID)
                        && (!rbt.type.equals(RobotType.COW)
                            || is_attack_drone
                        )
                        && rc.canSenseLocation(rbt.location)
                        && !is_valid_enemy_drop_loc(rbt.location)
                        && (!rbt.type.equals(RobotType.COW)
                            || !is_valid_cow_drop_loc(ml)
                        )
                    ) {
                        // pick up any enemy unit
                        System.out.println("Picked up enemy unit");
                        rc.pickUpUnit(rbt.ID);
                        carried_unit_info = rbt;
                        break;
                    } else if(
                        rbt != null
                        && rbt.team == rc.getTeam()
                        && locOfHQ != null
                        && (
                            (rbt.type == RobotType.MINER
                                && Math.random() < 0.2
                                && max_difference(
                                    rbt.location,
                                    locOfHQ
                                ) == 1
                            )
                            || (rbt.type == RobotType.LANDSCAPER
                                && max_difference(
                                    rbt.location,
                                    locOfHQ
                                ) >= 2
                                && rc.getRoundNum() - turn_i_was_1_from_hq <= 3
                            )
                        )
                        && rc.canPickUpUnit(rbt.ID)
                    ) {
                        // pick up miners off the levee
                        // and pick up landscapers hoping to put them on the levee
                        rc.pickUpUnit(rbt.ID);
                        carried_unit_info = rbt;
                        break;
                    }

                    if(carried_unit_info != null
                        && rc.isCurrentlyHoldingUnit()
                        && rc.canDropUnit(dir)
                    ) {
                        MapLocation drop_loc = rc.getLocation().add(dir);
                        if(carried_unit_info.team != rc.getTeam()
                            && is_valid_enemy_drop_loc(drop_loc)
                            && (carried_unit_info.type != RobotType.COW
                                || (rc.canSenseLocation(drop_loc)
                                    && rc.senseFlooding(drop_loc)
                                )
                            )
                        ) {
                            System.out.println("pit_max_elevation: " + String.valueOf(pit_max_elevation));
                            // drop enemy units and cows into water
                            // drop enemy units in pits
                            rc.dropUnit(dir);
                            carried_unit_info = null;
                            break;
                        } else if(
                            carried_unit_info.team == rc.getTeam()
                            && rc.canSenseLocation(drop_loc)
                            && (carried_unit_info.type == RobotType.MINER
                                || rc.getRoundNum() - turn_i_was_1_from_hq
                                    >= 10
                                        + (
                                            20
                                            * max_difference(drop_loc, locOfHQ)
                                            / max(rc.senseElevation(drop_loc), 1)
                                        )
                            )
                            && !rc.senseFlooding(drop_loc)
                            && rc.senseElevation(drop_loc) > 0
                            && (rc.senseElevation(drop_loc) < 12 + GameConstants.getWaterLevel(rc.getRoundNum())
                                || (locOfHQ != null
                                    && carried_unit_info.type == RobotType.LANDSCAPER
                                    && max_difference(drop_loc, locOfHQ) <= 2
                                )
                            )
                        ) {
                            // drop friendly miners and landscapers on safe ground
                            // (only drop landscapers if we give up on droping on the levee)
                            rc.dropUnit(dir);
                            carried_unit_info = null;
                            break;
                        } else if(
                            carried_unit_info.team == rc.getTeam()
                            && carried_unit_info.type == RobotType.LANDSCAPER
                            && locOfHQ != null
                            && drop_loc.isAdjacentTo(locOfHQ)
                        ) {
                            // drop friendly landscapers on the levee
                            rc.dropUnit(dir);
                            carried_unit_info = null;
                            break;
                        }
                    }
                }
            }

            if(carried_unit_info != null
                && carried_unit_info.team != rc.getTeam()
                && rc.isCurrentlyHoldingUnit()
            ) {
                if(RobotType.COW == carried_unit_info.type) {
                    if(where_i_found_valid_cow_drop_loc != null) {
                        hybridStep(where_i_found_valid_cow_drop_loc);
                    }
                } else {
                    if(where_i_found_valid_enemy_drop_loc != null) {
                        hybridStep(where_i_found_valid_enemy_drop_loc);
                    }
                }
            }

            // rush
            if(is_attack_drone
                && rc.getRoundNum() > 1300
                && opp_hq_loc != null
            ) {
                if(
                    (rc.getRoundNum() / 50) % 10 == 0
                    && rc.getLocation().distanceSquaredTo(opp_hq_loc) <= 64
                    && rc.canMove(rc.getLocation().directionTo(opp_hq_loc))
                ) {
                    // do the rush
                    rc.move(rc.getLocation().directionTo(opp_hq_loc));
                } else if(
                    (rc.getRoundNum() / 50) % 10 >= 7 // it may take up to about 150 turns to collect the drones
                ) {
                    // prepare for the rush
                    hybridStep(opp_hq_loc);
                }
            }

            if(!rc.isCurrentlyHoldingUnit()) {
                for(RobotInfo rbt : getNearbyOpponentUnits()) {
                    if(rbt.team != rc.getTeam()
                      && rbt.type.canBePickedUp()
                      && rc.canSenseLocation(rbt.location)
                      && rc.senseElevation(rbt.location) > pit_max_elevation
                    ) {
                        // move toward enemy units if not carrying anythin
                        if(Math.random() < 0.95) {
                            can_move_diagonally = true;
                            hybridStep(rbt.location);
                            can_move_diagonally = false;
                        }
                    }
                }
            }

            if(!has_posted) {
                int [] six_ints = {
                    MessageType.DRONE_SPAWN.getValue(),
                    1234,
                    rc.getMapWidth() % 4,
                    (int) (Math.random() * rc.getMapHeight()),
                    rc.getID(),
                    5
                };
                has_posted = tryPostMessage(six_ints, 2);
            }

            for(int [] message : getMyMessages(rc.getRoundNum() - 1)) {
                if(message[0] == MessageType.DRONE_SPAWN.getValue()
                    && message[4] != rc.getID()
                ) {
                    is_attack_drone = will_be_attack_drone;
                }
            }


            GoSomewhereOptions options = new GoSomewhereOptions();
            if(!is_attack_drone
                && (carried_unit_info == null
                    || carried_unit_info.team.equals(rc.getTeam())
            )) {
                options.setMaxDistFromHq(3);
                if(locOfHQ != null
                    && 2 == max_difference(rc.getLocation(), locOfHQ)
                    && rc.canSenseLocation(rc.getLocation())
                    && rc.senseFlooding(rc.getLocation())
                ) {
                    options.setMaxDistFromHq(2);
                }
            }

            if(is_attack_drone) {
                if(random_spot_im_going_to == null
                    || rc.getRoundNum() % 100 == 0
                    || rc.getLocation().distanceSquaredTo(random_spot_im_going_to) <= 6
                ) {
                    random_spot_im_going_to = new MapLocation(
                        (rc.getID() + (rc.getRoundNum()*3)) % rc.getMapWidth(),
                        (rc.getID() + (rc.getRoundNum()*7)) % rc.getMapHeight()
                    );
                }
                hybridStep(random_spot_im_going_to);
            }

            if(!is_attack_drone
                && locOfHQ != null
                && max_difference(rc.getLocation(), locOfHQ) >= 4
            ) {
                goToHQ();
            } else {
                tryGoSomewhere(options);
            }
        }
    }
}