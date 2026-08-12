package gg.alexandre.replay.replay.editor.properties;

import com.hypixel.hytale.server.core.entity.entities.Player;
import gg.alexandre.replay.replay.BasePlayer;
import gg.alexandre.replay.replay.editor.interpolation.InterpolationUtil;
import gg.alexandre.replay.replay.editor.properties.base.BaseProperty;
import gg.alexandre.replay.replay.state.ReplayState;
import gg.alexandre.replay.replay.state.UIState;
import gg.alexandre.replay.ui.event.UIEventContext;
import gg.alexandre.replay.util.Position;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class CameraProperty extends BaseProperty<Position> {

    public CameraProperty() {
        super(new Position(0, 0, 0, 0, 0));
    }

    @Override
    public void handle(@Nonnull ReplayState state, double tick) {
        Position cameraPosition = getValue(tick);
        if (cameraPosition == null) {
            return;
        }

        state.edit.cameraPosition = cameraPosition;
    }

    @Nullable
    @Override
    public Position getValue(double tick) {
        int intTick = (int) Math.floor(tick);

        Map.Entry<Integer, Position> previous = getValues().floorEntry(intTick);
        if (previous == null) {
            return null;
        }

        Map.Entry<Integer, Position> next = getValues().higherEntry(intTick);

        Map.Entry<Integer, Position> p0Entry = getValues().lowerEntry(previous.getKey());
        Map.Entry<Integer, Position> p3Entry = getValues().higherEntry(next != null ? next.getKey() : intTick);

        if (next == null) {
            return previous.getValue();
        } else {
            int previousTick = previous.getKey();
            int nextTick = next.getKey();
            Position p0 = p0Entry != null ? p0Entry.getValue() : previous.getValue();
            Position p1 = previous.getValue();
            Position p2 = next.getValue();
            Position p3 = p3Entry != null ? p3Entry.getValue() : next.getValue();

            double ratio = (tick - previousTick) / (nextTick - previousTick);

            double p0Yaw = unwrapRelative(p0.yaw(), p1.yaw());
            double p1Yaw = p1.yaw();
            double p2Yaw = unwrapRelative(p2.yaw(), p1Yaw);
            double p3Yaw = unwrapRelative(p3.yaw(), p2Yaw);

            double p0Pitch = unwrapRelative(p0.pitch(), p1.pitch());
            double p1Pitch = p1.pitch();
            double p2Pitch = unwrapRelative(p2.pitch(), p1Pitch);
            double p3Pitch = unwrapRelative(p3.pitch(), p2Pitch);

            Vector3d v0 = new Vector3d(p0.x(), p0.y(), p0.z());
            Vector3d v1 = new Vector3d(p1.x(), p1.y(), p1.z());
            Vector3d v2 = new Vector3d(p2.x(), p2.y(), p2.z());
            Vector3d v3 = new Vector3d(p3.x(), p3.y(), p3.z());

            double alpha = 0.5; // Centripetal parameter
            double t0 = 0.0;
            double t1 = t0 + Math.pow(v0.distance(v1), alpha);
            double t2 = t1 + Math.pow(v1.distance(v2), alpha);
            double t3 = t2 + Math.pow(v2.distance(v3), alpha);

            Vector3d pos = InterpolationUtil.catmullRomCentripetal(v0, v1, v2, v3, t0, t1, t2, t3, ratio);

            double yaw = InterpolationUtil.catmullRomCentripetal(p0Yaw, p1Yaw, p2Yaw, p3Yaw, t0, t1, t2, t3, ratio);
            double pitch = InterpolationUtil.catmullRomCentripetal(p0Pitch, p1Pitch, p2Pitch, p3Pitch, t0, t1, t2, t3, ratio);

            yaw = normalizeAngle(yaw);
            pitch = normalizeAngle(pitch);

            return new Position(pos.x, pos.y, pos.z, yaw, pitch);
        }
    }

    private double unwrapRelative(double angle, double reference) {
        double diff = angle - reference;

        while (diff > Math.PI) {
            angle -= 2.0 * Math.PI;
            diff -= 2.0 * Math.PI;
        }

        while (diff < -Math.PI) {
            angle += 2.0 * Math.PI;
            diff += 2.0 * Math.PI;
        }

        return angle;
    }

    private double normalizeAngle(double angle) {
        while (angle <= -Math.PI) {
            angle += 2.0 * Math.PI;
        }
        while (angle > Math.PI) {
            angle -= 2.0 * Math.PI;
        }
        return angle;
    }

    @Override
    public void onClick(@Nonnull BasePlayer player, @Nonnull ReplayState state,
                        @Nonnull Player playerComponent, @Nonnull UIEventContext<?> context, int tick) {
        handle(state, tick);
        state.cameraManager.moveCamera(state, context.playerRef, true);
    }

    @Override
    public void editKeyframe(@Nonnull BasePlayer player, @Nonnull ReplayState state,
                             @Nonnull Player playerComponent, @Nonnull UIEventContext<?> context, int tick) {
        state.overlay.clearImmediately(context.playerRef);

        onClick(player, state, playerComponent, context, tick);

        state.stage.isPlaying = false;
        state.ui.controlGame = true;
        state.ui.editingCamera = true;
        state.ui.selectedKeyframe = new UIState.Keyframe(id(), tick);
        context.close();
    }

    @Nonnull
    @Override
    public String id() {
        return "camera";
    }

    @Nonnull
    @Override
    public Position getDefaultValue(@Nonnull ReplayState state) {
        return state.edit.cameraPosition;
    }
}