package tannerplayer;
import battlecode.common.*;
import tannerplayer.ObservationRecord;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;

// Tanner's notes on how to make this work:
// Tanner's Mac has 2 versions of Java in /Library/Java/JavaVirtualMachines/
// The current one is determined by the env variable JAVA_HOME
// As of me writing this the default one is jdk-10.0.2.jdk
// This battlecode stuff ONLY works if the other one is used.
// So I have to run:
// export JAVA_HOME='/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home'
// Then run:
// ./gradlew run
// from the same terminal.
// ./gradlew run opens the game in the client app automatically.
// `./gradlew -q tasks` list gradlew commands

// I tried setting JAVA_HOME in .MacOSX/environment.plist and it didn't fix it.
// I had to create .MacOSX and environment.plist, so I deleted them after I saw that it didn't work.
// I've also tried setting JAVA_HOME in ~/.bash_profile

// BUT THIS WORKED:
// Run the export command above then run:
// open client/Battlecode\ Client.app/


enum RotationDirection {
    NULL,
    LEFT,
    RIGHT;
}
class RotDirFuncs {
    static RotationDirection getOpposite(final RotationDirection d) {
        switch(d) {
            case LEFT:
                return RotationDirection.RIGHT;
            case RIGHT:
                return RotationDirection.LEFT;
            default:
                return RotationDirection.NULL;
        }
    }
    static Direction getRotated(final Direction dir, final RotationDirection rd) {
        switch(rd) {
            case LEFT:
                return dir.rotateLeft();
            case RIGHT:
                return dir.rotateRight();
            default:
                return dir;
        }
    }
}


