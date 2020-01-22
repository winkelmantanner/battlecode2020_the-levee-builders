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
    }

    int turn_i_was_1_from_hq = -12345;


    public void runTurn() throws GameActionException {
        updateLocOfHQ();
        if(locOfHQ != null
            && 1 == max_difference(rc.getLocation(), locOfHQ)
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
                    if(rbt != null
                        && rbt.team != rc.getTeam()
                        && rc.canPickUpUnit(rbt.ID)
                        && rc.canSenseLocation(rbt.location)
                        && rc.senseElevation(rbt.location) > PIT_MAX_ELEVATION
                    ) {
                        // pick up any enemy unit
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
                                && rc.getRoundNum() - turn_i_was_1_from_hq <= 10
                            )
                        )
                        && rc.canPickUpUnit(rbt.ID)
                    ) {
                        // pick up miners off the levee
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
                            && (rc.senseFlooding(ml)
                                || (rc.senseElevation(ml) <= PIT_MAX_ELEVATION
                                        && carried_unit_info.type != RobotType.COW
                                    )
                            )
                        ) {
                            // drop enemy units and cows into water
                            rc.dropUnit(dir);
                            carried_unit_info = null;
                            break;
                        } else if(
                            carried_unit_info.team == rc.getTeam()
                            && rc.canSenseLocation(drop_loc)
                            && (carried_unit_info.type == RobotType.MINER
                                || rc.getRoundNum() - turn_i_was_1_from_hq >= 10 + (5 * max_difference(drop_loc, locOfHQ))
                            )
                            && !rc.senseFlooding(drop_loc)
                            && rc.senseElevation(drop_loc) > 0
                            && rc.senseElevation(drop_loc) < 12 + GameConstants.getWaterLevel(rc.getRoundNum())
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
                            && 1 == max_difference(drop_loc, locOfHQ)
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
                // move toward water if carrying enemy unit
                tryMoveToward(
                    RobotType.COW == carried_unit_info.type
                    ? X.WATER
                    : X.WATER_OR_PIT
                );
            }

            if(!rc.isCurrentlyHoldingUnit()) {
                for(RobotInfo rbt : getNearbyOpponentUnits()) {
                    if(rbt.team != rc.getTeam()
                      && rbt.type.canBePickedUp()
                      && rc.canSenseLocation(rbt.location)
                      && rc.senseElevation(rbt.location) > PIT_MAX_ELEVATION
                    ) {
                        // move toward enemy units if not carrying anythin
                        if(Math.random() < 0.95) {
                            hybridStep(rbt.location);
                        }
                    }
                }
            }

            // Generally stay near the HQ
            if(locOfHQ == null
                || (carried_unit_info != null
                    && carried_unit_info.team != rc.getTeam()
                )
                || Math.random() < 0.25
            ) {
                tryGoSomewhere();
            } else {
                hybridStep(locOfHQ);
            }
        }
    }
}