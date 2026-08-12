package gg.alexandre.replay.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import gg.alexandre.replay.ui.codec.CodecConstructor;
import gg.alexandre.replay.ui.codec.UIKey;
import gg.alexandre.replay.ui.event.UIEventContext;
import gg.alexandre.replay.ui.event.UIEventHandler;
import gg.alexandre.replay.ui.event.UIEventIdData;

import javax.annotation.Nonnull;

public class BoneEditorUI extends BaseUI<BoneEditorUI.Data> {

    private static final BuilderCodec<Data> CODEC = CodecConstructor.create(Data.class, Data::new);

    public static class Data extends UIEventIdData {
        @UIKey("@EntityId")
        private String entityId;
        
        @UIKey("@BoneName")
        private String boneName;
        
        @UIKey("@Pitch")
        private float pitch;
        
        @UIKey("@Yaw")
        private float yaw;
        
        @UIKey("@Roll")
        private float roll;
        
        public long getEntityIdParsed() {
            try {
                return Long.parseLong(entityId);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        
        public String getBoneName() {
            return boneName;
        }

        public float getPitch() { return pitch; }
        public float getYaw() { return yaw; }
        public float getRoll() { return roll; }
    }

    public interface SaveCallback {
        void onSave(long entityId, String boneName, float pitch, float yaw, float roll);
    }

    private final SaveCallback onSave;

    public BoneEditorUI(@Nonnull PlayerRef playerRef, @Nonnull SaveCallback onSave) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, CODEC);
        this.onSave = onSave;
    }

    @Override
    public void init(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("BoneEditor.ui");

        uiCommandBuilder.set("#PitchSlider.Min", -180);
        uiCommandBuilder.set("#PitchSlider.Max", 180);
        uiCommandBuilder.set("#PitchSlider.Value", 0);

        uiCommandBuilder.set("#YawSlider.Min", -180);
        uiCommandBuilder.set("#YawSlider.Max", 180);
        uiCommandBuilder.set("#YawSlider.Value", 0);

        uiCommandBuilder.set("#RollSlider.Min", -180);
        uiCommandBuilder.set("#RollSlider.Max", 180);
        uiCommandBuilder.set("#RollSlider.Value", 0);
        
        uiCommandBuilder.set("#Title.Text", "Edit Bone Keyframe");
    }

    @Override
    public void register(@Nonnull UIEventBuilder uiEventBuilder, @Nonnull UIEventHandler<Data> eventHandler) {
        eventHandler.handle(CustomUIEventBindingType.Activating,
                "#CloseButton",
                this::onClose
        );

        eventHandler.handle(CustomUIEventBindingType.Activating,
                "#Cancel",
                this::onClose
        );

        eventHandler.handle(
                CustomUIEventBindingType.Activating,
                "#Save",
                (data) -> {
                    data.append("@EntityId", "#EntityIdInput.Text");
                    data.append("@BoneName", "#BoneNameInput.Text");
                    data.append("@Pitch", "#PitchSlider.Value");
                    data.append("@Yaw", "#YawSlider.Value");
                    data.append("@Roll", "#RollSlider.Value");
                },
                this::onSave
        );
    }

    private void onClose(@Nonnull UIEventContext<Data> context) {
        context.close();
    }

    private void onSave(@Nonnull UIEventContext<Data> context) {
        long entId = context.data.getEntityIdParsed();
        String bName = context.data.getBoneName();
        if (entId != -1 && bName != null && !bName.isEmpty()) {
            onSave.onSave(entId, bName, context.data.getPitch(), context.data.getYaw(), context.data.getRoll());
        }
        context.close();
    }

}
