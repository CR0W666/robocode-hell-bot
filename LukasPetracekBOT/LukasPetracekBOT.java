package LukasPetracekBOT;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

//Chtel jsem implementovat lepsi distancovani od ostatnich n00b botu, kvuli coronaviru a aby jsem mohl zandavat lepsi noscope a zrektil je uplne vsechny
//ale to se mi vubec nedarilo, takze jsem jen pridal jednu barvicku a nechal to byt:))) nevim co jinyho bych tam upravil

//zkousel jsem to jeste nakombit s wall hugerem, ale to uz nestiham, ale urcite by to mohlo byt interesting....

public class LukasPetracekBOT extends AdvancedRobot {

    public static int BINS = 47;
    public static double[] _surfStats = new double[BINS];
    public Point2D.Double _myLocation;
    public Point2D.Double _enemyLocation;

    public ArrayList _enemyWaves;
    public ArrayList _surfDirections;
    public ArrayList _surfAbsBearings;


    public static double _oppEnergy = 100.0;

    /**
     * This is a rectangle that represents an 800x600 battle field,
     * used for a simple, iterative WallSmoothing method (by PEZ).
     * wall stick indicates
     * the amount of space we try to always have on either end of the tank
     * (extending straight out the front or back) before touching a wall.
     */
    public static Rectangle2D.Double _fieldRect = new Rectangle2D.Double(18, 18, 764, 564);
    public static double WALL_STICK = 160;


    // guess factor targeting
    public List<BulletWave> waves = new ArrayList<>();
    public static int[] stats = new int[31]; // 31 is the number of unique GuessFactors we're using
    public int direction = 1;                // Note: this must be an odd number so we can get
    // GuessFactor 0 at the middle.




    public void run() {
        setBodyColor(Color.green);



        this._enemyWaves = new ArrayList();
        this._surfDirections = new ArrayList();
        this._surfAbsBearings = new ArrayList();

        this.setAdjustGunForRobotTurn(true);
        this.setAdjustRadarForGunTurn(true);

        do {
                if(getEnergy() == 100 ) {
                    setAllColors(Color.black);
                } else if(getEnergy()>=90 && getEnergy() < 100) {
                    setAllColors(Color.green);
                } else if(getEnergy()>=40) {
                    setAllColors(Color.yellow);
                } else if(getEnergy()<40) {
                    setAllColors(Color.red);
                }

            this.turnRadarRightRadians(Double.POSITIVE_INFINITY);
        } while (true);
    }



    public void onScannedRobot(ScannedRobotEvent e) {
        this._myLocation = new Point2D.Double(this.getX(), this.getY());

        double lateralVelocity = this.getVelocity() * Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + this.getHeadingRadians();

        this.setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        this._surfDirections.add(0, new Integer((lateralVelocity >= 0) ? 1 : -1));
        this._surfAbsBearings.add(0, new Double(absBearing + Math.PI));

        double bulletPower = this._oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09 && this._surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = this.bulletVelocity(bulletPower);
            ew.distanceTraveled = this.bulletVelocity(bulletPower);
            ew.direction = ((Integer) this._surfDirections.get(2)).intValue();
            ew.directAngle = ((Double) this._surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double) this._enemyLocation.clone();

            this._enemyWaves.add(ew);
        }

        this._oppEnergy = e.getEnergy();

        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        this._enemyLocation = project(this._myLocation, absBearing, e.getDistance());

        this.updateWaves();
        this.doSurfing();

        // gun code

        // find enemy's location
        double ex = getX() + Math.sin(absBearing) * e.getDistance();
        double ey = getY() + Math.cos(absBearing) * e.getDistance();

        // process bullet waves
        for(int i=0; i<waves.size(); i++) {
            BulletWave currentWave = waves.get(i);
            if(currentWave.checkHit(ex, ey, getTime())) {
                waves.remove(currentWave);
                i--;
            }
        }

        double power = Math.min(3, Math.max(0.1, 2));
        // dont try to figure out the direction they're moving
        // if they're not moving, just use the direction we had before
        if(e.getVelocity() != 0) {
            if(Math.sin(e.getHeadingRadians()-absBearing)*e.getVelocity() <0) {
                direction = -1;
            } else {
                direction = 1;
            }
        }
        int[] currentStats = stats;
        BulletWave newWave = new BulletWave(getX(), getY(), absBearing, power, direction, getTime(), currentStats);
        int bestIndex = 15; // initialize it to be in the middle, guessFactor 0.
        for(int i=0; i<31; i++) {
            if(currentStats[bestIndex] < currentStats[i]) {
                bestIndex = i;
            }
        }

