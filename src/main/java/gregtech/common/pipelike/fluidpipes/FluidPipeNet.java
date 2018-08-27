package gregtech.common.pipelike.fluidpipes;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import gregtech.api.pipelike.ITilePipeLike;
import gregtech.api.worldentries.pipenet.PipeNet;
import gregtech.api.worldentries.pipenet.WorldPipeNet;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;
import java.util.*;

public class FluidPipeNet extends PipeNet<TypeFluidPipe, FluidPipeProperties, IFluidHandler> implements ITickable {
    public static final int TICK_RATE = 10;

    private Map<BlockPos, BufferTank> bufferTanks = Maps.newHashMap();

    public FluidPipeNet(WorldPipeNet worldNet) {
        super(FluidPipeFactory.INSTANCE, worldNet);
    }

    @Override
    protected void transferNodeDataTo(Collection<? extends BlockPos> nodeToTransfer, PipeNet<TypeFluidPipe, FluidPipeProperties, IFluidHandler> toNet) {
        FluidPipeNet net = (FluidPipeNet) toNet;
        nodeToTransfer.forEach(node -> {
            if (bufferTanks.containsKey(node)) net.bufferTanks.put(node, bufferTanks.get(node));
        });
    }

    @Override
    protected void removeData(BlockPos pos) {
        bufferTanks.remove(pos);
    }

    public static final IFluidTankProperties[] EMPTY = new IFluidTankProperties[0];

    public IFluidTankProperties[] getTankProperties(BlockPos pos, EnumFacing facing) {
        BufferTank buf = bufferTanks.get(pos);
        return buf == null ? EMPTY : new IFluidTankProperties[]{new PropertyWrapper(buf, facing)};
    }

    public int fill(BlockPos pos, EnumFacing fromDir, FluidStack stack, boolean doFill) {
        if (!allNodes.containsKey(pos)) return 0;
        BufferTank tank = getOrCreateBufferTank(pos);
        int filled = tank.fill(stack, fromDir, doFill);
        if (doFill && filled > 0) {
            FluidEvent.fireEvent(new FluidEvent.FluidFillingEvent(new FluidStack(stack, filled), worldNets.getWorld(), pos, tank, filled));
            worldNets.markDirty();
        }
        return filled;
    }

    public FluidStack drain(BlockPos pos, EnumFacing fromDir, FluidStack stack, boolean doDrain) {
        return drain(pos, fromDir, stack, stack.amount, doDrain);
    }

    public FluidStack drain(BlockPos pos, EnumFacing fromDir, int amount, boolean doDrain) {
        return drain(pos, fromDir, null, amount, doDrain);
    }

    private FluidStack drain(BlockPos pos, EnumFacing fromDir, FluidStack stack, int amount, boolean doDrain) {
        if (!bufferTanks.containsKey(pos)) return null;
        BufferTank tank = bufferTanks.get(pos);
        if (tank == null || tank.getFluidAmount() <= 0 || (stack != null && !stack.isFluidEqual(tank.bufferedStack))) return null;
        FluidStack drained = tank.drain(amount, doDrain);
        if (doDrain && drained != null && drained.amount > 0) {
            FluidEvent.fireEvent(new FluidEvent.FluidDrainingEvent(drained.copy(), worldNets.getWorld(), pos, tank, drained.amount));
            worldNets.markDirty();
        }
        return drained;
    }

    @Override
    protected void serializeNodeData(BlockPos pos, NBTTagCompound nodeTag) {
        BufferTank tank = bufferTanks.get(pos);
        if (tank != null && !tank.isEmpty()) {
            nodeTag.setTag("BufferTank", tank.serializeNBT());
        }
    }

    @Override
    protected void deserializeNodeData(BlockPos pos, NBTTagCompound nodeTag) {
        if (nodeTag.hasKey("BufferTank")) {
            BufferTank tank = new BufferTank(allNodes.get(pos).property.getFluidCapacity());
            tank.deserializeNBT(nodeTag.getCompoundTag("BufferTank"));
            bufferTanks.put(pos, tank);
        }
    }

    private BufferTank getOrCreateBufferTank(BlockPos pos) {
        return bufferTanks.computeIfAbsent(pos.toImmutable(), p -> new BufferTank(allNodes.get(p).property.getFluidCapacity()));
    }