public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    
    // miner
    // only one miner builds the build sequence
    static RobotType[] minerBuildSequence = {
        RobotType.REFINERY,
        RobotType.DESIGN_SCHOOL,
        RobotType.FULFILLMENT_CENTER,
        RobotType.DESIGN_SCHOOL,
        RobotType.VAPORATOR,
        RobotType.NET_GUN,
        RobotType.DESIGN_SCHOOL
    };
    static int numBuildingsBuilt = 0;
    static MapLocation locOfRefinery = null;

    static RobotInfo carried_unit_info = null;

    static int turnCount;
    static int roundNumCreated = -1;


    static int ceilOfSensorRadius;

    static final int MAX_ELEVATION_STEP = GameConstants.MAX_DIRT_DIFFERENCE; // I didn't see this in GameConstants until I'd already made this

    static final int NUM_MINERS_TO_BUILD_INITIALLY = 3; // used by HQ
    static final int TURN_TO_BUILD_ANOTHER_MINER = 150; // used by HQ
    static int num_miners_built = 0; // used by HQ

    static int num_landscapers_built = 0; // design school
    static int num_drones_built = 0; // fulfillment center

    static MapLocation locOfHQ = null;

    static Direction current_dir = null;

    static RotationDirection bug_rot_dir = RotationDirection.NULL; // NULL iff bug_dir == null
    static Direction bug_dir = null;
    static int bug_dist = -1; // -1 iff bug_dir == null
    static MapLocation bug_loc = null; // used to tell if we moved since bugPathingStep was last called

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;
        roundNumCreated = rc.getRoundNum();

        ceilOfSensorRadius = (int) ceil(sqrt(rc.getType().sensorRadiusSquared));


        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                int roundNumBefore = rc.getRoundNum();

                // Here, we've separated the controls into a different method for each RobotType.
                // You can add the missing ones or rewrite this into your own control structure.
                // System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case HQ:                 runHQ();                break;
                    case MINER:              runMiner();             break;
                    case REFINERY:           runRefinery();          break;
                    case VAPORATOR:          runVaporator();         break;
                    case DESIGN_SCHOOL:      runDesignSchool();      break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER:         runLandscaper();        break;
                    case DELIVERY_DRONE:     runDeliveryDrone();     break;
                    case NET_GUN:            runNetGun();            break;
                }

                if(roundNumBefore != rc.getRoundNum()) {
                    System.out.println("Took " + String.valueOf(rc.getRoundNum() - roundNumBefore) + " rounds");
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    
    static boolean isValid(MapLocation ml) {
      return ml.x >= 0 && ml.y >= 0 && ml.x < rc.getMapWidth() && ml.y < rc.getMapHeight();
    }

    static boolean shootOpponentDroneIfPossible() throws GameActionException {
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

    static void runHQ() throws GameActionException {
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

    static int max_difference(MapLocation ml1, MapLocation ml2) {
      return max(abs(ml1.x - ml2.x), abs(ml1.y - ml2.y));
    }

    static void tryGoSomewhere() throws GameActionException {
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


    static void updateLocOfHQ() throws GameActionException {
        // To be called for moving units at the beginning of each turn
        if(locOfHQ == null) {
            for(RobotInfo rbt : rc.senseNearbyRobots()) {
                if(rbt.getType() == RobotType.HQ && rbt.team == rc.getTeam()) {
                    locOfHQ = rbt.location;
                }
            }
        }
        if(opp_hq_loc == null) {
            for(RobotInfo rbt : rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam().opponent()
            )) {
                if(rbt.getType() == RobotType.HQ) {
                    opp_hq_loc = rbt.location;
                }
            }
        }
        if(rc.getType() == RobotType.MINER
          && locOfRefinery == null
        ) {
            for(RobotInfo rbt : rc.senseNearbyRobots()) {
                if(rbt.getType() == RobotType.REFINERY
                  && rbt.team == rc.getTeam()
                ) {
                    locOfRefinery = rbt.location;
                }
            }
        }
    }

    static boolean goToHQ() throws GameActionException {
        if(Math.random() < 0.1 || locOfHQ == null) {
            tryGoSomewhere();
            return true;
        } else {
            return bugPathingStep(locOfHQ);
        }
    }

    static MapLocation opp_hq_loc = null;

    static MapLocation [] getWhereOppHqMightBe() {
        // locOfHQ must not be null
        MapLocation [] possible_locs = {
            new MapLocation(rc.getMapWidth() - locOfHQ.x - 1, locOfHQ.y),
            new MapLocation(rc.getMapWidth() - locOfHQ.x - 1, rc.getMapHeight() - locOfHQ.y - 1),
            new MapLocation(locOfHQ.x, rc.getMapHeight() - locOfHQ.y - 1)
        };
        return possible_locs;
    }
    static boolean [] hq_might_be = {
        true,
        true,
        true
    };
    static boolean has_built_rush_design_school = false;
    static void runMiner() throws GameActionException {
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
            } else if(rc.getRoundNum() > 100) {
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
            if(rc.getRoundNum() < 120) {
                if(!has_moved_toward_soup && rc.getSoupCarrying() > 0) {
                    goToHQ();
                }
            } else if(!has_moved_toward_soup
              && locOfRefinery != null
              && rc.getSoupCarrying() > 0
            ) {
                bugPathingStep(locOfRefinery);
            }
        } else { // !should_mine
            for (Direction dir : directions)
                tryRefine(dir);
        }
        tryGoSomewhere();
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
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

    static void runFulfillmentCenter() throws GameActionException {
        if(num_drones_built < 3) {
            for (Direction dir : directions) {
                boolean did_build = tryBuild(RobotType.DELIVERY_DRONE, dir);
                if(did_build)
                    num_drones_built++;
            }
        }
    }

    static boolean digFromLowestAdjTile() throws GameActionException {
        boolean did_dig = false;
        // find direction to lowest adjacent tile that we can dig
        Direction lowest_unoccupied_dir = null;
        int min_diggable_elev = 30000;
        for(Direction dir : directions) {
            MapLocation l = rc.getLocation().add(dir);
            if(isValid(l)
                && (locOfHQ == null
                    || max_difference(l, locOfHQ) >= 2
                )
                && rc.canDigDirt(dir)
                && rc.canSenseLocation(l)
                && !rc.isLocationOccupied(l) // don't dig on occupied tiles
                && rc.senseElevation(l) < min_diggable_elev
            ) {
                lowest_unoccupied_dir = dir;
                min_diggable_elev = rc.senseElevation(l);
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

    static void runLandscaper() throws GameActionException {
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
            for(Direction dir : directions) {
                MapLocation l = rc.getLocation().add(dir);
                if(rc.canDepositDirt(dir)
                  && max_difference(l, locOfHQ) == 1
                ) {
                    can_deposit_adj_to_hq = true;
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
                if(rc.canSenseLocation(rc.getLocation())
                  && min_elev >= rc.senseElevation(rc.getLocation())
                  && rc.canDepositDirt(Direction.CENTER)
                ) {
                    rc.depositDirt(Direction.CENTER);
                } else {
                    Direction dir_to_deposit = null;
                    for(Direction dir : directions) {
                        MapLocation l = rc.getLocation().add(dir);
                        if(isValid(l)
                            && max_difference(l, locOfHQ) == 1
                            && rc.canSenseLocation(l)
                            && rc.senseElevation(l) < min_elev + MAX_ELEVATION_STEP
                        ) {
                            dir_to_deposit = dir;
                        }
                    }
                    if(dir_to_deposit != null && 
                        tryDeposit(dir_to_deposit)
                    ) {
                        // System.out.println("I deposited dirt " + rc.getLocation().add(dir_to_deposit).toString());
                    } else if(rc.getDirtCarrying() > 0) {
                        bugPathingStep(min_elev_loc);
                    }
                }
            }
            if(rc.getDirtCarrying() >= RobotType.LANDSCAPER.dirtLimit) {
                goToHQ();
            }
        }
        tryGoSomewhere();
    }

    enum X{WATER, SOUP;};
    static boolean tryMoveToward(X x) throws GameActionException {
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

    static void runDeliveryDrone() throws GameActionException {
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
                        // drop friendly units anywhere, even on water
                        Direction random_dir = randomDirection();
                        if(rc.canDropUnit(random_dir)) {
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

    static void runNetGun() throws GameActionException {
        shootOpponentDroneIfPossible();
    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    static boolean canSafeMove(Direction dir) throws GameActionException {
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
                return rc.canMove(dir) && (!rc.canSenseLocation(loc) || !rc.senseFlooding(loc));
        }
    }

    static boolean safeTryMove(Direction dir) throws GameActionException {
        // This func works for all unit types
        // Do not call if you are a building
        // VERY HIGH COMPLEXITY for drones
        if(canSafeMove(dir)) {
            rc.move(dir);
            return true;
        }
        return false;
    }


    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    static boolean bugTryMoveToward(MapLocation dest) throws GameActionException {
        Direction target_dir = rc.getLocation().directionTo(dest);
        if(safeTryMove(target_dir)) {
            return true;
        } else {
            bug_rot_dir = Math.random() < 0.5 ? RotationDirection.RIGHT : RotationDirection.LEFT;
            bug_dir = target_dir;
            bug_dist = max_difference(rc.getLocation(), dest);
            return false;
        }
    }
    static boolean bugPathingStep(MapLocation dest) throws GameActionException {
        // dest must not be null
        boolean did_move = false;
        if(rc.getLocation() != bug_loc) {
            bug_dir = null;
            bug_dist = -1;
            bug_rot_dir = RotationDirection.NULL;
        }
        if(rc.isReady()) {
            if(bug_dir == null) {
                // This function modifies the static variables
                bugTryMoveToward(dest);
            }
            if(bug_dir != null) {
                Direction local_dir = bug_dir;
                do {
                    local_dir = RotDirFuncs.getRotated(local_dir, bug_rot_dir);
                } while(canSafeMove(local_dir) && !local_dir.equals(bug_dir));
                if(local_dir.equals(bug_dir)) {
                    if(rc.getLocation().directionTo(dest) == bug_dir.opposite()) {
                        tryGoSomewhere();
                        bug_dir = null;
                        bug_rot_dir = RotationDirection.NULL;
                    } else {
                        // it moved
                        bug_dir = null;
                        bug_rot_dir = RotationDirection.NULL;

                        // This function modifies the static variables
                        bugTryMoveToward(dest);
                    }
                } else {
                    bug_dir = local_dir;
                    do {
                        bug_dir = RotDirFuncs.getRotated(bug_dir, RotDirFuncs.getOpposite(bug_rot_dir));
                    } while(!canSafeMove(bug_dir) && !bug_dir.equals(local_dir));
                    if(!bug_dir.equals(local_dir)) {
                        rc.move(bug_dir);
                        did_move = true;
                        if(max_difference(dest, rc.getLocation()) < bug_dist) {
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

    static boolean trySafeNonobstructiveDig(Direction dir) throws GameActionException {
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

    static boolean tryNonobstructiveDig(Direction dir) throws GameActionException {
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

    static int firstRoundNum = -1;
    static final int MAX_NUM_MESSAGES_TO_STORE = 500;
    static int [][] recentMessages = new int[MAX_NUM_MESSAGES_TO_STORE][GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH];
    static int recentMessagesLength = 0;
    static boolean tryToConfuseOpponentWithBlockchain() throws GameActionException {
        boolean submitted = false;
        if(firstRoundNum < 0) {
            firstRoundNum = rc.getRoundNum();
        } else {
            if(rc.getRoundNum() > firstRoundNum) {
                Transaction [] t = rc.getBlock(rc.getRoundNum() - 1);
                for(int k = 0; k < t.length; k++) {
                    if(t[k] != null && Math.random() < (1 - ((double)recentMessagesLength / MAX_NUM_MESSAGES_TO_STORE))) {
                        recentMessages[recentMessagesLength] = t[k].getMessage();
                        recentMessagesLength++;
                    }
                }
            }
            if(Math.random() < 0.05 && recentMessagesLength > 0) {
                int [] message = recentMessages[(int) (Math.random() * recentMessagesLength)];
                int [] message_copy = new int[message.length];
                for(int k = 0; k < message.length; k++) {
                    if(Math.random() < 0.5) {
                        message_copy[k] = message[k];
                    } else {
                        if(Math.random() < 0.5) {
                            message_copy[k] = (int) (Math.random() * rc.getMapWidth());
                        } else {
                            message_copy[k] = message[(int) (Math.random() * message.length)];
                        }
                    }
                }
                int cost = 1 + ((int) (Math.random() * 3));
                if(rc.isReady()
                    && rc.canSubmitTransaction(message_copy, cost)
                ) {
                    rc.submitTransaction(message_copy, cost);
                }
                submitted = true;
            }
        }
        return submitted;
    }


    static boolean tryDeposit(final Direction dir) throws GameActionException {
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
    static boolean tryMine(Direction dir) throws GameActionException {
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
    static boolean tryRefine(Direction dir) throws GameActionException {
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
