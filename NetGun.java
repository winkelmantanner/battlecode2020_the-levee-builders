package tannerplayer;
import battlecode.common.*;
import static java.lang.Math.sqrt;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;

import java.util.*;


public strictfp class NetGun extends Building {
    NetGun(RobotController rc) {
        super(rc);
        this.rc = rc;
    }
    public void runTurn() throws GameActionException {
        shootOpponentDroneIfPossible();
    }
}