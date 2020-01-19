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

        Robot me;

        switch(rc.getType()) {
            case MINER:                 me = new Miner(rc);             break;
            case LANDSCAPER:            me = new Landscaper(rc);        break;
            case DELIVERY_DRONE:        me = new DeliveryDrone(rc);     break;
            case HQ:                    me = new Hq(rc);                break;
            case DESIGN_SCHOOL:         me = new DesignSchool(rc);      break;
            case FULFILLMENT_CENTER:    me = new FulfillmentCenter(rc); break;
            case NET_GUN:               me = new NetGun(rc);            break;
            case REFINERY:              me = new Refinery(rc);          break;
            case VAPORATOR:             me = new Vaporator(rc);         break;
            default:                   return;
        }




        while (true) {
            try {
                me.takeTurn();
            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }

            // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
            Clock.yield();
        }
    }

   


}
