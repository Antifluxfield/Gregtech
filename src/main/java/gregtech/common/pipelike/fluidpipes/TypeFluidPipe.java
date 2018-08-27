package gregtech.common.pipelike.fluidpipes;

import gregtech.api.pipelike.IBaseProperty;
import gregtech.api.unification.ore.OrePrefix;
import net.minecraft.util.IStringSerializable;

import static gregtech.api.unification.ore.OrePrefix.*;

public enum TypeFluidPipe implements IBaseProperty, IStringSerializable {
    PIPE_TINY("fluid_pipe_tiny", 1, pipeTiny, 0.25F, 0),
    PIPE_SMALL("fluid_pipe_small", 2, pipeSmall, 0.375F, 1),
    PIPE_MEDIUM("fluid_pipe_medium", 6, pipeMedium, 0.5F, 2),
    PIPE_LARGE("fluid_pipe_large", 12, pipeLarge, 0.75F, 3),
    PIPE_HUGE("fluid_pipe_huge", 24, pipeHuge, 0.875F, 4);

    public final String name;
    public final OrePrefix orePrefix;
    public final float thickness;
    public final int index;
    public final int fluidCapacityMultiplier;

    TypeFluidPipe(String name, int fluidCapacityMultiplier, OrePrefix orePrefix, float thickness, int index) {
        this.name = name;
        this.fluidCapacityMultiplier = fluidCapacityMultiplier;
        this.orePrefix = orePrefix;
        this.thickness = thickness;
        this.index = index;
    }

    @Override
    public OrePrefix getOrePrefix() {
        return orePrefix;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public boolean isColorable() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }
}
