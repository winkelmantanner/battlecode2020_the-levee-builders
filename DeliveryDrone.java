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



    public void runTurn() throws GameActionException {
        updateLocOfHQ();
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
                    ) {
                        // pick up any enemy unit
                        rc.pickUpUnit(rbt.ID);
                        carried_unit_info = rbt;
                        break;
                    } else if(
                        rbt != null
                        && rbt.type == RobotType.MINER
                        && rbt.team == rc.getTeam()
                        && locOfHQ != null
                        && max_difference(
                            rbt.location,
                            locOfHQ
                        ) == 1
                        && rc.canPickUpUnit(rbt.ID)
                    ) {
                        // pick up miners off the levee
                        rc.pickUpUnit(rbt.ID);
                        carried_unit_info = rbt;
                        break;
                    }

                    if(carried_unit_info != null
                        && carried_unit_info.team != rc.getTeam()
                        && rc.isCurrentlyHoldingUnit()
                        && rc.senseFlooding(ml)
                        && rc.canDropUnit(dir)
                    ) {
                        // drop enemy units into water
                        rc.dropUnit(dir);
                        carried_unit_info = null;
                        break;
                    } else if(
                        carried_unit_info != null
                        && carried_unit_info.team == rc.getTeam()
                        && rc.isCurrentlyHoldingUnit()
                        && carried_unit_info.type == RobotType.MINER
                    ) {
                        // drop friendly units on safe ground
                        Direction random_dir = randomDirection();
                        MapLocation drop_loc = rc.getLocation().add(random_dir);
                        if(rc.canDropUnit(random_dir)
                            && rc.canSenseLocation(drop_loc)
                            && !rc.senseFlooding(drop_loc)
                            && rc.senseElevation(drop_loc) > 0
                        ) {
                            rc.dropUnit(random_dir);
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
                tryMoveToward(X.WATER);
            }

            if(!rc.isCurrentlyHoldingUnit()) {
                for(RobotInfo rbt : getNearbyOpponentUnits()) {
                    if(rbt.team != rc.getTeam()
                      && rbt.type.canBePickedUp()
                    ) {
                        // move toward enemy units if not carrying anythin
                        if(Math.random() < 0.95) {
                            bugPathingStep(rbt.location);
                        }
                    }
                }
            }

            // Generally stay near the HQ
            if(locOfHQ == null
              || rc.isCurrentlyHoldingUnit()
              || Math.random() < 0.25
            ) {
                tryGoSomewhere();
            } else {
                bugPathingStep(locOfHQ);
            }
        }
    }
}