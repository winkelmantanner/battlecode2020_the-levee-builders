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


    static int ceilOfSensorRadius;

    static final int MAX_ELEVATION_STEP = 3; // They didn't make this programmatically accessable.  The specification says 3.

    static final int NUM_MINERS_TO_BUILD = 1; // used by HQ
    static int num_miners_built = 0; // used by HQ

    static ArrayList<MapLocation> planned_route = null;
    static int planned_route_index = -1;

    static ObservationRecord [][] internalMap = null;


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

        ceilOfSensorRadius = (int) ceil(sqrt(rc.getType().sensorRadiusSquared));

        if(internalMap == null) {
            internalMap = new ObservationRecord[rc.getMapWidth()][rc.getMapHeight()];
        }

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                final int min_sensable_x = max(0, rc.getLocation().x - ceilOfSensorRadius);
                final int max_sensable_x = min(rc.getMapWidth() - 1, rc.getLocation().x + ceilOfSensorRadius);
                final int min_sensable_y = max(0, rc.getLocation().y - ceilOfSensorRadius);
                final int max_sensable_y = min(rc.getMapHeight() - 1, rc.getLocation().y + ceilOfSensorRadius);
                for(
                    int x = min_sensable_x;
                    x <= max_sensable_x;
                    x++
                ) {
                    for(
                      int y = min_sensable_y;
                      y <= max_sensable_y;
                      y++
                    ) {
                        if(rc.canSenseLocation(new MapLocation(x, y))) {
                            internalMap[x][y] = new ObservationRecord(rc, new MapLocation(x, y));
                        }
                    }
                }

                // for(int y = 0; y < rc.getMapHeight(); y++) {
                //   for(int x = 0; x < rc.getMapWidth(); x++) {
                //     if(internalMap[x][y] == null) {
                //        System.out.print(".");
                //     } else if(internalMap[x][y].elevation < 0) {
                //       System.out.print("-");
                //     } else if(internalMap[x][y].elevation > 10) {
                //       System.out.print("+");
                //     } else {
                //       System.out.print(String.valueOf(internalMap[x][y].elevation));
                //     }
                //   }
                //   System.out.println("");
                // }

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

    static boolean tryPlannedRouteMove() {
        boolean success = false;
        try {
            if ( planned_route_index >= 0
              && planned_route_index < planned_route.size()
              && (planned_route_index == 0 || rc.getLocation().equals(planned_route.get(planned_route_index - 1)))
            ) {
                MapLocation dest = planned_route.get(planned_route_index);
                Direction dir = rc.getLocation().directionTo(dest);
                if(rc.canMove(dir) && rc.canSenseLocation(dest) && !rc.senseFlooding(dest)) {
                    if(tryMove(dir)) {
                        planned_route_index++;
                        success = true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception in tryPlannedRouteMove");
            e.printStackTrace();
        }
        return success;
    }

    static void explore() {
        try {
            if ( !tryPlannedRouteMove() ) {
                int qi = 0;
                ArrayList<MapLocation> q = new ArrayList<MapLocation>();
                HashMap<String, MapLocation> prev = new HashMap<String, MapLocation>();
                MapLocation target_map_location = null;
                q.add(rc.getLocation());
                boolean stop = false;
                while (!stop) {
                    MapLocation current = q.get(qi);
                    ObservationRecord current_record = internalMap[current.x][current.y];
                    if(current_record != null) {
                        for (Direction dir : directions) {
                            MapLocation result = current.add(dir);
                            String result_string = result.toString();
                            if (isValid(result) && !prev.containsKey(result_string)) {
                                ObservationRecord result_record = internalMap[result.x][result.y];
                                if (result_record == null) {
                                    stop = true;
                                    target_map_location = result;
                                    prev.put(result_string, current);
                                } else if (abs(current_record.elevation - result_record.elevation) <= MAX_ELEVATION_STEP
                                  && result_record.building_if_any == null
                                  && !result_record.was_flooded
                                ) {
                                    q.add(result);
                                    prev.put(result_string, current);
                                }
                            }
                        }
                    } else {
                      System.out.println("current_record was null");
                      stop = true;
                    }
                    qi++;
                    if(qi >= q.size()) {
                      stop = true;
                    }
                }
                planned_route = new ArrayList<MapLocation>();
                planned_route_index = 0;
                if(target_map_location != null) {
                  int infLoopPreventer = 1000;
                  MapLocation curr = target_map_location;
                  while((curr.x != rc.getLocation().x || curr.y != rc.getLocation().y) && infLoopPreventer > 0) {
                    planned_route.add(curr);
                    curr = prev.get(curr.toString());
                    if(curr == null) {
                      System.out.println("curr is null");
                      break;
                    }
                    infLoopPreventer--;
                  }
                  Collections.reverse(planned_route);
                }
                System.out.println("Planned a route.  Bytecodes left:" + String.valueOf(Clock.getBytecodesLeft()) + " Target location:" + target_map_location.toString());

                tryPlannedRouteMove();
            }
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }
    }

    static void runHQ() throws GameActionException {
        if(num_miners_built < NUM_MINERS_TO_BUILD) {
            for (Direction dir : directions)
                if(tryBuild(RobotType.MINER, dir)) {
                    num_miners_built++;
                }
        }
    }

    static void runMiner() throws GameActionException {
        tryBlockchain();
        for (Direction dir : directions)
            if (tryRefine(dir))
                System.out.println("I refined soup! " + rc.getTeamSoup());
        for (Direction dir : directions)
            if (tryMine(dir))
                System.out.println("I mined soup! " + rc.getSoupCarrying());
        for (Direction dir : directions)
            tryBuild(randomSpawnedByMiner(), randomDirection());
        explore();
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {

    }

    static void runFulfillmentCenter() throws GameActionException {
        for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
    }

    static void runLandscaper() throws GameActionException {

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
