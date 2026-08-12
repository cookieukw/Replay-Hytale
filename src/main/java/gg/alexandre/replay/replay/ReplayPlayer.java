package gg.alexandre.replay.replay;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateBlockHitboxes;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.protocol.packets.connection.ClientDisconnect;
import com.hypixel.hytale.protocol.packets.connection.Ping;
import com.hypixel.hytale.protocol.packets.connection.Pong;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.ClientReady;
import com.hypixel.hytale.protocol.packets.player.ClientTeleport;
import com.hypixel.hytale.protocol.packets.player.JoinWorld;
import com.hypixel.hytale.protocol.packets.setup.RequestAssets;
import com.hypixel.hytale.protocol.packets.setup.SetTimeDilation;
import com.hypixel.hytale.protocol.packets.setup.ViewRadius;
import com.hypixel.hytale.protocol.packets.world.UpdateTime;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.handlers.SetupPacketHandler;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;
import gg.alexandre.replay.ReplayPlugin;
import gg.alexandre.replay.file.ReplayInputFile;
import gg.alexandre.replay.protocol.ReplayPacket;
import gg.alexandre.replay.protocol.ReplayProtocol;
import gg.alexandre.replay.protocol.packets.EndSnapshotReplayPacket;
import gg.alexandre.replay.protocol.packets.TickReplayPacket;
import gg.alexandre.replay.replay.editor.properties.EntityBonePath;
import gg.alexandre.replay.replay.editor.properties.base.BaseProperty;
import gg.alexandre.replay.replay.state.ReplayState;
import gg.alexandre.replay.util.CameramanUtil;
import gg.alexandre.replay.util.CameraPathDebugOverlay;
import gg.alexandre.replay.util.FovPacketUtil;
import gg.alexandre.replay.util.Position;
import gg.alexandre.replay.util.PositionTracker;
import gg.alexandre.replay.util.ReplayBoneUtil;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ReplayPlayer extends BasePlayer {

    public static final int SNAPSHOT_INTERVAL_TICKS = 30 * 60 * 5;

    private final Path replayStatePath;

    private final ReplayProtocol protocol;
    private final HytaleLogger logger = HytaleLogger.forEnclosingClass();

    public ReplayPlayer(@Nonnull ReplayProtocol protocol, @Nonnull Path dataDirectory) {
        this.protocol = protocol;
        replayStatePath = dataDirectory.resolve("state.json");

        PacketAdapters.registerInbound((PacketFilter) (handler, packet) -> {
            ReplayState state = getState(handler);
            if (state == null) {
                return false;
            }

            assert handler.getAuth() != null;
            if (state.playerUuid == null || !state.playerUuid.equals(handler.getAuth().getUuid())) {
                return false;
            }

            if (packet instanceof RequestAssets && !state.stage.processedConfigPhase) {
                state.stage.isProcessingPackets = true;
                state.file.consumeConfigPhase((replayPacket) -> replayPacket.handle(handler, state));
                handler.tryFlush();
                state.stage.isProcessingPackets = false;

                try {
                    Field receivedRequest = SetupPacketHandler.class.getDeclaredField("receivedRequest");
                    receivedRequest.setAccessible(true);
                    receivedRequest.set(handler, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                state.stage.processedConfigPhase = true;

                return true;
            }

            if (packet instanceof ViewRadius viewRadius) {
                state.clientViewRadius = viewRadius.value;
            }

            if (packet instanceof ClientReady clientReady) {
                state.stage.clientReady = true;

                if (clientReady.readyForChunks) {
                    state.stage.clearedWorld = true;
                    state.stage.hasStarted = true;
                }

                // Restore view radius
                if (clientReady.readyForGameplay && !state.stage.restoredViewRadius) {
                    int maxViewRadius = HytaleServer.get().getConfig().getMaxViewRadius();
                    state.stage.restoredViewRadius = true;
                    bypassFilter(state, () ->
                            handler.write(new ViewRadius(Math.min(state.clientViewRadius, maxViewRadius * 32)))
                    );
                }

                return false;
            }

            if (packet instanceof SyncInteractionChains syncInteractionChains) {
                handleInteractionChains(handler, state, syncInteractionChains);
                return true;
            }

            if (state.stage.isFilteringPackets && packet instanceof ClientDisconnect) {
                stop(state);
                return false;
            }

            if (packet instanceof ClientMovement movement) {
                PositionTracker.onClientMovement(state, movement);
            }

            if (state.stage.hasStarted) {
                return !(packet instanceof Pong || packet instanceof CustomPageEvent || packet instanceof ChatMessage);
            } else {
                return false;
            }
        });

        PacketAdapters.registerOutbound((PacketFilter) (handler, packet) -> {
            ReplayState state = getState(handler);
            if (state == null) {
                return false;
            }

            if (packet instanceof UpdateBlockHitboxes hitboxes) {
                assert hitboxes.blockBaseHitboxes != null;
                hitboxes.blockBaseHitboxes.replaceAll((_, _) -> new Hitbox[0]);
            }

            if (packet instanceof ViewRadius viewRadius) {
                if (!state.stage.restoredViewRadius) {
                    viewRadius.value = 32; // Fast load
                }
                return false;
            }

            if (packet instanceof Ping ||
                packet instanceof SetPage ||
                packet instanceof CustomPage ||
                packet instanceof ResetUserInterfaceState ||
                packet instanceof UpdateAnchorUI) {
                return packet instanceof CustomPage customPage &&
                       customPage.key != null && !customPage.key.startsWith("gg.alexandre.");
            }

//            if (packet instanceof UpdateBlockTypes updateBlockTypes && updateBlockTypes.blockTypes != null) {
//                for (BlockType type : updateBlockTypes.blockTypes.values()) {
//                    // Stops caustics from rendering, required for fov property
//                    type.requiresAlphaBlending = true;
//                }
//            }

            if (packet instanceof UpdateTranslations && !state.stage.sentTranslations) {
                state.stage.sentTranslations = true;
                I18nModule.get().sendTranslations(handler, state.lang);
                return true;
            }

            if (packet instanceof JoinWorld && !state.stage.sentJoinWorld) {
                state.stage.sentJoinWorld = true;
                return false;
            }

            if (packet instanceof UpdateTime updateTime) {
                setDayTime(state, updateTime);
            }

            boolean filter = state.stage.isFilteringPackets && !state.stage.isProcessingPackets;

            if (!filter) {
                if (packet instanceof EntityUpdates entityUpdates) {
                    if (entityUpdates.removed != null) {
                        for (int id : entityUpdates.removed) {
                            state.entityIds.remove(id);
                        }
                    }

                    if (entityUpdates.updates != null) {
                        for (EntityUpdate update : entityUpdates.updates) {
                            if (update.networkId == state.clientId && update.updates != null) {
                                for (ComponentUpdate data : update.updates) {
                                    if (data instanceof TransformUpdate transformUpdate) {
                                        Position pos;
                                        if (!state.position.sentInitialPosition &&
                                            state.file.getMetadata().position != null) {
                                            state.position.sentInitialPosition = true;
                                            pos = state.file.getMetadata().position;
                                        } else {
                                            pos = new Position(
                                                    state.position.x,
                                                    state.position.y,
                                                    state.position.z,
                                                    state.position.headPitch,
                                                    state.position.headYaw
                                            );
                                        }

                                        transformUpdate.transform.position = PositionUtil.toPositionPacket(
                                                new Vector3d(pos.x(), pos.y(), pos.z())
                                        );
                                        transformUpdate.transform.bodyOrientation = PositionUtil.toDirectionPacket(
                                                new Rotation3f(0, (float) pos.pitch(), 0)
                                        );
                                        transformUpdate.transform.lookOrientation = PositionUtil.toDirectionPacket(
                                                new Rotation3f((float) pos.yaw(), (float) pos.pitch(), 0)
                                        );

                                        PositionTracker.onTransformUpdate(state, transformUpdate);
                                    }
                                }
                            }

                            state.entityIds.add(update.networkId);
                        }
                    }
                }

                if (packet instanceof ClientTeleport teleport) {
                    PositionTracker.onClientTeleport(state, teleport);
                }
            }

            if (packet instanceof ShowEventTitle) {
                return true;
            }

            return filter;
        });
    }

    public void setup() {
        try {
            loadState();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void clearWorld(@Nonnull PlayerRef playerRef, @Nonnull ReplayState state) {
        Ref<EntityStore> ref = playerRef.getReference();
        assert ref != null;
        Store<EntityStore> store = ref.getStore();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        EntityTrackerSystems.EntityViewer viewer = store.getComponent(
                ref, EntityTrackerSystems.EntityViewer.getComponentType()
        );
        if (viewer != null) {
            state.entityIds.addAll(viewer.visible.stream().map(entityStoreRef -> {
                NetworkId networkId = store.getComponent(entityStoreRef, NetworkId.getComponentType());
                assert networkId != null;
                return networkId.getId();
            }).toList());
        }

        PacketHandler packetHandler = playerRef.getPacketHandler();

        state.entityIds.remove(state.clientId);

        EntityUpdates entityUpdates = new EntityUpdates();
        entityUpdates.removed = state.entityIds.stream().mapToInt(Integer::intValue).toArray();
        packetHandler.writeNoCache(entityUpdates);
        state.entityIds.clear();

        state.cameraManager.setDefaultCamera(packetHandler);

        packetHandler.tryFlush();

        state.stage.clearedWorld = true;
    }

    public boolean isPlaying(@Nonnull PlayerRef playerRef) {
        ReplayState state = states.get(playerRef.getUuid());
        return state != null;
    }

    public void start(@Nonnull PlayerRef playerRef, @Nonnull Path replayPath) {
        if (isPlaying(playerRef)) {
            clearWorld(playerRef, states.get(playerRef.getUuid()));
        }

        // Capture the player's current world before transitioning so we can return them to it.
        Ref<EntityStore> ref = playerRef.getReference();
        World originWorld = (ref != null) ? ref.getStore().getExternalData().getWorld() : null;

        initState(playerRef.getUuid(), playerRef.getLanguage(), replayPath);

        ReplayState state = states.get(playerRef.getUuid());
        state.originalWorld = originWorld;

        transfer(playerRef, true);
    }

    public void initState(@Nonnull UUID uuid, @Nonnull String lang, @Nonnull Path replayPath) {
        ReplayState state = new ReplayState();
        state.stage.isFilteringPackets = true;
        state.path = replayPath;
        state.playerUuid = uuid;
        state.lang = lang;
        // Run the first second so we don't see a blank world
        state.targetTick = 30;

        try {
            state.file = new ReplayInputFile(replayPath, protocol);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        states.put(uuid, state);

        try {
            state.loadTimelines(getSaveUUID(state));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadState() throws IOException {
        if (!Files.exists(replayStatePath)) {
            return;
        }

        try (JsonReader reader = new JsonReader(new FileReader(replayStatePath.toFile()))) {
            JsonObject json = ReplayPlugin.get().getGson().fromJson(reader, JsonObject.class);

            long timestamp = json.get("timestamp").getAsLong();
            if (Instant.ofEpochMilli(timestamp).plus(5, ChronoUnit.MINUTES).isBefore(Instant.now())) {
                logger.atWarning().log("Expired replay state, ignoring");
                return;
            }

            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String lang = json.get("lang").getAsString();
            Path replayPath = Path.of(json.get("replayPath").getAsString());

            initState(uuid, lang, replayPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Files.delete(replayStatePath);
        }
    }

    /**
     * Saves the current replay state to disk as a crash-recovery fallback.
     * This is no longer used in the normal transition flow, but is kept in case the
     * server shuts down unexpectedly while a replay is in progress.
     */
    private void saveState(@Nonnull ReplayState state) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", state.playerUuid.toString());
        json.addProperty("lang", state.lang);
        json.addProperty("replayPath", state.path.toString());
        json.addProperty("timestamp", System.currentTimeMillis());

        try (JsonWriter writer = new JsonWriter(new FileWriter(replayStatePath.toFile()))) {
            ReplayPlugin.get().getGson().toJson(json, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Seamlessly transitions the player in or out of replay mode using
     * {@link Universe#resetPlayer}, which reloads the player's entity state into
     * the target world without any disconnect.
     *
     * @param playerRef the player to transition
     * @param toReplay  {@code true} to move the player into their replay world,
     *                  {@code false} to return them to their original world
     */
    private void transfer(@Nonnull PlayerRef playerRef, boolean toReplay) {
        ReplayState state = states.get(playerRef.getUuid());

        // Determine which world to move the player into.
        World targetWorld;
        if (toReplay) {
            // The replay world is the world the player is currently in — we keep them
            // in the same world but reset their entity context so the packet filter
            // can intercept the fresh join sequence and replay the file.
            Ref<EntityStore> ref = playerRef.getReference();
            targetWorld = (ref != null) ? ref.getStore().getExternalData().getWorld() : null;
        } else {
            // Return the player to the world they were in before the replay started.
            targetWorld = (state != null) ? state.originalWorld : null;
        }

        if (targetWorld == null) {
            // Fallback: if we lost track of the world, use the server default.
            targetWorld = Universe.get().getDefaultWorld();
        }

        if (targetWorld == null) {
            logger.atWarning().log("No target world found for player %s during replay transfer — keeping them in place.", playerRef.getUuid());
            return;
        }

        final World finalTargetWorld = targetWorld;
        Universe.get().getPlayerStorage().load(playerRef.getUuid())
                .thenCompose(holder -> {
                    CompletableFuture<PlayerRef> future = new CompletableFuture<>();
                    finalTargetWorld.execute(() -> {
                        Universe.get().resetPlayer(playerRef, holder, finalTargetWorld, null)
                                .whenComplete((result, ex) -> {
                                    if (ex != null) future.completeExceptionally(ex);
                                    else future.complete(result);
                                });
                    });
                    return future;
                })
                .thenAccept(ref -> {
                    if (toReplay && ref != null) {
                        var entityRef = ref.getReference();
                        if (entityRef != null) {
                            CameramanUtil.makeGhost(entityRef.getStore(), entityRef);
                        }
                    }
                })
                .exceptionally(throwable -> {
                    logger.atWarning().withCause(throwable).log(
                            "Failed to seamlessly transfer player %s %s replay; falling back to disconnect.",
                            playerRef.getUuid(), toReplay ? "into" : "out of"
                    );

                    // Legacy fallback: disconnect with a message so the player can reconnect manually.
                    if (toReplay && state != null) {
                        saveState(state);
                    }
                    playerRef.getPacketHandler().disconnect(
                            toReplay ?
                                    Message.raw("Click 'Reconnect' to access your Replay.") :
                                    Message.raw("Click 'Reconnect' to access your World.")
                    );
                    return null;
                });
    }

    public void restart(@Nonnull ReplayState state, int tick) {
        if (!state.stage.hasStarted) {
            return;
        }

        try {
            state.file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            state.file = new ReplayInputFile(state.path, protocol);

            TreeMap<Integer, Integer> snapshotOffsets = state.file.getMetadata().snapshotOffsets;
            if (snapshotOffsets != null) {
                Map.Entry<Integer, Integer> entry = snapshotOffsets.floorEntry(tick);
                if (entry != null) {
                    state.file.skip(entry.getValue());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        state.processedSnapshot = false;
        state.stage.clearedWorld = false;
        state.stage.sentJoinWorld = false;
        state.stage.clientReady = false;
        state.stage.processedConfigPhase = false;
        state.clientId = 0;

        super.restart(state);

        state.targetTick = tick;
    }

    public void stop(@Nonnull PlayerRef playerRef) {
        ReplayState state = states.get(playerRef.getUuid());
        if (state == null) {
            return;
        }

        if (!state.stage.hasStarted) {
            // Replay was initialised but never fully started; just clean up the state
            states.remove(state.playerUuid);
            try {
                state.file.close();
            } catch (IOException e) {
                logger.atWarning().withCause(e).log("Error closing replay file for uninitialised replay");
            }
            return;
        }

        // Clean up client-side effects before removing state
        if (playerRef.isValid()) {
            if (state.fovUtil != null) {
                state.fovUtil.clear();
            }
            state.overlay.clearImmediately(playerRef);
        }

        stop(state);

        if (playerRef.isValid()) {
            state.cameraManager.setDefaultCamera(playerRef);
            transfer(playerRef, false);
        }
    }

    public void stop(@Nonnull ReplayState state) {
        states.remove(state.playerUuid);

        try {
            state.timeline.save(state.file.getMetadata().uuid, state.selectedTimeline);

            state.file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.atInfo().log("Stopped replaying");
    }

    public void stopAll() {
        for (ReplayState state : states.values()) {
            stop(state);
        }
    }

    @Override
    public void tick(float v, int i, @Nonnull Store<EntityStore> store) {
        for (ReplayState state : states.values()) {
            PlayerRef playerRef = Universe.get().getPlayer(state.playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            // TODO: use tick on player directly using a component
            if (ref == null || ref.getStore() != store) {
                continue;
            }

            World world = store.getExternalData().getWorld();
            tick(state, playerRef, world);
        }
    }

    private void tick(@Nonnull ReplayState state, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!state.stage.hasStarted) {
            return;
        }

        handlePage(state, playerRef);

        PacketHandler packetHandler = playerRef.getPacketHandler();
        try {
            state.stage.isProcessingPackets = true;

            if (!state.stage.clearedWorld) {
                clearWorld(playerRef, state);
                return;
            }

            boolean forceProcess = !state.stage.restoredViewRadius;

            int processedPackets = 0;
            int packetLimit = forceProcess ? 10 : 400;

            while (canProcessPackets(state) &&
                   state.file.read(forceProcess ? Integer.MAX_VALUE : (int) state.targetTick) &&
                   processedPackets < packetLimit) {
                ReplayPacket replayPacket = state.file.consumePacket();

                if (!state.ignorePackets || replayPacket instanceof EndSnapshotReplayPacket) {
                    replayPacket.handle(packetHandler, state);
                }

                processedPackets++;

                if (replayPacket instanceof TickReplayPacket) {
                    tickEditor(state, playerRef, world, false);
                }
            }

            if (processedPackets < packetLimit && (!state.stage.sentJoinWorld || state.stage.isPlaying) && canProcessPackets(state)) {
                state.targetTick += Math.max(0.01, state.edit.speed);
                state.targetTick = Math.min(state.targetTick, state.file.getMetadata().ticks);
            }

            tickEditor(state, playerRef, world, true);
        } catch (Exception e) {
            logger.atWarning().withCause(e).log("Error while processing replay packets");
        } finally {
            state.stage.isProcessingPackets = false;
        }
    }

    private void tickEditor(@Nonnull ReplayState state, @Nonnull PlayerRef playerRef, @Nonnull World world,
                            boolean move) {
        if (state.timeline.getLastSaved().plus(5, ChronoUnit.MINUTES).isBefore(Instant.now())) {
            state.timeline.save(state.file.getMetadata().uuid, state.selectedTimeline);
        }

        if (!state.stage.sentJoinWorld || !canProcessPackets(state)) {
            return;
        }

        state.edit.speed = 1.0;
        state.edit.roll = 0.0;

        state.edit.cameraPosition = new Position(
                state.position.x,
                state.position.y,
                state.position.z,
                state.position.headPitch, // yaw and pitch from packets are inverted?
                state.position.headYaw
        );

        if (state.stage.isPlaying && !state.ui.controlGame) {
            state.edit.fov = 1.0;
            state.edit.time = -1.0;

            for (BaseProperty<?> property : state.timeline.getProperties().values()) {
                property.handle(state, state.targetTick);
            }

            if (state.timeline.getBonePaths() != null) {
                long currentTimestamp = (long) state.targetTick;
                for (List<EntityBonePath> paths : state.timeline.getBonePaths().values()) {
                    for (EntityBonePath path : paths) {
                        Vector3f rot = path.getInterpolatedRotation(currentTimestamp);
                        ReplayBoneUtil.applyBoneRotation(playerRef, path.getEntityId(), path.getBoneName(), rot);
                    }
                }
            }
        } else {
            state.cameraManager.applyFreeCameraMovement(playerRef);
        }

        if (move) {
            state.cameraManager.moveCamera(state, playerRef, false);
        }

        handleTimeDilation(state, playerRef.getPacketHandler(), move);
        handleDayTime(state, playerRef.getPacketHandler());

        if (move && state.timeline.getProperties().containsKey("camera")) {
            handleCameraPathDisplay(state, playerRef);
        }

        if (move) {
            if (state.fovUtil == null) {
                state.fovUtil = new FovPacketUtil(playerRef, world);
                state.fovUtil.setup();
            }

            Vector3d playerPosition = new Vector3d(state.position.x, state.position.y, state.position.z);
            state.fovUtil.apply(state.edit.fov, playerPosition);
        }
    }

    private void handleTimeDilation(@Nonnull ReplayState state, @Nonnull PacketHandler packetHandler, boolean move) {
        float speed = (float) state.edit.speed;
        if (state.ui.dragging || state.ui.controlGame || state.currentTick + speed <= state.targetTick) {
            state.overrideTimeDilation = true;
            speed = 1;
        } else if (state.overrideTimeDilation) {
            state.overrideTimeDilation = !move;
            speed = 1;
        } else if (!state.stage.isPlaying) {
            speed = 0;
        }

        if (state.timeDilation != speed) {
            state.timeDilation = speed;
            packetHandler.writeNoCache(new SetTimeDilation(Math.min(Math.max(0.0101f, speed), 4)));
        }
    }

    private void handleDayTime(@Nonnull ReplayState state, @Nonnull PacketHandler packetHandler) {
        if (state.edit.time >= 0) {
            UpdateTime packet = new UpdateTime();
            setDayTime(state, packet);
            packetHandler.write(packet);
        }
    }

    private void setDayTime(@Nonnull ReplayState state, @Nonnull UpdateTime packet) {
        if (state.edit.time >= 0) {
            Instant instant = WorldTimeResource.ZERO_YEAR.plusNanos(
                    (long) (WorldTimeResource.NANOS_PER_DAY * state.edit.time)
            );
            packet.gameTime = new InstantData(instant.getEpochSecond(), instant.getNano());
        }
    }

    private boolean canProcessPackets(@Nonnull ReplayState state) {
        if (!state.stage.sentJoinWorld) {
            return true;
        }

        return state.stage.clientReady;
    }

    @Override
    public void bypassFilter(@Nonnull ReplayState state, @Nonnull Runnable runnable) {
        if (!state.stage.isFilteringPackets || state.stage.isProcessingPackets) {
            runnable.run();
            return;
        }

        state.stage.isProcessingPackets = true;
        try {
            runnable.run();
        } finally {
            state.stage.isProcessingPackets = false;
        }
    }

    @Override
    public int getDurationTicks(@Nonnull ReplayState state) {
        return state.file.getMetadata().ticks;
    }

    @Override
    public UUID getSaveUUID(@NonNullDecl ReplayState state) {
        return state.file.getMetadata().uuid;
    }

    @Override
    public void goTo(@Nonnull ReplayState state, int tick) {
        if (tick < state.targetTick || tick - state.currentTick >= SNAPSHOT_INTERVAL_TICKS) {
            restart(state, tick);
        }

        state.targetTick = tick;
    }
}
