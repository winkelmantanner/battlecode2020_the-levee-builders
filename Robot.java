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

    final int PIT_DEPTH = 10;
    int pit_max_elevation = -PIT_DEPTH; // to be modified when the elevation of the hq is read from the blockchain


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

    RobotInfo [] nearbyOpponentUnits = null;
    int roundNearbyOpponentUnitsWereRecorded = -1;
    public RobotInfo [] getNearbyOpponentUnits() throws GameActionException {
        if(rc.getRoundNum() != roundNearbyOpponentUnitsWereRecorded) {
            nearbyOpponentUnits = rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam().opponent()
            );
            roundNearbyOpponentUnitsWereRecorded = rc.getRoundNum();
        }
        return nearbyOpponentUnits;
    }

    public void takeTurn() throws GameActionException {
        beginTurn();
        runTurn();
        endTurn();
    }
    abstract public void runTurn() throws GameActionException;



    Direction[] directions = { Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST };
    Direction[] directions_including_center = { Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST,
            Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST };
    RobotType[] spawnedByMiner = { RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN };

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
                    if(rc.canSenseLocation(locOfHQ)) {
                        pit_max_elevation = rc.senseElevation(locOfHQ) - PIT_DEPTH;
                    }
                }
            }
            if(locOfHQ == null) {
                readHqLoc();
            }
        }
        if(opp_hq_loc == null) {
            for(RobotInfo rbt : getNearbyOpponentUnits()) {
                if(rbt.getType() == RobotType.HQ) {
                    opp_hq_loc = rbt.location;
                }
            }
        }
        if(locOfRefinery == null) {
            for(int [] myMessage : getMyMessages(rc.getRoundNum() - 1)) {
                if(myMessage[0] == MessageType.LOC_OF_REFINERY.getValue()) {
                    locOfRefinery = new MapLocation(myMessage[1], myMessage[2]);
                }
            }
        }
        for(RobotInfo rbt : rc.senseNearbyRobots()) {
            if(rbt.getType() == RobotType.REFINERY
                && rbt.team == rc.getTeam()
            ) {
                locOfRefinery = rbt.location;
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
    Direction randomDirectionIncludingCenter() {
        return directions_including_center[(int) (Math.random() * directions_including_center.length)];
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
        if (type != null
            && dir != null
            && rc.isReady()
            && rc.canBuildRobot(type, dir)
        ) {
            rc.buildRobot(type, dir);
            return true;
        } else
            return false;
    }

    final int NUM_PAYLOAD_INTS = GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1;
    final int[] NUMS_TO_ADD = { 791, 913, 123, 619, 403, 797 };
    int [] nums_to_add = null;
    int [] getNumsToAdd() {
        if(nums_to_add == null) {
            int map_w = rc.getMapWidth();
            int map_h = rc.getMapHeight();
            nums_to_add = new int[NUM_PAYLOAD_INTS];
            nums_to_add[0] = (rc.getTeam().ordinal() * 411) + 59 + (13 * map_w);
            nums_to_add[1] = ((map_h - 5) * (map_w + 19)) % 673;
            nums_to_add[2] = (map_w - (3 * rc.getTeam().ordinal()) - 4) * 9;
            nums_to_add[3] = ((map_w - map_h + 55) * map_h) + 83;
            nums_to_add[4] = 461 - (rc.getTeam().ordinal() * 91);
            nums_to_add[5] = ((map_w + 21) * 8) - (map_h * 2);
        }
        return nums_to_add;
    }

    int getSeventhInt(final int[] six_ints) {
        int p = 1;
        for (int k = 0; k < NUM_PAYLOAD_INTS; k++) {
            p *= (getNumsToAdd()[k] + six_ints[k]);
        }
        return p % 29971;
    }

    enum MessageType {
        LOC_OF_REFINERY(1),
        LOC_OF_HQ(2),
        DRONE_SPAWN(3);

        private final int value;

        MessageType(int value) {
            this.value = value;
        }
        public int getValue() {
            return this.value;
        }
    };

    boolean tryPostMessage(final int[] six_ints, int amount_of_soup) throws GameActionException {
        boolean message_was_posted = false;
        if (rc.getTeamSoup() >= amount_of_soup) {
            int seventh_int = getSeventhInt(six_ints);
            int[] message = new int[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
            for (int k = 0; k < NUM_PAYLOAD_INTS; k++) {
                message[k] = six_ints[k];
            }
            message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] = seventh_int;
            if (rc.canSubmitTransaction(message, amount_of_soup)) {
                rc.submitTransaction(message, amount_of_soup);
                message_was_posted = true;
            }
        }
        return message_was_posted;
    }

    boolean tryPostMyLocAsHqLoc() throws GameActionException {
        // to be called by HQ only!
        int [] six_ints = {
            MessageType.LOC_OF_HQ.getValue(),
            rc.getMapWidth() - rc.getLocation().x - 1,
            rc.getLocation().y,
            rc.getLocation().x, // [3]
            rc.getLocation().y, // [4]
            (rc.canSenseLocation(rc.getLocation())
                ? rc.senseElevation(rc.getLocation())
                : 0
            ) // [5]
        };
        return tryPostMessage(six_ints, 5);
    }
    int last_round_num_searched_for_hq = 0; // note that round nums must be positive
    void readHqLoc() throws GameActionException {
        while(locOfHQ == null
            && Clock.getBytecodesLeft() > 2000
            && last_round_num_searched_for_hq < rc.getRoundNum()
        ) {
            last_round_num_searched_for_hq++;
            for(int [] my_message : getMyMessages(last_round_num_searched_for_hq)) {
                if(my_message[0] == MessageType.LOC_OF_HQ.getValue()) {
                    locOfHQ = new MapLocation(
                        my_message[3],
                        my_message[4]
                    );
                    pit_max_elevation = my_message[5] - PIT_DEPTH;
                }
            }
        }
    }

    int[][] getMyMessages(final int roundNumber) throws GameActionException {
        // note that the opponent still might copy our messages exactly
        // we can only tell if a message was originally created by us
        ArrayList<int[]> myMessages = new ArrayList<int[]>();
        for (Transaction t : rc.getBlock(roundNumber)) {
            int[] message = t.getMessage();
            if (message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] == getSeventhInt(message)) {
                myMessages.add(message);
            }
        }
        int [][] myMessagesArray = new int[myMessages.size()][GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
        for(int k = 0; k < myMessages.size(); k++) {
            myMessagesArray[k] = myMessages.get(k);
        }
        return myMessagesArray;
    }

    int firstRoundNum = -1;
    final int MAX_NUM_MESSAGES_TO_STORE = 500;
    int [][] recentMessages = new int[MAX_NUM_MESSAGES_TO_STORE][GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
    int recentMessagesLength = 0;
    boolean tryToConfuseOpponentWithBlockchain() throws GameActionException {
        boolean submitted = false;
        if (firstRoundNum < 0) {
            firstRoundNum = rc.getRoundNum();
        } else {
            if (rc.getRoundNum() > firstRoundNum) {
                Transaction[] t = rc.getBlock(rc.getRoundNum() - 1);
                for (int k = 0; k < t.length; k++) {
                    if (t[k] != null
                        && Math.random() < (1 - ((double) recentMessagesLength / MAX_NUM_MESSAGES_TO_STORE))
                    ) {
                        recentMessages[recentMessagesLength] = t[k].getMessage();
                        recentMessagesLength++;
                    }
                }
            }
            if (Math.random() < 0.05 && recentMessagesLength > 0) {
                int[] message = recentMessages[(int) (Math.random() * recentMessagesLength)];
                int[] message_copy = new int[message.length];
                for (int k = 0; k < message.length; k++) {
                    if (Math.random() < 0.75) {
                        message_copy[k] = message[k];
                    } else {
                        if (Math.random() < 0.5) {
                            message_copy[k] = (int) (Math.random() * rc.getMapWidth());
                        } else {
                            message_copy[k] = message[(int) (Math.random() * message.length)];
                        }
                    }
                }
                int cost = 1 + ((int) (Math.random() * 3));
                if (rc.isReady() && rc.canSubmitTransaction(message_copy, cost)) {
                    rc.submitTransaction(message_copy, cost);
                }
                submitted = true;
            }
        }
        return submitted;
    }



    boolean isValidDigLoc(MapLocation l, MapLocation loc_of_hq) {
        int dx = l.x - loc_of_hq.x;
        int dy = l.y - loc_of_hq.y;
        return loc_of_hq == null 
            || (rc.onTheMap(l)
                && (
                    (abs(dx) > 2 || abs(dy) > 2)
                    ? (
                        ((dx & 1) == 0)
                        && ((dy & 1) == 0)
                    )
                    : (
                        (dx == 2 && dy == 1)
                        || (dx == -1 && dy == 2)
                        || (dx == -2 && dy == -1)
                        || (dx == 1 && dy == -2)
                    )
                )
            );
    }
    boolean isValidBuildLoc(MapLocation l, MapLocation loc_of_hq) {
        int dx = l.x - loc_of_hq.x;
        int dy = l.y - loc_of_hq.y;
        return loc_of_hq == null 
            || (rc.onTheMap(l)
                && ((dx & 1) == 1)
                && ((dy & 1) == 1)
                && (abs(dx) != 1 || abs(dy) != 1)
            );
    }




}