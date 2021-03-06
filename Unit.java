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


    boolean can_move_diagonally = true;


    Direction current_dir = null;

    RotationDirection bug_rot_dir = RotationDirection.NULL; // NULL iff bug_dir == null
    Direction bug_dir = null;
    int bug_dist = -1; // -1 iff bug_dir == null
    MapLocation bug_loc = null; // used to tell if we moved since bugPathingStep was last called

    class GoSomewhereOptions {
        public int max_dist_from_hq = 1234;
        void setMaxDistFromHq(final int max_dist) {
            this.max_dist_from_hq = max_dist;
        }
    }
    boolean tryGoSomewhere() throws GameActionException {
        return tryGoSomewhere(new GoSomewhereOptions());
    }
    boolean isCloseEnoughToHq(final Direction dir, final int max_dist) {
        if(locOfHQ == null) {
            return true;
        }
        int resulting_dist = max_difference(locOfHQ, rc.adjacentLocation(dir));
        return resulting_dist <= max_dist
            || resulting_dist <= max_difference(rc.getLocation(), locOfHQ);
    }
    boolean tryGoSomewhere(final GoSomewhereOptions options) throws GameActionException {
        boolean did_move = false;
        if(rc.isReady()) {
            if(Math.random() < 0.2) {
                current_dir = null;
            }
            if(current_dir != null) {
                if(isCloseEnoughToHq(current_dir, options.max_dist_from_hq)
                    && safeTryMove(current_dir)
                ) {
                    did_move = true;
                } else {
                    current_dir = null;
                }
            }
            if(current_dir == null) {
                int infLoopPreventer = 10;
                do {
                    current_dir = randomDirection(!can_move_diagonally);
                    if(
                        isCloseEnoughToHq(current_dir, options.max_dist_from_hq)
                        && safeTryMove(current_dir)
                    ) {
                        did_move = true;
                    } else {
                        current_dir = null;
                    }
                    infLoopPreventer--;
                } while(current_dir == null && infLoopPreventer > 0);
            }
        }
        return did_move;
    }



    enum HybridStatus {
        WALL_BFS,
        FUZZY,
        BUG
    }
    HybridStatus hybrid_status = HybridStatus.WALL_BFS;
    MapLocation prev_hybrid_dest = null;
    MapLocation prev_hybrid_loc = null;
    boolean hybridStep(final MapLocation dest) throws GameActionException {
        return hybridStep(dest, false);
    }  
    boolean hybridStep(final MapLocation dest, final boolean can_move_to_below_water_level) throws GameActionException {
        // includes randomness
        if(prev_hybrid_dest != dest
            || prev_hybrid_loc != rc.getLocation()
        ) {
            hybrid_status = HybridStatus.WALL_BFS;
            // System.out.println("resetting to BFS dest:" + dest.toString());
            prev_hybrid_dest = dest;
        }
        boolean has_moved = false;
        boolean is_stuck = true;
        for(Direction dir : directions) {
            if(canSafeMove(dir)) {
                is_stuck = false;
                break;
            }
        }
        if(rc.isReady()
            && !is_stuck
        ) {
            if(hybrid_status == HybridStatus.WALL_BFS) {
                has_moved = wall_BFS_step(dest, can_move_to_below_water_level);
                if(!has_moved) {
                    // System.out.println("falling back to fuzzy from BFS dest:" + dest.toString());
                    hybrid_status = HybridStatus.FUZZY;
                }
            }
            if(hybrid_status == HybridStatus.FUZZY) {
                has_moved = fuzzy_step(dest, can_move_to_below_water_level);
                if(!has_moved) {
                    // System.out.println("falling back to bug from fuzzy dest:" + dest.toString());
                    hybrid_status = HybridStatus.BUG;
                } else if(Math.random() < 0.25) {
                    // System.out.println("advancing to bfs from fuzzy dest:" + dest.toString());
                    hybrid_status = HybridStatus.WALL_BFS;
                }
            }
            if(hybrid_status == HybridStatus.BUG) {
                has_moved = bugPathingStep(dest, can_move_to_below_water_level);
                if(Math.random() < 0.05) {
                    // System.out.println("advancing to fuzzy from bug dest:" + dest.toString());
                    hybrid_status = HybridStatus.FUZZY;
                }
            }
            // if(rc.isReady()) {
            //     System.out.println("DIDN'T MOVE");
            // }
        }
        prev_hybrid_loc = rc.getLocation();
        return has_moved;
    }



    HashMap<String, Integer> fuzzy_where_ive_been = new HashMap<String, Integer>();
    void fuzzy_clear() throws GameActionException {
        fuzzy_where_ive_been.clear();
    }
    boolean fuzzy_step(final MapLocation dest) throws GameActionException {
        return fuzzy_step(dest, false);
    }
    // int fuzzy_mbc = 0;
    boolean fuzzy_step(final MapLocation dest, final boolean can_move_to_below_water_level) throws GameActionException {
        // this function has been observed to take up to 4383 bytecodes
        // but may have been changed since then
        int bcbefore = Clock.getBytecodeNum();
        fuzzy_where_ive_been.put(rc.getLocation().toString(), rc.getRoundNum());
        final Direction target_dir = rc.getLocation().directionTo(dest);
        Direction dir = target_dir;
        final Direction opposite_dir = target_dir.opposite();
        boolean has_moved = false;
        int num_to_rotate = 0;
        boolean move_left = true;
        boolean stop = false;
        while(!stop && !has_moved && dir != opposite_dir) {
            if(move_left) {
                for(int k = 0; k < num_to_rotate; k++) {
                    dir = dir.rotateLeft();
                }
            } else {
                for(int k = 0; k < num_to_rotate; k++) {
                    dir = dir.rotateRight();
                }
            }

            if(canSafeMove(dir)) {
                String key = rc.adjacentLocation(dir).toString();
                
                if(
                    !fuzzy_where_ive_been.containsKey(key)
                    || fuzzy_where_ive_been.get(key) + 10 < rc.getRoundNum()
                ) {
                    has_moved = safeTryMove(dir, can_move_to_below_water_level);
                } else {
                    // System.out.println("fuzzy explorative block " + dir.toString());
                    // Don't get stuck in large cup shapes
                    stop = true;
                }
            }
            move_left = !move_left;
            num_to_rotate++;
        }
        // int bcnow = Clock.getBytecodeNum();
        // if(bcnow - bcbefore > fuzzy_mbc) {
        //     fuzzy_mbc = bcnow - bcbefore;
        //     System.out.println("fuzzy_mbc:" + String.valueOf(fuzzy_mbc));
        // }
        return has_moved;
    }

    // @SuppressWarnings("unused")
    // abstract public void runTurn() throws GameActionException;


    boolean goToHQ() throws GameActionException {
        // this function has been observed to take up to 6231 bytecodes
        if(Math.random() < 0.1 || locOfHQ == null) {
            tryGoSomewhere();
            return true;
        } else {
            return hybridStep(locOfHQ, true);
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

    

    enum X{WATER, SOUP, WATER_OR_PIT;};
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
                            case WATER_OR_PIT:
                                if(is_valid_enemy_drop_loc(ml)
                                    && rc.canMove(dir)
                                ) {
                                    current_dir = dir;
                                    found = true;
                                    stop = true;
                                }
                                // break intentionally omitted
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

    class EnemyShooterData {
        public RobotInfo info;
        public int round_num;
        EnemyShooterData(RobotInfo rbt, final int round_num) {
            this.info = rbt;
            this.round_num = round_num;
        }
        float getCooldownTurnsForRound(final int roundNum) {
            return max(0, this.info.cooldownTurns - ((float)(roundNum - this.round_num)));
        }
    }
    ArrayList<EnemyShooterData> enemy_shooter_array = new ArrayList<EnemyShooterData>();
    // MapLocation where_i_spotted_enemy_shooter = null; // must be updated by the drone
    int round_num_i_spotted_enemy_shooter = -12345;
    int round_num_searched_for_enemy_shooter = -12345;
    final int NUM_ROUNDS_TO_REMEMBER_SHOOTERS = 50;
    void updateWhereISpottedEnemyShooter() throws GameActionException {
        // no matter how many times this function is called,
        // senseNearbyRobots() is only called once per round
        if(rc.getRoundNum() != round_num_searched_for_enemy_shooter) {
            round_num_searched_for_enemy_shooter = rc.getRoundNum();
            for(RobotInfo enemy_rbt : rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam().opponent()
            )) {

                if(enemy_rbt.type.canShoot()) {
                    boolean was_already_recorded = false;
                    for(EnemyShooterData data : enemy_shooter_array) {
                        if(enemy_rbt.ID == data.info.ID) {
                            data.round_num = rc.getRoundNum();
                            data.info = enemy_rbt;
                            was_already_recorded = true;
                            break;
                        }
                    }
                    if(!was_already_recorded) {
                        enemy_shooter_array.add(new EnemyShooterData(enemy_rbt, rc.getRoundNum()));
                    }
                }

                if(enemy_rbt.type.canShoot()
                    && enemy_rbt.getCooldownTurns() < 2
                ) {
                    // where_i_spotted_enemy_shooter = enemy_rbt.location;
                    round_num_i_spotted_enemy_shooter = rc.getRoundNum();
                    break;
                }
            }
            // if(rc.getRoundNum() - round_num_i_spotted_enemy_shooter > 50) {
            //     // Handle the case of the shooter having been destroyed
            //     where_i_spotted_enemy_shooter = null;
            // }

            // remove old entries from the array, but only at most 1 per round
            int spot_to_remove = -1;
            for(int k = 0; k < enemy_shooter_array.size(); k++) {
                if(rc.getRoundNum() - enemy_shooter_array.get(k).round_num > NUM_ROUNDS_TO_REMEMBER_SHOOTERS) {
                    spot_to_remove = k;
                    break;
                }
            }
            if(spot_to_remove >= 0) {
                // System.out.println("REMOVING " + enemy_shooter_array.get(spot_to_remove).info.toString());
                enemy_shooter_array.remove(spot_to_remove);
            }
        }
    }
    boolean isSafeFromEnemyShooters(Direction d) throws GameActionException {
        updateWhereISpottedEnemyShooter();
        MapLocation l = rc.adjacentLocation(d);
        boolean is_safe = true;
        for(EnemyShooterData data : enemy_shooter_array) {
            if(l.isWithinDistanceSquared(
                data.info.location,
                GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED
            )) {
                if(l.distanceSquaredTo(data.info.location)
                    <= rc.getLocation().distanceSquaredTo(data.info.location)
                ) {
                    is_safe = false;
                    break;
                }
            }
        }

        // if(!is_safe) {
        //     for(EnemyShooterData data : enemy_shooter_array) {
        //         System.out.println(String.valueOf(data.info.ID) + " " + data.info.location.toString() + " " + String.valueOf(data.round_num));
        //     }
        // }
        return is_safe;
    }

    class CanSafeMove {
        public int roundNum;
        public boolean is_safe;
        CanSafeMove(final int roundNum, final boolean is_safe) {
            this.roundNum = roundNum;
            this.is_safe = is_safe;
        }
        void set(final int roundNum, final boolean is_safe) {
            this.roundNum = roundNum;
            this.is_safe = is_safe;
        }
    }
    CanSafeMove north_csm = new CanSafeMove(-1, false);
    CanSafeMove northeast_csm = new CanSafeMove(-1, false);
    CanSafeMove east_csm = new CanSafeMove(-1, false);
    CanSafeMove southeast_csm = new CanSafeMove(-1, false);
    CanSafeMove south_csm = new CanSafeMove(-1, false);
    CanSafeMove southwest_csm = new CanSafeMove(-1, false);
    CanSafeMove west_csm = new CanSafeMove(-1, false);
    CanSafeMove northwest_csm = new CanSafeMove(-1, false);
    CanSafeMove center_csm = new CanSafeMove(-1, false);
    CanSafeMove getMemoizedCanSafeMove(Direction d) throws GameActionException {
        switch (d) {
            case NORTH:
                return north_csm;
            case NORTHEAST:
                return northeast_csm;
            case EAST:
                return east_csm;
            case SOUTHEAST:
                return southeast_csm;
            case SOUTH:
                return south_csm;
            case SOUTHWEST:
                return southwest_csm;
            case WEST:
                return west_csm;
            case NORTHWEST:
                return northwest_csm;
            case CENTER:
                return center_csm;
            default:
                return null;
        }
    }
    boolean canSafeMove(Direction dir) throws GameActionException {
        return canSafeMove(dir, false);
    }
    boolean canSafeMove(Direction dir, final boolean can_move_to_below_water_level) throws GameActionException {
        // This func works for all unit types
        // Do not call if you are a building
        // VERY HIGH COMPLEXITY
        if(dir == null || !rc.isReady()) {
            return false;
        }
        CanSafeMove csm = getMemoizedCanSafeMove(dir);
        if(csm.roundNum == rc.getRoundNum()) {
            return csm.is_safe;
        }
        boolean is_safe = true;
        MapLocation loc = rc.getLocation().add(dir);
        if(!rc.onTheMap(loc)) {
            is_safe = false;
        }
        if(!can_move_diagonally
            && !isCardinal(dir)
        ) {
            is_safe = false;
        }
        if(is_safe) {
            boolean is_safe_from_enemy_robots = true;
            switch(rc.getType()) {
                case DELIVERY_DRONE:
                    is_safe = rc.canMove(dir)
                        && isSafeFromEnemyShooters(dir);
                    break;
                default:
                    float water_level_30_ago = (can_move_to_below_water_level
                        ? -1234
                        : GameConstants.getWaterLevel(rc.getRoundNum() - 30)
                    );
                    float water_level_in_20 = (can_move_to_below_water_level
                        ? -1234
                        : GameConstants.getWaterLevel(rc.getRoundNum() + 20)
                    );
                    for(RobotInfo rbt : getNearbyOpponentUnits()) {
                        if(rbt.type == RobotType.DELIVERY_DRONE
                            && rbt.cooldownTurns < 4
                            && max_difference(loc, rbt.location) <= 3
                            && max_difference(rc.getLocation(), rbt.location) > max_difference(loc, rbt.location)
                        ) {
                            is_safe_from_enemy_robots = false;
                        }
                    }
                    is_safe = is_safe_from_enemy_robots
                        && rc.canMove(dir)
                        && (!rc.canSenseLocation(loc) || !rc.senseFlooding(loc));
                    if(is_safe) {
                        boolean flood_danger = rc.canSenseLocation(loc)
                            && rc.canSenseLocation(rc.getLocation())
                            && water_level_in_20 > rc.senseElevation(loc)
                            && water_level_in_20 < rc.senseElevation(rc.getLocation())
                            && !(water_level_30_ago > rc.senseElevation(loc));
                        is_safe = !flood_danger;
                    }
                    break;
            }
        }
        csm.set(rc.getRoundNum(), is_safe);
        return is_safe;
    }

    boolean safeTryMove(Direction dir) throws GameActionException {
        return safeTryMove(dir, false);
    }
    boolean safeTryMove(Direction dir, final boolean can_move_to_below_water_level) throws GameActionException {
        // This func works for all unit types
        // Do not call if you are a building
        // VERY HIGH COMPLEXITY
        if(canSafeMove(dir, can_move_to_below_water_level)) {
            rc.move(dir);
            return true;
        }
        return false;
    }







    class PrevEntry {
        public MapLocation prev_loc;
        public int roundNumRecorded;
        PrevEntry(MapLocation prev_loc, int roundNum) {
            this.set(prev_loc, roundNum);
        }
        void set(MapLocation prev_loc, int roundNum) {
            this.prev_loc = prev_loc;
            this.roundNumRecorded = roundNum;
        }
    }
    int queue_begin = 0;
    int queue_end = 0;
    MapLocation [] queue = new MapLocation[4 * ceilOfSensorRadius * ceilOfSensorRadius];
    void queuePush(MapLocation ml) {
        queue[queue_end] = ml;
        queue_end++;
    }
    MapLocation queuePop() {
        queue_begin++;
        return queue[queue_begin - 1];
    }
    boolean isQueueEmpty() {
        return queue_begin == queue_end;
    }
    void clearQueue() {
        queue_begin = 0;
        queue_end = 0;
    }
    PrevEntry [] [] prev = new PrevEntry[2 * ceilOfSensorRadius][2 * ceilOfSensorRadius];
    PrevEntry prevGet(final int x, final int y, final int our_x, final int our_y) {
        // this function costs 23 bytecodes
        return prev[x - our_x + ceilOfSensorRadius][y - our_y + ceilOfSensorRadius];
    }
    void prevSet(final int x, final int y, final int our_x, final int our_y, MapLocation prev_loc) {
        prev[x - our_x + ceilOfSensorRadius][y - our_y + ceilOfSensorRadius].set(prev_loc, rc.getRoundNum());
    }
    boolean prev_has_been_initialized = false;
    void wall_BFS_initialize_prev() {
        if(!prev_has_been_initialized) {
            for(int k = 0; k < 2 * ceilOfSensorRadius; k++) {
                for(int j = 0; j < 2 * ceilOfSensorRadius; j++) {
                    prev[k][j] = new PrevEntry(null, -1);
                }
            }
            prev_has_been_initialized = true;
        }
    }
    boolean wall_BFS_is_following_wall = false;
    enum BFS_mode {
        GET_TO_DEST, // non-drone unit tries to get to the destination.  The case of wall_BFS_step returning false must be handled.
        COUNT_SOUP; // miner should use this to count the amount of soup in a soup deposit.  dest must be a MapLocation with soup
    }
    class SoupCount {
        public int soup_count;
        SoupCount(final int soup_count) {
            this.soup_count = soup_count;
        }
    }
    int count_soup_with_BFS(MapLocation soup_loc) throws GameActionException {
        SoupCount count_holder = new SoupCount(0);
        run_BFS(null, soup_loc, BFS_mode.COUNT_SOUP, count_holder);
        return count_holder.soup_count;
    }
    boolean wall_BFS_step(MapLocation dest) throws GameActionException {
        return wall_BFS_step(dest, false);
    }
    boolean wall_BFS_step(MapLocation dest, final boolean can_move_to_below_water_level) throws GameActionException {
        if(!rc.isReady() || !rc.canSenseLocation(dest)) {
            return false;
        }
        // System.out.println("I CAN OUTPUT STUFF");
        if(!wall_BFS_is_following_wall) {
            wall_BFS_is_following_wall = !safeTryMove(rc.getLocation().directionTo(dest), can_move_to_below_water_level);
        }
        if(wall_BFS_is_following_wall) {
            return pure_wall_BFS_step(dest, can_move_to_below_water_level);
        }
        return false;
    }
    boolean pure_wall_BFS_step(MapLocation dest, final boolean can_move_to_below_water_level) throws GameActionException {
        if(!rc.canSenseLocation(dest)) {
            return false;
        } 
        
        run_BFS(dest, rc.getLocation(), BFS_mode.GET_TO_DEST);
            
        if(rc.getRoundNum() == prevGet(dest.x, dest.y, rc.getLocation().x, rc.getLocation().y).roundNumRecorded) {
            MapLocation l = dest;
            MapLocation prev_l = dest;
            int infLoopPreventer = 100;
            while(l != rc.getLocation() && infLoopPreventer > 0) {
                // System.out.println("l:" + l.toString() + " round:" + prevGet(l.x, l.y, our_x, our_y).roundNumRecorded);
                prev_l = l;
                l = prevGet(l.x, l.y, rc.getLocation().x, rc.getLocation().y).prev_loc;
                infLoopPreventer--;
            }
            if(l == rc.getLocation()) {
                Direction the_final_answer = rc.getLocation().directionTo(prev_l);
                // System.out.println("THE FINAL ANSWER: " + the_final_answer.toString());
                if(canSafeMove(the_final_answer, can_move_to_below_water_level)) {
                    rc.move(the_final_answer);
                    // System.out.println("RETURNING TRUE");

                    return true;
                }
            }
        }
        return false;
    }
    void run_BFS(MapLocation dest, MapLocation start, final BFS_mode mode) throws GameActionException {
        run_BFS(dest, start, mode, null);
    }
    void run_BFS(MapLocation dest, MapLocation start, final BFS_mode mode, final SoupCount count_holder) throws GameActionException {
        wall_BFS_initialize_prev();
        clearQueue();
        int our_x = rc.getLocation().x;
        int our_y = rc.getLocation().y;
        queuePush(start);
        if(BFS_mode.COUNT_SOUP == mode && rc.canSenseLocation(start)) {
            count_holder.soup_count += rc.senseSoup(start);
        }
        prevSet(start.x, start.y, our_x, our_y, rc.getLocation());
        boolean stop = false;
        // System.out.println("I CAN OUTPUT STUFF");
        MapLocation [] entries_to_process = new MapLocation[directions.length];
        while(!stop && !isQueueEmpty()) {
            MapLocation current_loc = queuePop();
            // System.out.println("current_loc: " + current_loc.toString());
            // System.out.println("bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
            int current_loc_elevation = rc.senseElevation(current_loc);
            boolean is_adjacent_to_wall = false;
            int entries_to_process_length = 0;
            PrevEntry pe = prevGet(current_loc.x, current_loc.y, our_x, our_y);
            for(Direction dir : directions) {
                MapLocation neighbor = current_loc.add(dir);
                if(neighbor.equals(dest)) {
                    prevSet(dest.x, dest.y, our_x, our_y, current_loc);
                    stop = true;
                    // System.out.println("FOUND IT");
                }
                // System.out.println("d bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
                if(!stop && !neighbor.equals(pe.prev_loc)) {
                    if(!rc.canSenseLocation(neighbor)
                        || rc.senseFlooding(neighbor)
                        || MAX_ELEVATION_STEP < abs(rc.senseElevation(neighbor) - current_loc_elevation)
                        || null != rc.senseRobotAtLocation(neighbor)
                        || (BFS_mode.COUNT_SOUP == mode && 0 == rc.senseSoup(neighbor))
                    ){
                        // System.out.println("iaw=true bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
                        is_adjacent_to_wall = true;
                        if(mode == BFS_mode.COUNT_SOUP
                            && rc.canSenseLocation(neighbor)
                            && rc.getRoundNum() != prevGet(neighbor.x, neighbor.y, our_x, our_y).roundNumRecorded
                        ) {
                            count_holder.soup_count += rc.senseSoup(neighbor);
                            prevSet(neighbor.x, neighbor.y, our_x, our_y, current_loc);
                        }
                    } else if(
                        BFS_mode.COUNT_SOUP == mode
                        // a PRUNING CONDITION
                        || max_difference(neighbor, dest) <= max_difference(current_loc, dest)
                    ) {
                        // System.out.println("a bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
                        PrevEntry entry = prevGet(neighbor.x, neighbor.y, our_x, our_y);
                        // System.out.println("b bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
                        if(entry.roundNumRecorded != rc.getRoundNum()) {
                            entries_to_process[entries_to_process_length] = neighbor;
                            entries_to_process_length++;
                            if(BFS_mode.COUNT_SOUP == mode) {
                                count_holder.soup_count += rc.senseSoup(neighbor);
                            }
                        }
                    }
                }
            }

            if(!stop
                && (is_adjacent_to_wall || BFS_mode.COUNT_SOUP == mode)  // a PRUNING CONDITION
            ) {
                // System.out.println("prepush bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
                for(int etp_index = 0;
                    etp_index < entries_to_process_length;
                    etp_index++
                ) {
                    MapLocation entry = entries_to_process[etp_index];
                    queuePush(entry);
                    prevSet(entry.x, entry.y, our_x, our_y, current_loc);
                }
                // System.out.println("postpush bytecodes left: " + String.valueOf(Clock.getBytecodesLeft()));
            } else if(current_loc.equals(start)) {
                wall_BFS_is_following_wall = false;
            }

            if(Clock.getBytecodesLeft() < 4000) {
                stop = true;
            }
        } // end while

    }









    HashMap<String, ArrayList<Direction> > where_ive_been = new HashMap<String, ArrayList<Direction> >();
    boolean bugCanSafeMove(Direction dir, final boolean can_move_to_below_water_level) throws GameActionException {
        String k = rc.getLocation().toString();
        boolean can_safe_move = canSafeMove(dir, can_move_to_below_water_level);
        if(can_safe_move
            && (!where_ive_been.containsKey(k)
                || where_ive_been.get(k).indexOf(dir) == -1 // yes, Java has short-circuit evaluation
            )
        ) {
            return true;
        } else {
            // if(canSafeMove(dir, can_move_to_below_water_level)) {
            //     System.out.println("move prevented " + dir.toString());
            // }
            return false;
        }
    }
    boolean bugSafeTryMove(Direction dir, final boolean can_move_to_below_water_level) throws GameActionException {
        String k = rc.getLocation().toString();
        if(bugCanSafeMove(dir, can_move_to_below_water_level)
            && rc.canMove(dir) // makes it less likely to have exceptions
        ) {
            rc.move(dir);
            if(!where_ive_been.containsKey(k)) {
                where_ive_been.put(k, new ArrayList<Direction>());
            }
            where_ive_been.get(k).add(dir);
            return true;
        } else {
            return false;
        }
    }
    boolean bugTryMoveToward(MapLocation dest, final boolean can_move_to_below_water_level) throws GameActionException {
        Direction target_dir = rc.getLocation().directionTo(dest);
        if(bugSafeTryMove(target_dir, can_move_to_below_water_level)) {
            return true;
        } else {
            bug_rot_dir = Math.random() < 0.5 ? RotationDirection.RIGHT : RotationDirection.LEFT;
            bug_dir = target_dir;
            bug_dist = max_difference(rc.getLocation(), dest);
            return false;
        }
    }
    boolean bugPathingStep(MapLocation dest) throws GameActionException {
        return bugPathingStep(dest, false);
    }
    boolean bugPathingStep(MapLocation dest, final boolean can_move_to_below_water_level) throws GameActionException {
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
                did_move = bugTryMoveToward(dest, can_move_to_below_water_level);
            }
            if(bug_dir != null) {
                Direction local_dir = bug_dir;
                do {
                    local_dir = RotDirFuncs.getRotated(local_dir, bug_rot_dir);
                } while(bugCanSafeMove(local_dir, can_move_to_below_water_level) && !local_dir.equals(bug_dir));
                if(local_dir.equals(bug_dir)) {
                    if(rc.getLocation().directionTo(dest) == bug_dir.opposite()) {
                        for(int k = 0; k < 10; k++) {
                            if(bugSafeTryMove(randomDirection(), can_move_to_below_water_level)) {
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
                        did_move = bugTryMoveToward(dest, can_move_to_below_water_level);
                    }
                } else {
                    bug_dir = local_dir;
                    do {
                        bug_dir = RotDirFuncs.getRotated(bug_dir, RotDirFuncs.getOpposite(bug_rot_dir));
                    } while(!bugCanSafeMove(bug_dir, can_move_to_below_water_level) && !bug_dir.equals(local_dir));
                    if(!bug_dir.equals(local_dir)) {
                        MapLocation loc_before = rc.getLocation();
                        bugSafeTryMove(bug_dir, can_move_to_below_water_level);
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


    private final int MAX_NUM_NEARBY_ENEMY_DRONES = 3;
    private MapLocation [] nearby_enemy_drone_locs = new MapLocation[MAX_NUM_NEARBY_ENEMY_DRONES];
    boolean tryRunFromAnyNearbyDrones() throws GameActionException {
        int nearby_enemy_drones_length = 0;
        for(RobotInfo rbt : rc.senseNearbyRobots(
            rc.getType().sensorRadiusSquared,
            rc.getTeam().opponent()
        )) {
            if(rbt.type.equals(RobotType.DELIVERY_DRONE)
                && max_difference(rbt.location, rc.getLocation()) <= 3
            ) {
                nearby_enemy_drone_locs[nearby_enemy_drones_length] = rbt.location;
                nearby_enemy_drones_length++;
                // System.out.println("Enemy drone " + rbt.location.toString());
                if(nearby_enemy_drones_length >= MAX_NUM_NEARBY_ENEMY_DRONES) {
                    break;
                }
            }
        }
        Direction best_dir = null;
        if(nearby_enemy_drones_length > 0) {
            int best_dir_min_drone_dist = -1234;
            for(Direction dir : directions) {
                if(canSafeMove(dir)) {
                    MapLocation ml = rc.adjacentLocation(dir);
                    int min_drone_dist = 1234;
                    for(int k = 0; k < nearby_enemy_drones_length; k++) {
                        int dist = max_difference(nearby_enemy_drone_locs[k], ml);
                        if(dist < min_drone_dist) {
                            min_drone_dist = dist;
                        }
                    }
                    
                    // find the dir with the maximum min_drone_dist
                    if(min_drone_dist > best_dir_min_drone_dist) {
                        best_dir = dir;
                        best_dir_min_drone_dist = min_drone_dist;
                    }
                }
            }
        }
        boolean moved = false;
        if(best_dir != null) {
            System.out.println("Ran from enemy drone; moved " + best_dir.toString());
            rc.move(best_dir);
            moved = true;
        }
        return moved;
    }

    // int maxbc = 0;
    // int max_bc_taken = 0;
    RobotInfo friendly_shooter_info = null;
    int round_num_observed_friendly_shooter = -1;
    boolean runToNetGunIfDrones() throws GameActionException {
        // int bc = Clock.getBytecodeNum();
        boolean has_moved = false;
        // int bca = 0;
        // int bcb = 0;
        // int bcc = 0;
        if(rc.isReady()) {
            int min_dist_from_shooter = 1234;
            // bca = Clock.getBytecodeNum();
            for(RobotInfo rbt : rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam()
            )) {
                if(rbt.type.canShoot()
                    && rc.getLocation().distanceSquaredTo(rbt.location) < min_dist_from_shooter
                ) {
                    friendly_shooter_info = rbt;
                    min_dist_from_shooter = rc.getLocation().distanceSquaredTo(rbt.location);
                    round_num_observed_friendly_shooter = rc.getRoundNum();
                }
            }
            // bcb = Clock.getBytecodeNum();
            if(rc.getRoundNum() - round_num_observed_friendly_shooter > 30) {
                friendly_shooter_info = null;
            }
            boolean need_to_run_from_enemy_drones = false;
            for(RobotInfo rbt : rc.senseNearbyRobots(
                rc.getType().sensorRadiusSquared,
                rc.getTeam().opponent()
            )) {
                if(rbt.type.equals(RobotType.DELIVERY_DRONE)
                    && max_difference(rbt.location, rc.getLocation()) <= 3
                ) {
                    if(friendly_shooter_info != null
                        && rc.getLocation().distanceSquaredTo(friendly_shooter_info.location) >= 6
                        && max_difference(rbt.location, rc.getLocation()) >= 2
                    ) {
                        has_moved = hybridStep(friendly_shooter_info.location);
                    } else {
                        need_to_run_from_enemy_drones = true;
                    }
                    break;
                }
            }
            // bcc = Clock.getBytecodeNum();
            if(need_to_run_from_enemy_drones) {
                has_moved = (has_moved || tryRunFromAnyNearbyDrones());
            }
        }
        // int bc_taken = Clock.getBytecodeNum() - bc;
        // if(bc_taken > max_bc_taken) {
        //     max_bc_taken = bc_taken;
        //     System.out.println("max_bc_taken:" + String.valueOf(max_bc_taken)
        //         + " bca-bc:" + String.valueOf(bca - bc)
        //         + " bcb-bc:" + String.valueOf(bcb - bc)
        //         + " bcc-bc:" + String.valueOf(bcc - bc)
        //     );
        // }
        return has_moved;
    }
    // boolean tryGoToHqIfNearbyEnemyDrones() throws GameActionException {
    //     // int b = Clock.getBytecodeNum();
    //     for(RobotInfo rbt : rc.senseNearbyRobots(
    //         rc.getType().sensorRadiusSquared,
    //         rc.getTeam().opponent()
    //     )) {
    //         if(rbt.type.equals(RobotType.DELIVERY_DRONE)
    //             && max_difference(rbt.location, rc.getLocation()) <= 3
    //         ) {
    //             return goToHQ();
    //         }
    //     }
    //     // int t = Clock.getBytecodeNum() - b;
    //     // if(t > maxbc) {
    //     //     maxbc = t;
    //     //     System.out.println("maxbc:" + String.valueOf(maxbc));
    //     // }
    //     return false;
    // }



    boolean is_valid_enemy_drop_loc(MapLocation l) throws GameActionException {
        return rc.canSenseLocation(l)
            && (
                rc.senseFlooding(l)
                || (rc.senseElevation(l) <= pit_max_elevation
                    && locOfHQ != null
                    && max_difference(locOfHQ, l) > 1 + (rc.getRoundNum() > 300 ? 1 : 0)
                )
            );
    }
    boolean is_valid_cow_drop_loc(MapLocation l) throws GameActionException {
        return rc.canSenseLocation(l)
            && (
                rc.senseFlooding(l)
                || (locOfHQ != null
                    && l.distanceSquaredTo(locOfHQ) >= 225 // 225 == 15*15
                )
            );
    }




}