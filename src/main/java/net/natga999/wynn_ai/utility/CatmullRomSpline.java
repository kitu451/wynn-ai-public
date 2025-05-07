package net.natga999.wynn_ai.utility;

import net.minecraft.util.math.Vec3d;
import java.util.*;

public class CatmullRomSpline {
    public static List<Vec3d> createSpline(List<Vec3d> points, int segments) {
        if (points.size() < 2) {
            return new ArrayList<>(points);
        }

        List<Vec3d> spline = new ArrayList<>();

        // Always add the first point
        spline.add(points.get(0));

        if (points.size() == 2) {
            // For just two points, add the end point
            spline.add(points.get(1));
            return spline;
        }

        if (points.size() == 3) {
            // For three points, do basic interpolation
            for (int s = 1; s <= segments; s++) {
                float t = s / (float) segments;
                Vec3d p1 = points.get(0);
                Vec3d p2 = points.get(1);
                spline.add(p1.multiply(1 - t).add(p2.multiply(t)));
            }

            for (int s = 1; s <= segments; s++) {
                float t = s / (float) segments;
                Vec3d p1 = points.get(1);
                Vec3d p2 = points.get(2);
                spline.add(p1.multiply(1 - t).add(p2.multiply(t)));
            }

            return spline;
        }

        // Catmull-Rom requires at least 4 points
        // For the first segment, use the first point repeated
        Vec3d p0 = points.get(0);
        Vec3d p1 = points.get(0);
        Vec3d p2 = points.get(1);
        Vec3d p3 = points.get(2);

        for (int s = 1; s <= segments; s++) {
            float t = s / (float) segments;
            spline.add(interpolate(p0, p1, p2, p3, t));
        }

        // Middle segments
        for (int i = 0; i < points.size() - 3; i++) {
            p0 = points.get(i);
            p1 = points.get(i + 1);
            p2 = points.get(i + 2);
            p3 = points.get(i + 3);

            for (int s = 1; s <= segments; s++) {
                float t = s / (float) segments;
                spline.add(interpolate(p0, p1, p2, p3, t));
            }
        }

        // Last segment - uses the last point repeated
        if (points.size() >= 4) {
            p0 = points.get(points.size() - 3);
            p1 = points.get(points.size() - 2);
            p2 = points.getLast();
            p3 = points.getLast();

            for (int s = 1; s <= segments; s++) {
                float t = s / (float) segments;
                spline.add(interpolate(p0, p1, p2, p3, t));
            }
        }

        return spline;
    }

    private static Vec3d interpolate(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        // Standard Catmull-Rom formulation
        float b1 = 0.5f * (-t3 + 2*t2 - t);
        float b2 = 0.5f * (3*t3 - 5*t2 + 2);
        float b3 = 0.5f * (-3*t3 + 4*t2 + t);
        float b4 = 0.5f * (t3 - t2);

        return new Vec3d(
                b1 * p0.x + b2 * p1.x + b3 * p2.x + b4 * p3.x,
                b1 * p0.y + b2 * p1.y + b3 * p2.y + b4 * p3.y,
                b1 * p0.z + b2 * p1.z + b3 * p2.z + b4 * p3.z
        );
    }
}