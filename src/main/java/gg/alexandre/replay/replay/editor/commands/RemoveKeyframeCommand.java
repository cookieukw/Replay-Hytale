package gg.alexandre.replay.replay.editor.commands;

import gg.alexandre.replay.replay.editor.properties.base.BaseProperty;
import gg.alexandre.replay.replay.state.ReplayState;

import javax.annotation.Nonnull;

public class RemoveKeyframeCommand extends CommandBase {

    private final String propertyId;
    private final int tick;

    private Object value;

    public RemoveKeyframeCommand(@Nonnull ReplayState state, @Nonnull String propertyId, int tick) {
        super("removeKeyframe", state);

        this.propertyId = propertyId;
        this.tick = tick;
    }

    @Override
    public void execute() {
        BaseProperty<?> property = state.timeline.getProperties().get(propertyId);
        if (property == null) {
            return;
        }
        value = property.getValues().remove(tick);
    }

    @Override
    public void undo() {
        if (value != null) {
            BaseProperty<?> property = state.timeline.getProperties().get(propertyId);
            if (property != null) {
                restoreKeyframeGeneric(property, tick, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void restoreKeyframeGeneric(BaseProperty<T> property, int tick, Object value) {
        property.getValues().put(tick, (T) value);
    }

}
