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



public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    static int roundNumCreated = -1;


    static int ceilOfSensorRadius;

    static final int MAX_ELEVATION_STEP = 3; // They didn't make this programmatically accessable.  The specification says 3.

    static final int NUM_MINERS_TO_BUILD = 3; // used by HQ
    static int num_miners_built = 0; // used by HQ

    static int num_landscapers_built = 0; // design school

    static MapLocation locOfHQ = null;

    static HashMap<String, ObservationRecord> where_ive_been_map = new HashMap<String, ObservationRecord>();
    static ArrayList<MapLocation> where_ive_been = new ArrayList<MapLocation>();
    static int where_ive_been_obstruction_index_if_known = -1;
    static Direction current_dir = null;



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

                if(Clock.getBytecodeNum() >= 2000) {
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
        for(Direction dir : directions) {
            MapLocation l = rc.getLocation().add(dir);
            if(locOfHQ == null) {
                if(isValid(l) && rc.canSenseLocation(l)) {
                    RobotInfo rbt_at_l_or_null = rc.senseRobotAtLocation(l);
                    if(rbt_at_l_or_null != null && rbt_at_l_or_null.type == RobotType.HQ && rbt_at_l_or_null.team == rc.getTeam()) {
                        locOfHQ = l;
                    }
                }
            } else if(l.equals(locOfHQ)) {
                where_ive_been.clear();
                where_ive_been_map.clear();
                where_ive_been_obstruction_index_if_known = -1;
            }
        }
        if(where_ive_been.size() == 0 || rc.getLocation() != where_ive_been.get(where_ive_been.size() - 1)) {
            where_ive_been.add(rc.getLocation());
        }
        String location_string = rc.getLocation().toString();
        ObservationRecord prev_rec_if_any = where_ive_been_map.get(location_string);
        if(null == prev_rec_if_any) {
            where_ive_been_map.put(location_string, new ObservationRecord(rc, rc.getLocation(), where_ive_been.size() - 1));
        } else if(prev_rec_if_any.where_ive_been_index < where_ive_been.size() - 1) {
            for(
              int index = where_ive_been.size() - 1;
              index > prev_rec_if_any.where_ive_been_index;
              index--
            ) {
                where_ive_been_map.remove(where_ive_been.get(index).toString()); // does not throw if the key is not in the map
                where_ive_been.remove(index);
            }
            if(prev_rec_if_any.where_ive_been_index <= where_ive_been_obstruction_index_if_known) {
                where_ive_been_obstruction_index_if_known = -1;
            }
            where_ive_been_map.put(location_string, new ObservationRecord(rc, rc.getLocation(), where_ive_been.size() - 1));
        }
    }

    static boolean goBackAlongWhereIveBeen() throws GameActionException {
        // this function actually removes the last entry from where_ive_been
        if(where_ive_been_obstruction_index_if_known < 0 && rc.isReady()) {
            int last_index_im_not_at = where_ive_been.size() - 1;
            while(last_index_im_not_at >= 0 && rc.getLocation().equals(where_ive_been.get(last_index_im_not_at))) {
                last_index_im_not_at--;
            }
            if(last_index_im_not_at >= 0 && safeTryMove(rc.getLocation().directionTo(where_ive_been.get(last_index_im_not_at)))) {
                for(int i = where_ive_been.size() - 1; i >= last_index_im_not_at; i--) {
                    where_ive_been.remove(i);
                }
                return true;
            } else {
              where_ive_been_obstruction_index_if_known = last_index_im_not_at;
            }
        }
        return false;
    }

    static void runMiner() throws GameActionException {
        updateWhereIveBeenRecords();
        for (Direction dir : directions)
            if (tryMine(dir))
                System.out.println("I mined soup! " + rc.getSoupCarrying());
        for (Direction dir : directions)
            if (tryRefine(dir))
                System.out.println("I refined soup! " + rc.getTeamSoup());
        if(roundNumCreated <= 2) {
            // only one miner should build so that we can control what is built
            tryBuild(randomSpawnedByMiner(), randomDirection());
        }

        boolean has_moved_toward_soup = false;
        if(rc.getSoupCarrying() < RobotType.MINER.soupLimit) {
            has_moved_toward_soup = tryGoTowardSoup();
        }
        if(!has_moved_toward_soup && rc.getSoupCarrying() > 0) {
            goBackAlongWhereIveBeen();
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

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
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
