package net.natga999.wynn_ai.path.network;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RoadNode {
    private String id;
    private Vec3dData positionData; // Using a separate class for GSON compatibility
    private String worldId;
    private List<String> connections;
    private String type;

    // Transient Vec3d for actual use after deserialization
    private transient Vec3d positionVec;

    // Default constructor for GSON
    public RoadNode() {}

    public RoadNode(String id, Vec3d position, String worldId, List<String> connections, String type) {
        this.id = id;
        this.positionVec = position;
        this.positionData = new Vec3dData(position.x, position.y, position.z);
        this.worldId = worldId; // This needs to be passed or defaulted
        this.connections = (connections != null) ? new ArrayList<>(connections) : new ArrayList<>();
        this.type = type;
    }
    // Or a simpler one used by the command, and worldId is set later or defaults
    public RoadNode(String id, Vec3d position, String worldId, String type) { // Assumes empty connections initially
        this(id, position, worldId, new ArrayList<>(), type);
    }
    public String getId() {
        return id;
    }

    public Vec3d getPosition() {
        if (positionVec == null && positionData != null) {
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

    // Call this after GSON deserialization to initialize transient fields
    public void initializeTransientFields() {
        if (positionData != null) {
            this.positionVec = new Vec3d(positionData.x, positionData.y, positionData.z);
        }
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

    public void reconstructPosition() {
        // this.position = new Vec3d(this.x, this.y, this.z); // If you serialize x,y,z
        // If RoadNode directly has Vec3d and you use a TypeAdapter, this might not be needed.
        // However, if Vec3d is transient, and you serialize its components, this is essential.
    }

    // Inner class for GSON Vec3d serialization/deserialization
    public static class Vec3dData {
        public double x, y, z;

        public Vec3dData() {} // For GSON

        public Vec3dData(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}