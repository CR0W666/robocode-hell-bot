package LukasPetracekBOTwall;

import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.ScannedRobotEvent;

import java.awt.*;

public class LukasPetracekBOTwall extends AdvancedRobot {

    boolean peek;
    double moveAmount;

    //tohohle nepouzivej, straight copy waller robota pro easier cteni a testing ruznych zmen

    public void run() {

        if(getEnergy() == 100 ) {
            setAllColors(Color.black);
        } else if(getEnergy()>=90 && getEnergy() < 100) {
            setAllColors(Color.green);
        } else if(getEnergy()>=40) {
            setAllColors(Color.yellow);
        } else if(getEnergy()<40) {
            setAllColors(Color.red);
        }


        moveAmount = Math.max(getBattleFieldWidth(), getBattleFieldHeight());

        peek = false;
        turnLeft(getHeading() % 90);
        ahead(moveAmount);
        peek = true;
        turnGunRight(90);
        turnRight(90);

        while (true) {

            peek = true;

            ahead(moveAmount);

            peek = false;

            turnRight(90);
        }
    }


    public void onHitRobot(HitRobotEvent e) {

        if (e.getBearing() > -90 && e.getBearing() < 90) {
            back(100);
        }
        else {
            ahead(100);
        }
    }


    public void onScannedRobot(ScannedRobotEvent e) {
        fire(2);

        if (peek) {
            scan();
        }
    }

}
