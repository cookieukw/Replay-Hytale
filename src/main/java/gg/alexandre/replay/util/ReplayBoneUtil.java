package gg.alexandre.replay.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.joml.Vector3f;

public class ReplayBoneUtil {
    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();

    /**
     * Applies a visual rotation to a specific entity bone.
     * This update is sent only to the observer (player watching the replay).
     */
    public static void applyBoneRotation(PlayerRef observer, long entityId, String boneName, Vector3f rotation) {
        // TODO: Hytale API Investigation
        // Identify the proper packet (e.g. UpdateEntityModelPacket) or component (ModelComponent/AnimationComponent)
        // that allows overriding bone rotation for this entity on the client.
        
        logger.atInfo().log("Simulating bone rotation -> Observer: %s | EntityID: %d | Bone: %s | Pitch: %.2f, Yaw: %.2f, Roll: %.2f",
                observer.getUuid(), entityId, boneName, rotation.x, rotation.y, rotation.z);
        
        // Final networking logic will be built here to send to the observer packet handler.
    }
}
