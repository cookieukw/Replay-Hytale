package gg.alexandre.replay.replay.editor.commands;

import gg.alexandre.replay.replay.editor.properties.base.BaseProperty;
import gg.alexandre.replay.replay.state.ReplayState;

import javax.annotation.Nonnull;

public class AddKeyframeCommand extends CommandBase {

    private final String propertyId;
    private final int tick;
    private final Object value;
    private Object previousValue;

    public AddKeyframeCommand(@Nonnull ReplayState state, @Nonnull String propertyId, int tick, @Nonnull Object value) {
        super("addKeyframe", state);

        this.propertyId = propertyId;
        this.tick = tick;
        this.value = value;
    }

    @Override
    public void execute() {
        BaseProperty<?> property = state.timeline.getProperties().get(propertyId);
        if (property != null) {
            previousValue = putKeyframeGeneric(property, tick, value);
        }
    }

    @Override
    public void undo() {
        BaseProperty<?> property = state.timeline.getProperties().get(propertyId);
        if (property == null) {
            return;
        }
        
        if (previousValue != null) {
            putKeyframeGeneric(property, tick, previousValue);
        } else {
            property.getValues().remove(tick);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Object putKeyframeGeneric(BaseProperty<T> property, int tick, Object value) {
        return property.getValues().put(tick, (T) value);
    }

}
