package net.natga999.wynn_ai.utility;

import net.minecraft.util.math.Vec3d;
import java.util.*;

public class CatmullRomSpline {
    private static final double TENSION = 0.5;
    private static final double DROP_THRESHOLD = 0.1; // Blocks drop
    private static final double DIST_THRESHOLD = 1.0; // Blocks distance

    public static List<Vec3d> createSpline(List<Vec3d> points, int segments) {
        if (points.size() < 2) return new ArrayList<>(points);

        List<Vec3d> spline = new ArrayList<>();
        int n = points.size();

        // Straight line for exactly 2 points
        if (n == 2) {
            Vec3d start = points.get(0);
            Vec3d end = points.get(1);
            for (int s = 0; s <= segments; s++) {
                double t = s / (double) segments;
                spline.add(start.multiply(1 - t).add(end.multiply(t)));
            }
            return spline;
        }

        // Create "virtual" endpoints for smooth rounding
        Vec3d virtualStart = points.get(0).subtract(points.get(1).subtract(points.get(0)));
        Vec3d virtualEnd = points.get(n - 1).add(points.get(n - 1).subtract(points.get(n - 2)));

        List<Vec3d> extended = new ArrayList<>();
        extended.add(virtualStart);
        extended.addAll(points);
        extended.add(virtualEnd);

        // Iterate through each segment
        for (int i = 1; i < extended.size() - 2; i++) {
            Vec3d p0 = extended.get(i - 1);
            Vec3d p1 = extended.get(i);
            Vec3d p2 = extended.get(i + 1);
            Vec3d p3 = extended.get(i + 2);

            // Detect a steep drop: if p1 to p2 drops more than threshold
            if ((p1.y - p2.y > DROP_THRESHOLD) || (p2.y - p1.y >= DROP_THRESHOLD)) {
                // Add only start and end of drop to create a single straight segment
                spline.add(p1);
                spline.add(p2);
                continue;
            }

            // Detect very short distance: straight line only
            if (p1.distanceTo(p2) < DIST_THRESHOLD) {
                spline.add(p1);
                spline.add(p2);
                continue;
            }

            // Otherwise, interpolate smoothly
            for (int s = 0; s <= segments; s++) {
                double t = s / (double) segments;
                spline.add(interpolate(p0, p1, p2, p3, t));
            }
        }

        return spline;
    }

    private static Vec3d interpolate(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        // Tension-adjusted basis functions
        double b0 = -TENSION * t3 + 2 * TENSION * t2 - TENSION * t;
        double b1 = (2 - TENSION) * t3 + (TENSION - 3) * t2 + 1;
        double b2 = (TENSION - 2) * t3 + (3 - 2 * TENSION) * t2 + TENSION * t;
        double b3 = TENSION * t3 - TENSION * t2;

        return new Vec3d(
                b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
                b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y,
                b0 * p0.z + b1 * p1.z + b2 * p2.z + b3 * p3.z
        );
    }
}
