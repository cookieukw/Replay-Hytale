package gg.alexandre.replay.replay.editor.properties;

import org.joml.Vector3f;

public class BoneKeyframe {
    private final long timestamp;
    private final Vector3f rotation;

    public BoneKeyframe(long timestamp, Vector3f rotation) {
        this.timestamp = timestamp;
        this.rotation = new Vector3f(rotation);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Vector3f getRotation() {
        return new Vector3f(rotation);
    }

    public Vector3f interpolate(BoneKeyframe next, double progress) {
        Vector3f currentRot = getRotation();
        Vector3f nextRot = next.getRotation();
        
        // Simple linear interpolation for Euler angles
        float x = (float) (currentRot.x + (nextRot.x - currentRot.x) * progress);
        float y = (float) (currentRot.y + (nextRot.y - currentRot.y) * progress);
        float z = (float) (currentRot.z + (nextRot.z - currentRot.z) * progress);
        
        return new Vector3f(x, y, z);
    }
}
