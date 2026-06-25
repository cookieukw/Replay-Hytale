package gg.alexandre.replay.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import gg.alexandre.replay.ReplayPlugin;
import gg.alexandre.replay.recorder.RecordingData;
import gg.alexandre.replay.recorder.ReplayRecorder;
import gg.alexandre.replay.repository.ReplayRepository;
import gg.alexandre.replay.ui.codec.CodecConstructor;
import gg.alexandre.replay.ui.event.UIEventContext;
import gg.alexandre.replay.ui.event.UIEventHandler;
import gg.alexandre.replay.ui.event.UIEventIdData;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.hypixel.hytale.server.core.entity.entities.Player;

public class ReplayUI extends BaseUI<ReplayUI.Data> {

    private final ReplayRepository replayRepository;
    private final ReplayRecorder recorder;

    private boolean recording;

    private static final BuilderCodec<Data> CODEC = CodecConstructor.create(Data.class, Data::new);

    public static class Data extends UIEventIdData {

    }

    public ReplayUI(@Nonnull PlayerRef playerRef, @Nonnull ReplayRepository replayRepository,
                    @Nonnull ReplayRecorder recorder) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, CODEC);

        this.replayRepository = replayRepository;
        this.recorder = recorder;
    }

    @Override
    public void init(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("Replay.ui");

        renderList(uiCommandBuilder);

        RecordingData recordingData = recorder.getRecordingData(playerRef);
        if (recordingData != null) {
            recording = true;
            uiCommandBuilder.set("#Record.Text", Message.translation("replay.stopRecording"));

            Duration duration = recordingData.start.until(Instant.now());
            String formatted = String.format("%02d:%02d", duration.toMinutesPart(), duration.toSecondsPart());
            if (duration.toHoursPart() > 0) {
                formatted = String.format("%d:%s", duration.toHours(), formatted);
            }

            uiCommandBuilder.set(
                    "#Time.Text", Message.translation("replay.recording").getAnsiMessage() + " " + formatted
            );
        }
    }

    private void renderList(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.clear("#List");
        List<Path> replays = replayRepository.getReplays(playerRef);
        for (int i = 0; i < replays.size(); i++) {
            Path replay = replays.get(i);
            uiCommandBuilder.append("#List", "ReplayEntry.ui");

            String name = replay.getFileName().toString();
            if (name.endsWith(ReplayRepository.REPLAY_EXTENSION)) {
                name = name.substring(0, name.length() - ReplayRepository.REPLAY_EXTENSION.length());
            }
            uiCommandBuilder.set("#List[" + i + "][0].Text", name);
        }

        renderEmptyMessage(uiCommandBuilder, replays);
    }

    private void renderEmptyMessage(@Nonnull UICommandBuilder uiCommandBuilder, @Nonnull List<Path> replays) {
        if (replays.isEmpty()) {
            uiCommandBuilder.appendInline("#List", """
                    Label {
                      Text: %replay.noReplaysYet;
                      Style: (FontSize: 16, Alignment: Center);
                    }
                    """);
        }
    }

    @Override
    public void register(@Nonnull UIEventBuilder uiEventBuilder, @Nonnull UIEventHandler<Data> eventHandler) {
        eventHandler.handle(CustomUIEventBindingType.Activating,
                "#Record",
                this::onRecord
        );

        registerListEvents(eventHandler);
    }

    private void registerListEvents(@Nonnull UIEventHandler<Data> eventHandler) {
        List<Path> replays = replayRepository.getReplays(playerRef);
        for (int i = 0; i < replays.size(); i++) {
            Path replay = replays.get(i);
            eventHandler.handle(CustomUIEventBindingType.Activating,
                    "#List[" + i + "][0]",
                    null,
                    context -> onReplay(context, replay),
                    true
            );
            eventHandler.handle(CustomUIEventBindingType.Activating,
                    "#List[" + i + "][1]",
                    null,
                    context -> onDeleteReplay(context, replay),
                    true
            );
        }
    }

    private void onReplay(@Nonnull UIEventContext<Data> context, @Nonnull Path replay) {
        ReplayPlugin.get().startReplaying(playerRef, replay);
        context.close();
    }

    private void onDeleteReplay(@Nonnull UIEventContext<Data> context, @Nonnull Path replay) {
        replayRepository.deleteReplay(replay);
        renderList(context.uiCommandBuilder);
        registerListEvents(context.uiEventHandler);
    }

    private void onRecord(@Nonnull UIEventContext<Data> context) {
        if (!recording) {
            context.close();
        }

        Ref<EntityStore> ref = context.playerRef.getReference();
        assert ref != null;
        Store<EntityStore> store = ref.getStore();

        store.getExternalData().getWorld().execute(() -> {
            if (recording) {
                ReplayPlugin.get().stopRecording(playerRef);
            } else {
                ReplayPlugin.get().startRecording(playerRef);
            }
        });
    }

}
