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
    static Direction rotate(final Direction dir, final RotationDirection rd) {
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
    
    static RobotType[] minerBuildSequence = {
        RobotType.DESIGN_SCHOOL,
        RobotType.FULFILLMENT_CENTER,
        RobotType.DESIGN_SCHOOL,
        RobotType.REFINERY,
        RobotType.VAPORATOR,
        RobotType.NET_GUN,
        RobotType.DESIGN_SCHOOL
    };
    static int numBuildingsBuilt = 0;

    static int turnCount;
    static int roundNumCreated = -1;


    static int ceilOfSensorRadius;

    static final int MAX_ELEVATION_STEP = GameConstants.MAX_DIRT_DIFFERENCE; // I didn't see this in GameConstants until I'd already made this

    static final int NUM_MINERS_TO_BUILD = 4; // used by HQ
    static int num_miners_built = 0; // used by HQ

    static int num_landscapers_built = 0; // design school

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


        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
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

                if(Clock.getBytecodeNum() >= 5000) {
                    System.out.println("Bytecodes " + String.valueOf(Clock.getBytecodeNum()));
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

    static void runHQ() throws GameActionException {
        tryBlockchain();
        if(num_miners_built < NUM_MINERS_TO_BUILD && rc.getRoundNum() > 2 * num_miners_built) {
            for (Direction dir : directions)
                if(tryBuild(RobotType.MINER, dir)) {
                    num_miners_built++;
                }
        }
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
                if(!safeTryMove(current_dir)) {
                    current_dir = null;
                    // and just don't move
                }
            }
        }
    }

    static boolean tryGoTowardSoup() throws GameActionException {
        boolean found_soup = false;
        if(rc.isReady()) {
            for(Direction dir : directions) {
                MapLocation ml = rc.getLocation();
                boolean stop = false;
                int last_elevation = rc.senseElevation(ml);
                while(!stop) {
                    ml = ml.add(dir);
                    if(!isValid(ml)
                      || !rc.canSenseLocation(ml)
                      || rc.senseFlooding(ml)
                      || abs(rc.senseElevation(ml) - last_elevation) > MAX_ELEVATION_STEP
                      || null != rc.senseRobotAtLocation(ml)
                    ) {
                      stop = true;
                    } else {
                        last_elevation = rc.senseElevation(ml);
                        if(0 < rc.senseSoup(ml) && rc.canMove(dir)) {
                            current_dir = dir;
                            found_soup = true;
                            stop = true;
                        }
                    }
                }
            }
            if(found_soup) {
                rc.move(current_dir); // we already checked canMove and isReady
            }
        }

        return found_soup;
    }

    static void updateWhereIveBeenRecords() throws GameActionException {
        // To be called for moving units at the beginning of each turn
        if(locOfHQ == null) {
            for(RobotInfo rbt : rc.senseNearbyRobots()) {
                if(rbt.getType() == RobotType.HQ && rbt.team == rc.getTeam()) {
                    locOfHQ = rbt.location;
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

    static void runMiner() throws GameActionException {
        updateWhereIveBeenRecords();
        if(roundNumCreated <= 2) {
            if(max_difference(locOfHQ, rc.getLocation()) >= 3) {
                // only one miner should build so that we can control what is built
                RobotType type_to_build = null;
                if(numBuildingsBuilt < minerBuildSequence.length) {
                    type_to_build = minerBuildSequence[numBuildingsBuilt];
                } else {
                    type_to_build = randomSpawnedByMiner();
                }
                if(tryBuild(type_to_build, randomDirection())) {
                    numBuildingsBuilt++;
                }
            } else if(rc.getRoundNum() > 100) {
                tryGoSomewhere();
            }
        }
        for (Direction dir : directions)
            if (tryMine(dir))
                System.out.println("I mined soup! " + rc.getSoupCarrying());
        for (Direction dir : directions)
            if (tryRefine(dir))
                System.out.println("I refined soup! " + rc.getTeamSoup());

        boolean has_moved_toward_soup = false;
        if(rc.getSoupCarrying() < RobotType.MINER.soupLimit) {
            has_moved_toward_soup = tryGoTowardSoup();
        }
        if(!has_moved_toward_soup && rc.getSoupCarrying() > 0) {
            goToHQ();
        }
        tryGoSomewhere();
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
      for (Direction dir : directions) {
        if(rc.canBuildRobot(RobotType.LANDSCAPER, dir)) {
          rc.buildRobot(RobotType.LANDSCAPER, dir);
          num_landscapers_built++;
        }
      }
    }

    static void runFulfillmentCenter() throws GameActionException {
        for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
    }

    static void runLandscaper() throws GameActionException {
        updateWhereIveBeenRecords();
        if(locOfHQ != null) {
            if(max_difference(locOfHQ, rc.getLocation()) >= 7) {
                for(Direction dir : directions) {
                    trySafeNonobstructiveDig(dir);
                }
            } else {
                for(Direction dir : directions) {
                    MapLocation l = rc.getLocation().add(dir);
                    if(max_difference(l, locOfHQ) == 1) {
                        int min_elev = 12345;
                        MapLocation min_elev_loc = null;
                        for(int dx = -1; dx <= 1; dx++) {
                            for(int dy = -1; dy <= 1; dy++) {
                                MapLocation ml = locOfHQ.translate(dx, dy);
                                if((dx != 0 || dy != 0) && rc.canSenseLocation(ml)) {
                                    int elev = rc.senseElevation(ml);
                                    if(elev < min_elev) {
                                        min_elev = elev;
                                        min_elev_loc = ml;
                                    }
                                }
                            }
                        }
                        if(rc.canSenseLocation(l) && rc.senseElevation(l) < min_elev + MAX_ELEVATION_STEP) {
                            tryDeposit(dir);
                        } else if(rc.getDirtCarrying() > 0) {
                            bugPathingStep(min_elev_loc);
                        }
                    }
                }
            }
            if(rc.getDirtCarrying() >= RobotType.LANDSCAPER.dirtLimit) {
                goToHQ();
            }
        }
        tryGoSomewhere();
    }

    static void runDeliveryDrone() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        if (!rc.isCurrentlyHoldingUnit()) {
            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);

            if (robots.length > 0) {
                // Pick up a first robot within range
                rc.pickUpUnit(robots[0].getID());
                System.out.println("I picked up " + robots[0].getID() + "!");
            }
        } else {
            // No close robots, so search for robots within sight radius
            tryMove(randomDirection());
        }
    }

    static void runNetGun() throws GameActionException {

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
        MapLocation loc = rc.getLocation().add(dir);
        return rc.canMove(dir) && (!rc.canSenseLocation(loc) || !rc.senseFlooding(loc));
    }

    static boolean safeTryMove(Direction dir) throws GameActionException {
        MapLocation landing_space = rc.getLocation().add(dir);
        if ( rc.canSenseLocation(landing_space)
          && !rc.senseFlooding(landing_space)
        ) {
            return tryMove(dir);
        }
        return false;
    }

    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
        // MapLocation loc = rc.getLocation();
        // if (loc.x < 10 && loc.x < loc.y)
        //     return tryMove(Direction.EAST);
        // else if (loc.x < 10)
        //     return tryMove(Direction.SOUTH);
        // else if (loc.x > loc.y)
        //     return tryMove(Direction.WEST);
        // else
        //     return tryMove(Direction.NORTH);
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
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
        if(!safeTryMove(target_dir)) {
            bug_rot_dir = Math.random() < 0.5 ? RotationDirection.RIGHT : RotationDirection.LEFT;
            bug_dir = target_dir;
            bug_dist = max_difference(rc.getLocation(), dest);
            return false;
        }
        return true;
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
                    local_dir = RotDirFuncs.rotate(local_dir, bug_rot_dir);
                } while(canSafeMove(local_dir) && !local_dir.equals(bug_dir));
                if(local_dir.equals(bug_dir)) {
                    // it moved
                    bug_dir = null;
                    bug_rot_dir = RotationDirection.NULL;

                    // This function modifies the static variables
                    bugTryMoveToward(dest);
                } else {
                    bug_dir = local_dir;
                    do {
                        bug_dir = RotDirFuncs.rotate(bug_dir, RotDirFuncs.getOpposite(bug_rot_dir));
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
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }


    static void tryBlockchain() throws GameActionException {
        if (turnCount < 3) {
            int[] message = new int[10];
            for (int i = 0; i < 10; i++) {
                message[i] = 123;
            }
            if (rc.canSubmitTransaction(message, 10))
                rc.submitTransaction(message, 10);
        }
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
