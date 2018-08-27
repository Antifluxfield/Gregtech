package gregtech.common.pipelike.fluidpipes;

import gregtech.api.pipelike.IPipeLikeTileProperty;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Objects;

public class FluidPipeProperties implements IPipeLikeTileProperty {

    private int fluidCapacity;
    private int heatLimit;
    private boolean isGasProof;

    /**
     * Create an empty property for nbt deserialization
     */
    protected FluidPipeProperties() {}

    public FluidPipeProperties(int fluidCapacity, int heatLimit, boolean isGasProof) {
        this.fluidCapacity = fluidCapacity;
        this.heatLimit = heatLimit;
        this.isGasProof = isGasProof;
    }

    public int getFluidCapacity() {
        return fluidCapacity;
    }

    public int getHeatLimit() {
        return heatLimit;
    }

    public boolean isGasProof() {
        return isGasProof;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof FluidPipeProperties)) return false;
        FluidPipeProperties that = (FluidPipeProperties) obj;
        return this.fluidCapacity == that.fluidCapacity
            && this.heatLimit == that.heatLimit
            && this.isGasProof == that.isGasProof;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluidCapacity, heatLimit, isGasProof);
    }

    @Override
    public void addInformation(List<String> tooltip) {
        tooltip.add(I18n.format("gregtech.fluid_pipe.capacity", fluidCapacity));
        tooltip.add(I18n.format("gregtech.fluid_pipe.heat_limit", heatLimit));
        if (isGasProof) tooltip.add(I18n.format("gregtech.fluid_pipe.gas_proof"));
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("FluidPipeCapacity", fluidCapacity);
        nbt.setInteger("FluidPipeHeatLimit", heatLimit);
        nbt.setBoolean("IsFluidPipeGasProof", isGasProof);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        fluidCapacity = nbt.getInteger("FluidPipeCapacity");
        heatLimit = nbt.getInteger("FluidPipeHeatLimit");
        isGasProof = nbt.getBoolean("IsFluidPipeGasProof");
    }
}
