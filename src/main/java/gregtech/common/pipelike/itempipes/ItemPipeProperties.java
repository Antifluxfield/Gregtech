package gregtech.common.pipelike.itempipes;

import gregtech.api.pipelike.IPipeLikeTileProperty;
import gregtech.api.util.GTUtility;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Objects;

public class ItemPipeProperties implements IPipeLikeTileProperty {

    private int transferCapacity;
    private int tickRate;
    private int routingValue;

    /**
     * Create an empty property for nbt deserialization
     */
    protected ItemPipeProperties() {}

    public ItemPipeProperties(int transferCapacity, int tickRate, int routingValue) {
        int d = (int) GTUtility.gcd(transferCapacity, tickRate);
        this.transferCapacity = transferCapacity / d;
        this.tickRate = tickRate / d;
        this.routingValue = routingValue;
    }

    public int getTransferCapacity() {
        return transferCapacity;
    }

    public int getTickRate() {
        return tickRate;
    }

    public int getRoutingValue() {
        return routingValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ItemPipeProperties)) return false;
        ItemPipeProperties that = (ItemPipeProperties) obj;
        return that.tickRate == this.tickRate
            && that.transferCapacity == this.transferCapacity
            && that.routingValue == this.routingValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferCapacity, tickRate, routingValue);
    }

    @Override
    public void addInformation(List<String> tooltip) {
        tooltip.add(tickRate != 1 ? I18n.format("gregtech.item_pipe.capacity1", transferCapacity, tickRate)
            : transferCapacity != 1 ? I18n.format("gregtech.item_pipe.capacity2", transferCapacity)
            : I18n.format("gregtech.item_pipe.capacity3"));
        tooltip.add(I18n.format("gregtech.item_pipe.routing_value", routingValue));
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("ItemPipeCapacity", transferCapacity);
        nbt.setInteger("ItemPipeTickRate", tickRate);
        nbt.setInteger("ItemPipeRoutingValue", routingValue);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        transferCapacity = nbt.getInteger("ItemPipeCapacity");
        tickRate = nbt.getInteger("ItemPipeTickRate");
        routingValue = nbt.getInteger("ItemPipeRoutingValue");
    }
}
