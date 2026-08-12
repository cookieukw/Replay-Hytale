package gg.alexandre.replay.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.joml.Vector3f;

public class ReplayBoneUtil {
    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();

    /**
     * Aplica uma rotação visual a um osso específico de uma entidade.
     * Esta alteração é enviada apenas para o observer (jogador assistindo ao replay).
     */
    public static void applyBoneRotation(PlayerRef observer, long entityId, String boneName, Vector3f rotation) {
        // TODO: Investigação da API do Hytale
        // Descobrir o pacote correto (ex: UpdateEntityModelPacket) ou o Componente (ModelComponent/AnimationComponent)
        // que permite sobrescrever a rotação de um osso para esta entidade no cliente.
        
        logger.atInfo().log("Simulando rotação de osso -> Observer: %s | EntityID: %d | Osso: %s | Pitch: %.2f, Yaw: %.2f, Roll: %.2f",
                observer.getUuid(), entityId, boneName, rotation.x, rotation.y, rotation.z);
        
        // Aqui construiremos a lógica final de rede para enviar ao packet handler do observer.
    }
}
