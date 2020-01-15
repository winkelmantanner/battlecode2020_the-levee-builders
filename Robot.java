package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;


abstract public strictfp class Robot {
    RobotController rc;

    int turnCount = 0;
    int roundNumCreated = -1;

    int ceilOfSensorRadius = -1;



    MapLocation locOfHQ = null;

    final int MAX_ELEVATION_STEP = GameConstants.MAX_DIRT_DIFFERENCE; // I didn't see this in GameConstants until I'd already made this



    Robot(RobotController rbt_controller) {
        rc = rbt_controller;

        turnCount = 0;
        roundNumCreated = rc.getRoundNum();

        ceilOfSensorRadius = (int) ceil(sqrt(rc.getType().sensorRadiusSquared));
    }


    int roundNumBefore = -1;
    public void beginTurn() {
        turnCount += 1;
        roundNumBefore = rc.getRoundNum();
    }
    public void endTurn() {
        if(roundNumBefore != rc.getRoundNum()) {
            System.out.println("Took " + String.valueOf(rc.getRoundNum() - roundNumBefore) + " rounds");
        }
    }

    abstract public void runTurn();
    



    Direction[] directions = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    Direction[] directions_including_center = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    
    
    boolean isValid(MapLocation ml) {
        return ml.x >= 0 && ml.y >= 0 && ml.x < rc.getMapWidth() && ml.y < rc.getMapHeight();
    }

    int max_difference(MapLocation ml1, MapLocation ml2) {
        return max(abs(ml1.x - ml2.x), abs(ml1.y - ml2.y));
    }

    
    MapLocation locOfRefinery = null;


    void updateLocOfHQ() throws GameActionException {
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

    

    MapLocation opp_hq_loc = null;

    MapLocation [] getWhereOppHqMightBe() {
        // locOfHQ must not be null
        MapLocation [] possible_locs = {
            new MapLocation(rc.getMapWidth() - locOfHQ.x - 1, locOfHQ.y),
            new MapLocation(rc.getMapWidth() - locOfHQ.x - 1, rc.getMapHeight() - locOfHQ.y - 1),
            new MapLocation(locOfHQ.x, rc.getMapHeight() - locOfHQ.y - 1)
        };
        return possible_locs;
    }


    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    


    int firstRoundNum = -1;
    final int MAX_NUM_MESSAGES_TO_STORE = 500;
    int [][] recentMessages = new int[MAX_NUM_MESSAGES_TO_STORE][GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH];
    int recentMessagesLength = 0;
    boolean tryToConfuseOpponentWithBlockchain() throws GameActionException {
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
                    if(Math.random() < 0.75) {
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




}