package tannerplayer;
import battlecode.common.*;
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
// `./gradlew -q tasks` list gradlew commands

// I tried setting JAVA_HOME in .MacOSX/environment.plist and it didn't fix it.
// I had to create .MacOSX and environment.plist, so I deleted them after I saw that it didn't work.
// I've also tried setting JAVA_HOME in ~/.bash_profile

// BUT THIS WORKED:
// Run the export command above then run:
// open client/Battlecode\ Client.app/


enum RotationDirection {
    NULL,
    LEFT,
    RIGHT;
}
class RotDirFuncs {
    static RotationDirection getOpposite(final RotationDirection d) {
        switch(d) {
            case LEFT:
                return RotationDirection.RIGHT;
            case RIGHT:
                return RotationDirection.LEFT;
            default:
                return RotationDirection.NULL;
        }
    }
    static Direction getRotated(final Direction dir, final RotationDirection rd) {
        switch(rd) {
            case LEFT:
                return dir.rotateLeft();
            case RIGHT:
                return dir.rotateRight();
            default:
                return dir;
        }
    }
}


public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;


        Robot me = new Robot(rc);


        while (true) {
            me.runTurn();
        }
    }

   


}