    @Override
    public void update() {
        World world = worldNets.getWorld();
        if (!bufferTanks.isEmpty()) {
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
            Table<BlockPos, BufferTank, FluidStack> snapshots = HashBasedTable.create();
            bufferTanks.forEach((p, tank) -> {
                if (tank.canFluidMove()) snapshots.put(p, tank, tank.bufferedStack);
            });
            EnumMap<EnumFacing, Object> sideTanks = new EnumMap<>(EnumFacing.class);
            for (Table.Cell<BlockPos, BufferTank, FluidStack> cell : snapshots.cellSet()) {
                BufferTank tank = cell.getColumnKey();
                pos.setPos(cell.getRowKey());
                Node<FluidPipeProperties> node = allNodes.get(pos);
                int activeMask = node.getActiveMask();
                FluidStack fluidSnapshot = cell.getValue();
                if (!fluidSnapshot.isFluidEqual(tank.bufferedStack)) continue;
                sideTanks.clear();
                ITilePipeLike<TypeFluidPipe, FluidPipeProperties> pipe = factory.getTile(world, pos);
                for (EnumFacing facing : EnumFacing.VALUES) if (tank.isFacingValid(facing)) {
                    if ((activeMask & 1 << facing.getIndex()) != 0) {
                        IFluidHandler handler = pipe.getCapabilityAtSide(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
                        if (handler != null) sideTanks.put(facing, handler);
                    } else {
                        if (allNodes.containsKey(pos.move(facing)) && PipeNet.<FluidPipeProperties>adjacent().test(node, facing, allNodes.get(pos))) {
                            sideTanks.put(facing, getOrCreateBufferTank(pos));
                        }
                        pos.move(facing.getOpposite());
                    }
                }

                if (!sideTanks.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map.Entry<EnumFacing, Object>[] entries = sideTanks.entrySet().toArray(new Map.Entry[0]);
                    int size = entries.length;
                    int amount = Math.min(fluidSnapshot.amount, tank.bufferedStack.amount);
                    int quotient = amount / size;
                    int remainder = amount % size;
                    int filled = 0;

                    int[] indices = new int[size];
                    for (int i = 0; i < size; i++) indices[i] = i;
                    for (int i = size, r; i > 0; indices[r] = indices[--i]) {
                        r = getRandom().nextInt(i);
                        Map.Entry<EnumFacing, Object> e = entries[indices[r]];
                        Object sideTank = e.getValue();
                        FluidStack toFill = new FluidStack(fluidSnapshot, quotient + (remainder --> 0 ? 1 : 0));
                        if (sideTank instanceof IFluidHandler) {
                            filled += ((IFluidHandler) sideTank).fill(toFill, true);
                        } else if (sideTank instanceof BufferTank) {
                            filled += ((BufferTank) sideTank).fill(toFill, e.getKey(), true);
                        }
                    }
                    tank.bufferedStack.amount -= filled;
                }
            }

            if (world.getTotalWorldTime() % 5 == 0) {
                Map<BlockPos, Integer> burnt = Maps.newHashMap();
                bufferTanks.forEach((p, tank) -> {
                    List<FluidStack> leakedStacks = Lists.newArrayList();
                    FluidPipeProperties properties = allNodes.get(p).property;
                    if (!tank.isEmpty()) {
                        if (!properties.isGasProof() && tank.bufferedStack.getFluid().isGaseous(tank.bufferedStack)) {
                            int leaked = Math.min(tank.capacity / 50, tank.bufferedStack.amount);
                            tank.bufferedStack.amount -= leaked;
                            leakedStacks.add(new FluidStack(tank.bufferedStack, leaked));
                        }
                        int tempDiff = tank.bufferedStack.getFluid().getTemperature(tank.bufferedStack) - properties.getHeatLimit();
                        if (tempDiff > 0) {
                            burnt.compute(p, (q, diff) -> diff == null || tempDiff > diff ? tempDiff : diff);
                        }
                    }
                    if (!leakedStacks.isEmpty()) {
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                            p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                            8 + world.rand.nextInt(3), 0.0, 0.0, 0.0, 0.1);
                        //TODO sound
                        for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(p).grow(4.0))) {
                            FluidPipeFactory.applyGasLeakingDamage(entity, leakedStacks);
                        }
                    }
                });
                burnt.forEach((p, tempDiff) -> {
                    int chance = Math.max(1, 100 * 50 / tempDiff);
                    for (EnumFacing facing : EnumFacing.VALUES) if (worldNets.getWorld().rand.nextInt(chance) < 15) {
                        pos.setPos(p).move(facing);
                        if (world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
                            world.setBlockToAir(pos);
                            world.setBlockState(pos, Blocks.FIRE.getDefaultState());
                        }
                    }
                    if (worldNets.getWorld().rand.nextInt(chance) == 0) {
                        world.setBlockToAir(p);
                        world.setBlockState(p, Blocks.FIRE.getDefaultState());
                    }
                });
            }
            pos.release();

