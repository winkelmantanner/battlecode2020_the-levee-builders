package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;


public strictfp class Refinery extends Building {
    boolean has_posted_location = false;

    Refinery(RobotController rc) {
        super(rc);
        this.rc = rc;
    }

    public void runTurn() throws GameActionException {
        if (!has_posted_location) {
            int[] six_ints = { MessageType.LOC_OF_REFINERY.getValue(), rc.getLocation().x, rc.getLocation().y, 67,
                    985432, 3 };
            if (tryPostMessage(six_ints, 5)) {
                has_posted_location = true;
            }
        }
    }
}