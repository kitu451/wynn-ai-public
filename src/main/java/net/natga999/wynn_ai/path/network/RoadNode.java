package net.natga999.wynn_ai.path.network;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RoadNode {
    private final String id;
    private final Vec3dData positionData; // Using a separate class for GSON compatibility
    private final String worldId;
    private final List<String> connections;
    private String type;
    private String targetTunnelExitNodeId;

    // Transient Vec3d for actual use after deserialization
    private transient Vec3d positionVec;

    public RoadNode(String id, Vec3d position, String worldId, List<String> connections, String type) {
        this(id, position, worldId, connections, type, null);
    }

    public RoadNode(String id, Vec3d position, String worldId, List<String> connections, String type, String targetTunnelExitNodeId) {
        this.id = id;
        this.positionVec = position;
        this.positionData = new Vec3dData(position.x, position.y, position.z);
        this.worldId = worldId;
        this.connections = (connections != null) ? new ArrayList<>(connections) : new ArrayList<>();
        this.type = type;
        this.targetTunnelExitNodeId = targetTunnelExitNodeId;
    }

    public String getId() {
        return id;
    }

    public Vec3d getPosition() {
        if (positionVec == null) {
            positionVec = new Vec3d(positionData.x, positionData.y, positionData.z);
        }
        return positionVec;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getWorldId() {
        return worldId;
    }

    public List<String> getConnections() {
        return connections;
    }

    public String getType() {
        return type;
    }

    public String getTargetTunnelExitNodeId() {
        return targetTunnelExitNodeId;
    }

    //TODO make commands for this
    public void setTargetTunnelExitNodeId(String targetTunnelExitNodeId) {
        this.targetTunnelExitNodeId = targetTunnelExitNodeId;
    }

    // Call this after GSON deserialization to initialize transient fields
    public void initializeTransientFields() {
        this.positionVec = new Vec3d(positionData.x, positionData.y, positionData.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoadNode roadNode = (RoadNode) o;
        return Objects.equals(id, roadNode.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoadNode{" +
                "id='" + id + '\'' +
                ", position=" + getPosition() +
                ", worldId='" + worldId + '\'' +
                ", type='" + type + '\'' +
                '}';
    }

    // Inner class for GSON Vec3d serialization/deserialization
    public static class Vec3dData {
        public double x, y, z;

        public Vec3dData(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}