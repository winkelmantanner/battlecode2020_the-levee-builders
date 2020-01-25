package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;



public class DesignSchool extends Building {
    DesignSchool(RobotController rc) {
        super(rc);
        this.rc = rc;
    }


    public void runTurn() throws GameActionException {
        updateLocOfHQ();
        Direction dir_to_build = null;
        int dir_to_build_dist_from_hq = 12345;
        
        int elev_of_highest_buildable_adj_loc = -12345;
        for(Direction dir : directions) {
            MapLocation l = rc.adjacentLocation(dir);
            if(rc.canSenseLocation(l)
                && rc.canSenseLocation(rc.getLocation())
                && rc.senseElevation(l) > elev_of_highest_buildable_adj_loc
                && rc.senseElevation(l) >= rc.senseElevation(rc.getLocation()) - MAX_ELEVATION_STEP
                && rc.senseElevation(l) <= rc.senseElevation(rc.getLocation()) + MAX_ELEVATION_STEP
            ) {
                elev_of_highest_buildable_adj_loc = rc.senseElevation(l);
            }
        }
        boolean should_build_a_lot_of_landscapers = true;
            // has_seen_distress_signal
            // || (rc.canSenseLocation(rc.getLocation())
            //     && (
            //         GameConstants.getWaterLevel(rc.getRoundNum() + 100) >= rc.senseElevation(rc.getLocation())
            //         || GameConstants.getWaterLevel(rc.getRoundNum() + 100) >= elev_of_highest_buildable_adj_loc
            //     )
            // );

        for (Direction dir : directions) {
            if(locOfHQ != null) {
                if(rc.canBuildRobot(RobotType.LANDSCAPER, dir)
                    && rc.getTeamSoup() > RobotType.LANDSCAPER.cost * (1.2 + (((double)num_landscapers_built)
                        / (should_build_a_lot_of_landscapers
                                ? 3
                                : 0.5
                            )
                        )
                    )
                    && num_landscapers_built < 10
                ) {
                    MapLocation ml = rc.getLocation().add(dir);
                    if(max_difference(ml, locOfHQ) < dir_to_build_dist_from_hq) {
                        dir_to_build = dir;
                        dir_to_build_dist_from_hq = max_difference(
                            rc.getLocation().add(dir_to_build),
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
                        dir_to_build_dist_from_hq = max_difference(
                            rc.getLocation().add(dir_to_build),
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
}