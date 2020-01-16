package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;



public strictfp class FulfillmentCenter extends Building {
    FulfillmentCenter(RobotController rc) {
        super(rc);
        this.rc = rc;
    }
    public void runTurn() throws GameActionException {
        if(num_drones_built < 3) {
            for (Direction dir : directions) {
                boolean did_build = tryBuild(RobotType.DELIVERY_DRONE, dir);
                if(did_build)
                    num_drones_built++;
            }
        }
    }
}