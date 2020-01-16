package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;


public strictfp class Hq extends Building {
    Hq(RobotController rc) {
        super(rc);
        this.rc = rc;
    }

    public void runTurn() throws GameActionException {
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
}