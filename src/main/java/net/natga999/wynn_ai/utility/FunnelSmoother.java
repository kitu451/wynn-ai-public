package net.natga999.wynn_ai.utility;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.stream.Collectors;

public class FunnelSmoother {
    public static List<Vec3d> smoothPath(List<BlockPos> path) {
        // Return the original path if it's too short
        if (path.size() < 3) {
            return path.stream().map(BlockPos::toCenterPos).toList();
        }

        // Convert BlockPos to Vec3d center coordinates
        List<Vec3d> centerPoints = path.stream()
                .map(BlockPos::toCenterPos)
                .collect(Collectors.toList());

        // Create portals (pairs of consecutive points)
        List<Vec3d> portals = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            portals.add(centerPoints.get(i));
            portals.add(centerPoints.get(i + 1));
        }

        // Initialize funnel algorithm
        List<Vec3d> smoothed = new ArrayList<>();
        if (portals.isEmpty()) {
            return centerPoints; // Return original points if no portals
        }

        Vec3d apex = portals.get(0);
        Vec3d left = portals.get(0);
        Vec3d right = portals.get(1);

        smoothed.add(apex);

        // Check if we have enough portals to continue
        if (portals.size() <= 2) {
            // Add the last point and return if we only have one portal
            if (portals.size() == 2 && !smoothed.contains(portals.get(1))) {
                smoothed.add(portals.get(1));
            }
            return smoothed;
        }

        // Process the funnel algorithm
        for (int i = 2; i < portals.size(); i += 2) {
            // Make sure we don't go out of bounds
            if (i + 1 >= portals.size()) break;

            Vec3d nextLeft = portals.get(i);
            Vec3d nextRight = portals.get(i + 1);

            // Implement the funnel algorithm logic
            if (cross(right.subtract(apex), nextLeft.subtract(apex)) > 0) {
                if (apex.equals(right) || cross(nextRight.subtract(apex), nextLeft.subtract(apex)) < 0) {
                    apex = right;
                    smoothed.add(apex);
                }
                left = nextLeft;
                right = nextRight;
            } else if (cross(left.subtract(apex), nextRight.subtract(apex)) < 0) {
                if (apex.equals(left) || cross(nextLeft.subtract(apex), nextRight.subtract(apex)) > 0) {
                    apex = left;
                    smoothed.add(apex);
                }
                left = nextLeft;
                right = nextRight;
            } else {
                // Both points are within the funnel, continue narrowing
                left = nextLeft;
                right = nextRight;
            }
        }

        // Add the final point if not already in the smoothed path
        Vec3d finalPoint = centerPoints.get(centerPoints.size() - 1);
        if (!smoothed.contains(finalPoint)) {
            smoothed.add(finalPoint);
        }

        return smoothed;
    }

    private static double cross(Vec3d a, Vec3d b) {
        return a.x * b.z - a.z * b.x;
    }
}