            for (Iterator<BufferTank> itr = bufferTanks.values().iterator(); itr.hasNext();) {
                BufferTank tank = itr.next();
                tank.tick();
                if (tank.isEmpty()) {
                    itr.remove();
                }
            }
            worldNets.markDirty();
        }
    }

    static class BufferTank implements IFluidTank, INBTSerializable<NBTTagCompound> {
        int capacity;
        FluidStack bufferedStack;
        int moveCountDown = 0;
        int dirCountDown[] = new int[6];
        FluidTankInfo info;

        BufferTank(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            if (bufferedStack != null) bufferedStack.writeToNBT(tag);
            tag.setInteger("MovingCountDown", moveCountDown);
            tag.setIntArray("DirCountDown", dirCountDown);
            return tag;
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            bufferedStack = FluidStack.loadFluidStackFromNBT(nbt);
            moveCountDown = nbt.getInteger("MovingCountDown");
            dirCountDown = nbt.getIntArray("DirCountDown");
        }

        void setCountDown(EnumFacing fromDir, boolean lock) {
            int countDown = bufferedStack == null ? 0 : Math.max(1, FluidPipeNet.TICK_RATE * (1000 + bufferedStack.getFluid().getViscosity(bufferedStack)) / 2000);
            if (lock) this.moveCountDown = countDown;
            if (fromDir != null) this.dirCountDown[fromDir.getOpposite().getIndex()] = countDown * 8;
        }

        boolean isEmpty() {
            return bufferedStack == null || bufferedStack.amount <= 0;
        }

        void tick() {
            if (moveCountDown > 0) moveCountDown--;
            for (int i = 0; i < 6; i++) {
                if (dirCountDown[i] > 0) dirCountDown[i]--;
            }
        }

        boolean canFluidMove() {
            return bufferedStack != null && bufferedStack.amount > 0 && moveCountDown == 0;
        }

        boolean isFacingValid(EnumFacing facing) {
            return facing == null || dirCountDown[facing.getIndex()] <= 0;
        }

        int fill(FluidStack stack, EnumFacing fromDir, boolean doFill) {
            if (bufferedStack == null || bufferedStack.isFluidEqual(stack)) {
                int filled = Math.min(stack.amount, capacity - (bufferedStack == null ? 0 : bufferedStack.amount));
                if (doFill) {
                    if (bufferedStack == null) {
                        bufferedStack = new FluidStack(stack, filled);
                        setCountDown(fromDir, true);
                    } else {
                        bufferedStack.amount += filled;
                        setCountDown(fromDir, false);
                    }
                }
                return filled;
            }
            return 0;
        }

        @Nullable
        @Override
        public FluidStack getFluid() {
            return bufferedStack;
        }

        @Override
        public int getFluidAmount() {
            return bufferedStack == null ? 0 : bufferedStack.amount;
        }

        @Override
        public int getCapacity() {
            return capacity;
        }

        @Override
        public FluidTankInfo getInfo() {
            return info == null ? (info = new FluidTankInfo(this)) : info;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return fill(resource, null, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (bufferedStack == null) return null;
            FluidStack drained = new FluidStack(bufferedStack, Math.min(maxDrain, bufferedStack.amount));
            if (doDrain) {
                bufferedStack.amount -= drained.amount;
                if (bufferedStack.amount <= 0) bufferedStack = null;
            }
            return drained;
        }
    }

    static class PropertyWrapper implements IFluidTankProperties {

        BufferTank bufferTank;
        EnumFacing dir;

        PropertyWrapper(BufferTank bufferTank, EnumFacing dir) {
            this.bufferTank = bufferTank;
        }

        @Nullable
        @Override
        public FluidStack getContents() {
            return bufferTank.bufferedStack;
        }

        @Override
        public int getCapacity() {
            return bufferTank.capacity;
        }

        @Override
        public boolean canFill() {
            return true;
        }

        @Override
        public boolean canDrain() {
            return true;
        }

        @Override
        public boolean canFillFluidType(FluidStack fluidStack) {
            return bufferTank.fill(fluidStack, dir, false) > 0;
        }

        @Override
        public boolean canDrainFluidType(FluidStack fluidStack) {
            return bufferTank.bufferedStack != null && bufferTank.bufferedStack.isFluidEqual(fluidStack);
        }
    }
}
