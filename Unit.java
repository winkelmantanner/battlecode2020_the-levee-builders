package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;

abstract public strictfp class Unit extends Robot {

    Unit(RobotController rc) {
      super(rc);
    }



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




    
    // @SuppressWarnings("unused")
    // abstract public void runTurn() throws GameActionException;


    boolean goToHQ() throws GameActionException {
        if(Math.random() < 0.1 || locOfHQ == null) {
            tryGoSomewhere();
            return true;
        } else {
            return bugPathingStep(locOfHQ);
        }
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
                    ) {
                        stop = true;
                    } else {
                        if(
                            (rc.senseFlooding(ml)
                                && rc.getType() != RobotType.DELIVERY_DRONE)
                            || (abs(rc.senseElevation(ml) - last_elevation) > MAX_ELEVATION_STEP
                                && rc.getType() != RobotType.DELIVERY_DRONE)
                            || null != rc.senseRobotAtLocation(ml)
                        ) {
                            stop = true;
                        }
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
                for(RobotInfo rbt : getNearbyOpponentUnits()) {
                    if(rbt.type == RobotType.DELIVERY_DRONE
                      && max_difference(loc, rbt.location) <= 2
                      && max_difference(rc.getLocation(), rbt.location) > 2
                    ) {
                        return false;
                    }
                }
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











}