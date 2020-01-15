package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;

public strictfp class Unit extends Robot {

    Unit(RobotController rc) {
      super(rc);
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

    RobotInfo carried_unit_info = null;





    Direction current_dir = null;

    RotationDirection bug_rot_dir = RotationDirection.NULL; // NULL iff bug_dir == null
    Direction bug_dir = null;
    int bug_dist = -1; // -1 iff bug_dir == null
    MapLocation bug_loc = null; // used to tell if we moved since bugPathingStep was last called



    void tryGoSomewhere() throws GameActionException {
        if(rc.isReady()) {
            if(Math.random() < 0.2) {
                current_dir = null;
            }
            if(current_dir != null) {
                if(!safeTryMove(current_dir)) {
                    current_dir = null;
                }
            }
            if(current_dir == null) {
                current_dir = randomDirection();
                int infLoopPreventer = 10;
                do {
                    if(!safeTryMove(current_dir)) {
                        current_dir = null;
                    }
                    infLoopPreventer--;
                } while(current_dir == null && infLoopPreventer > 0);
            }
        }
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
                case MINER:              runMiner();             break;
                case LANDSCAPER:         runLandscaper();        break;
                case DELIVERY_DRONE:     runDeliveryDrone();     break;
            }
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }
        endTurn();
    }


    boolean goToHQ() throws GameActionException {
        if(Math.random() < 0.1 || locOfHQ == null) {
            tryGoSomewhere();
            return true;
        } else {
            return bugPathingStep(locOfHQ);
        }
    }







    boolean [] hq_might_be = {
        true,
        true,
        true
    };
    boolean has_built_rush_design_school = false;
    void runMiner() throws GameActionException {
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
    }









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

    boolean im_stuck() throws GameActionException {
        // because of the functions they provide, this function has undefined behavior, along with most movement-related functions.
        // I with the functions they provide would actually work.
        // Don't rely on this function returning the same if you call it two times consecutively.
        for(Direction d : directions) {
            if(canSafeMove(d)) {
                return true;
            }
        }
        return false;
    }

    void runLandscaper() throws GameActionException {
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
                // dig from lowest adjacent file that is not occupied
                digFromLowestAdjTile();
            } else if(rc.canSenseLocation(target_tile)
                && rc.senseElevation(target_tile) - rc.senseElevation(rc.getLocation()) > 3
                && max_difference(target_tile, opp_hq_loc) == 1
                && rc.canDigDirt(rc.getLocation().directionTo(opp_hq_loc))
            ) {
                // dig through opp levee
                rc.digDirt(rc.getLocation().directionTo(opp_hq_loc));
            } else {
                bugPathingStep(opp_hq_loc);
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

            // dig from the lowest adjacent tile that is not occupied by a robot
            digFromLowestAdjTile();

            boolean can_deposit_adj_to_hq = false;
            Direction dir_we_can_deposit_adj_to_hq = null; // not necessarily the lowest
            for(Direction dir : directions) {
                MapLocation l = rc.getLocation().add(dir);
                if(rc.canDepositDirt(dir)
                  && max_difference(l, locOfHQ) == 1
                ) {
                    can_deposit_adj_to_hq = true;
                    dir_we_can_deposit_adj_to_hq = dir;
                }
            }
            if(can_deposit_adj_to_hq) {
                int min_elev = 30000;
                MapLocation min_elev_loc = null;
                for(int dx = -1; dx <= 1; dx++) {
                    for(int dy = -1; dy <= 1; dy++) {
                        MapLocation ml = locOfHQ.translate(dx, dy);
                        if((dx != 0 || dy != 0) && isValid(ml) && rc.canSenseLocation(ml)) {
                            int elev = rc.senseElevation(ml);
                            if(elev < min_elev) {
                                min_elev = elev;
                                min_elev_loc = ml;
                            }
                        }
                    }
                }
                if(max_difference(locOfHQ, rc.getLocation()) == 1) {
                    Direction dir_to_deposit = null;
                    if(rc.canSenseLocation(rc.getLocation())
                        && min_elev >= rc.senseElevation(rc.getLocation())
                        && rc.canDepositDirt(Direction.CENTER)
                    ) {
                        dir_to_deposit = Direction.CENTER;
                    } else {
                        int yet_another_min_elev = 12345;
                        for(Direction dir : directions_including_center) {
                            MapLocation l = rc.getLocation().add(dir);
                            if(isValid(l)
                                && max_difference(l, locOfHQ) == 1
                                && rc.canSenseLocation(l)
                                && rc.senseElevation(l) < min_elev + MAX_ELEVATION_STEP
                                && rc.senseElevation(l) < yet_another_min_elev
                            ) {
                                dir_to_deposit = dir;
                                yet_another_min_elev = rc.senseElevation(l);
                            }
                        }
                    }
                    if(dir_to_deposit != null && 
                        tryDeposit(dir_to_deposit)
                    ) {
                        // rc.setIndicatorDot(rc.getLocation().add(dir_to_deposit), 0, 255, 0);
                        // System.out.println("I deposited dirt " + rc.getLocation().add(dir_to_deposit).toString());
                    } else if(rc.getDirtCarrying() > 0) {
                        bugPathingStep(min_elev_loc);
                    }
                } else if(max_difference(locOfHQ, rc.getLocation()) == 2) {
                    if(rc.canSenseLocation(rc.getLocation())
                        && rc.senseElevation(rc.getLocation()) < 400
                    ) {
                        // we know we have dirt since can_deposit_adj_to_hq
                        rc.depositDirt(Direction.CENTER);
                    } else {
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
            if(rc.getDirtCarrying() >= RobotType.LANDSCAPER.dirtLimit) {
                goToHQ();
            }
        }
        tryGoSomewhere();
    }

    enum X{WATER, SOUP;};
    boolean tryMoveToward(X x) throws GameActionException {
        boolean found = false;
        if(rc.isReady()) {
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation();
                boolean stop = false;
                int last_elevation = rc.senseElevation(ml);
                while(!stop) {
                    ml = ml.add(dir);
                    if(!isValid(ml)
                        || !rc.canSenseLocation(ml)
                        || (rc.senseFlooding(ml)
                            && rc.getType() != RobotType.DELIVERY_DRONE)
                        || (abs(rc.senseElevation(ml) - last_elevation) > MAX_ELEVATION_STEP
                            && rc.getType() != RobotType.DELIVERY_DRONE)
                        || null != rc.senseRobotAtLocation(ml)
                    ) {
                        stop = true;
                    } else {
                        last_elevation = rc.senseElevation(ml);
                        switch(x) {
                            case WATER:
                                if(rc.senseFlooding(ml) && rc.canMove(dir)) {
                                    current_dir = dir;
                                    found = true;
                                    stop = true;
                                }
                                break;
                            case SOUP:
                                if(0 < rc.senseSoup(ml) && rc.canMove(dir)) {
                                    current_dir = dir;
                                    found = true;
                                    stop = true;
                                }
                                break;
                        }
                    }
                }
            }
            if(found) {
                rc.move(current_dir); // we already checked canMove and isReady
            }
        }
        return found;
    }

    void runDeliveryDrone() throws GameActionException {
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
                for(RobotInfo rbt : rc.senseNearbyRobots(
                    rc.getLocation(),
                    rc.getType().sensorRadiusSquared,
                    rc.getTeam().opponent()
                )) {
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

    boolean canSafeMove(Direction dir) throws GameActionException {
        // This func works for all unit types
        // Do not call if you are a building
        // VERY HIGH COMPLEXITY for drones
        if(dir == null) {
            return false;
        }
        MapLocation loc = rc.getLocation().add(dir);
        if(!isValid(loc)) {
            return false;
        }
        switch(rc.getType()) {
            case DELIVERY_DRONE:
                for(RobotInfo rbt : rc.senseNearbyRobots()) {
                    if(rbt.team == rc.getTeam().opponent()
                      && rbt.type.canShoot()
                      && loc.isWithinDistanceSquared(
                          rbt.location,
                          GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED
                    )) {
                       return false;
                    }
                }
                return rc.canMove(dir);
            default:
                float water_level_in_5 = GameConstants.getWaterLevel(rc.getRoundNum() + 5);
                return rc.canMove(dir)
                    && (!rc.canSenseLocation(loc) || !rc.senseFlooding(loc))
                    && (!rc.canSenseLocation(rc.getLocation())
                        || water_level_in_5 >= rc.senseElevation(rc.getLocation())
                        || water_level_in_5 < rc.senseElevation(loc)
                    );
        }
    }

    boolean safeTryMove(Direction dir) throws GameActionException {
        // This func works for all unit types
        // Do not call if you are a building
        // VERY HIGH COMPLEXITY for drones
        if(canSafeMove(dir)) {
            rc.move(dir);
            return true;
        }
        return false;
    }










    HashMap<String, ArrayList<Direction> > where_ive_been = new HashMap<String, ArrayList<Direction> >();
    boolean bugCanSafeMove(Direction dir) throws GameActionException {
        String k = rc.getLocation().toString();
        if(canSafeMove(dir)
            && (!where_ive_been.containsKey(k)
                || where_ive_been.get(k).indexOf(dir) == -1 // yes, Java has short-circuit evaluation
            )
        ) {
            return true;
        } else {
            if(canSafeMove(dir)) {
                System.out.println("move prevented " + dir.toString());
            }
            return false;
        }
    }
    boolean bugSafeTryMove(Direction dir) throws GameActionException {
        String k = rc.getLocation().toString();
        if(bugCanSafeMove(dir)) {
            if(!where_ive_been.containsKey(k)) {
                where_ive_been.put(k, new ArrayList<Direction>());
            }
            where_ive_been.get(k).add(dir);
            rc.move(dir);
            return true;
        } else {
            return false;
        }
    }
    boolean bugTryMoveToward(MapLocation dest) throws GameActionException {
        Direction target_dir = rc.getLocation().directionTo(dest);
        if(bugSafeTryMove(target_dir)) {
            return true;
        } else {
            bug_rot_dir = Math.random() < 0.5 ? RotationDirection.RIGHT : RotationDirection.LEFT;
            bug_dir = target_dir;
            bug_dist = max_difference(rc.getLocation(), dest);
            return false;
        }
    }
    boolean bugPathingStep(MapLocation dest) throws GameActionException {
        // dest must not be null
        boolean did_move = false;
        if(rc.getLocation() != bug_loc) {
            bug_dir = null;
            bug_dist = -1;
            bug_rot_dir = RotationDirection.NULL;
            where_ive_been.clear();
        }
        if(rc.isReady()) {
            if(bug_dir == null) {
                // This function modifies the variables
                bugTryMoveToward(dest);
            }
            if(bug_dir != null) {
                Direction local_dir = bug_dir;
                do {
                    local_dir = RotDirFuncs.getRotated(local_dir, bug_rot_dir);
                } while(bugCanSafeMove(local_dir) && !local_dir.equals(bug_dir));
                if(local_dir.equals(bug_dir)) {
                    if(rc.getLocation().directionTo(dest) == bug_dir.opposite()) {
                        for(int k = 0; k < 10; k++) {
                            if(bugSafeTryMove(randomDirection())) {
                                break;
                            }
                        }
                        bug_dir = null;
                        bug_rot_dir = RotationDirection.NULL;
                    } else {
                        // it moved
                        bug_dir = null;
                        bug_rot_dir = RotationDirection.NULL;

                        // This function modifies the variables
                        bugTryMoveToward(dest);
                    }
                } else {
                    bug_dir = local_dir;
                    do {
                        bug_dir = RotDirFuncs.getRotated(bug_dir, RotDirFuncs.getOpposite(bug_rot_dir));
                    } while(!bugCanSafeMove(bug_dir) && !bug_dir.equals(local_dir));
                    if(!bug_dir.equals(local_dir)) {
                        MapLocation loc_before = rc.getLocation();
                        bugSafeTryMove(bug_dir);
                        did_move = true;
                        if(max_difference(dest, rc.getLocation()) < bug_dist
                            && max_difference(dest, rc.getLocation()) >= max_difference(dest, loc_before)
                        ) {
                            bug_dir = null;
                            bug_dist = -1;
                            bug_rot_dir = RotationDirection.NULL;
                        }
                    }
                }
            }
        }
        bug_loc = rc.getLocation();
        return did_move;
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