        double guessFactor = (double)(bestIndex - (stats.length - 1) / 2) / ((stats.length - 1) / 2);
        double angleOffset = direction * guessFactor * newWave.maxEscapeAngle();
        double gunAdjust = Utils.normalRelativeAngle(
                absBearing - getGunHeadingRadians() + angleOffset);
        setTurnGunRightRadians(gunAdjust);
        if(getGunHeat() == 0 && gunAdjust < Math.atan2(9, e.getDistance()) && setFireBullet(power) != null) {
            waves.add(newWave);
        }
    }


    private void updateWaves() {
        for (int x = 0; x < this._enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) this._enemyWaves.get(x);

            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled > this._myLocation.distance(ew.fireLocation) + 50) {
                this._enemyWaves.remove(x);
                x--;
            }
        }
    }


    private EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000; // arbitrary big number
        EnemyWave surfWave = null;
        for (int x = 0; x < this._enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) this._enemyWaves.get(x);
            double distance = this._myLocation.distance(ew.fireLocation) - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }

        return surfWave;
    }


    /**
     * Given the EnemyWave that the bullet was on, and the point where we were hit,
     * calculate the index into our stat array for that factor.
     */
    private static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation) - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle) / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int)limit(0, (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2), BINS - 1);
    }


    /**
     * Given the EnemyWave that the bullet was on, and the point where we were hit,
     * update our stat array to reflect the danger in that area.
     */
    private void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);
        for (int x = 0; x < BINS; x++) {
            // for the spot bin that we were hit on, add 1;
            // for the bins next to it, add 1/2;
            // the next one, add 1/5; and so on....
            this._surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }



    public void onHitByBullet(HitByBulletEvent e) {
        // if the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
        if (!this._enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            // Look through the EnemyWaves, and find on that could've hit us.
            for (int x = 0; x < this._enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave) this._enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled - this._myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(this.bulletVelocity(e.getBullet().getPower()) - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                this.logHit(hitWave, hitBulletLocation);

                // we can remove this wave now, of course.
                this._enemyWaves.remove(this._enemyWaves.lastIndexOf(hitWave));
            }
        }
    }


    private Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double) this._myLocation.clone();
        double predictedVelocity = this.getVelocity();
        double predictedHeading = this.getHeadingRadians();
        double maxTurning, moveAngle, moveDir;

        int counter = 0; // number of ticks in the future
        boolean intercepted = false;

        do {
            moveAngle = wallSmoothing(predictedPosition,
                    absoluteBearing(surfWave.fireLocation, predictedPosition) + (direction * (Math.PI / 2)),
                    direction)
                    - predictedHeading;
            moveDir = 1;
            if (Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            //you can't turn more than this in one tick
            maxTurning = Math.PI / 720d * (40d - 3d * Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading + limit(maxTurning, moveAngle, maxTurning));

            //  if predictedVelocity and moveDir have
            // different signs you want to break down
            // otherwise, you want to accelerate (look at the factor "2")
            predictedVelocity += (predictedVelocity * moveDir < 0 ? 2 * moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);

            // calculate the new predicted position
            predictedPosition = project(predictedPosition, predictedHeading, predictedVelocity);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) < surfWave.distanceTraveled + (counter * surfWave.bulletVelocity) + surfWave.bulletVelocity) {
                intercepted = true;
            }
        } while (!intercepted && counter < 500);

        return predictedPosition;
    }


    private double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave, predictPosition(surfWave, direction));

        return this._surfStats[index];
    }


    private void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();
        if (surfWave == null) {
            return;
        }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, this._myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(this._myLocation, goAngle - (Math.PI / 2), -1);
        } else {
            goAngle = wallSmoothing(this._myLocation, goAngle + (Math.PI / 2), 1);
        }

        setBackAsFront(this, goAngle);
    }



    public void onHitWall(HitWallEvent e) {
        back(20);
    }


    private double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!this._fieldRect.contains(project(botLocation, angle, WALL_STICK))) {
            angle += orientation * 0.05;
        }

        return angle;
    }

    public static Point2D.Double project(Point2D.Double sourceLocation,
                                         double angle, double length) {
        return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
                sourceLocation.y + Math.cos(angle) * length);
    }

    private static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    private static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double bulletVelocity(double power) {
        return (20.0 - (3.0 * power));
    }

    private static double maxEscapeAngle(double velocity) {
        // http://robowiki.net/wiki/Maximum_Escape_Angle
        return Math.asin(8.0 / velocity);
    }

    private static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle = Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI / 2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1 * angle);
            } else {
                robot.setTurnRightRadians(angle);
            }
            robot.setAhead(100);
        }
    }
}
