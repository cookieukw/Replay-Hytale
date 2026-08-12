package gg.alexandre.replay.replay.editor.properties;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EntityBonePath {
    private final long entityId;
    private final String boneName;
    private final List<BoneKeyframe> keyframes;

    public EntityBonePath(long entityId, String boneName) {
        this.entityId = entityId;
        this.boneName = boneName;
        this.keyframes = new ArrayList<>();
    }

    public long getEntityId() {
        return entityId;
    }

    public String getBoneName() {
        return boneName;
    }

    public List<BoneKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }

    public void addKeyframe(BoneKeyframe keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingLong(BoneKeyframe::getTimestamp));
    }

    public void removeKeyframe(long timestamp) {
        keyframes.removeIf(k -> k.getTimestamp() == timestamp);
    }

    public Vector3f getInterpolatedRotation(long currentTimestamp) {
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }
        
        if (keyframes.size() == 1 || currentTimestamp <= keyframes.get(0).getTimestamp()) {
            return keyframes.get(0).getRotation();
        }
        
        BoneKeyframe lastKeyframe = keyframes.get(keyframes.size() - 1);
        if (currentTimestamp >= lastKeyframe.getTimestamp()) {
            return lastKeyframe.getRotation();
        }

        // Find the keyframes to interpolate between
        for (int i = 0; i < keyframes.size() - 1; i++) {
            BoneKeyframe current = keyframes.get(i);
            BoneKeyframe next = keyframes.get(i + 1);

            if (currentTimestamp >= current.getTimestamp() && currentTimestamp <= next.getTimestamp()) {
                double progress = (double) (currentTimestamp - current.getTimestamp()) / 
                                  (next.getTimestamp() - current.getTimestamp());
                return current.interpolate(next, progress);
            }
        }
        
        return new Vector3f(0, 0, 0); // Fallback, though we shouldn't reach here
    }
}
