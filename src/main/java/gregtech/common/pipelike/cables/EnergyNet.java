package gregtech.common.pipelike.cables;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.worldentries.pipenet.PipeNet;
import gregtech.api.worldentries.pipenet.RoutePath;
import gregtech.api.worldentries.pipenet.WorldPipeNet;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnergyNet extends PipeNet<Insulation, WireProperties, IEnergyContainer> {

    private Map<BlockPos, EnergyPacket> passingPackets = Maps.newHashMap();
    private Map<BlockPos, Statistics> statistics = Maps.newHashMap();

    EnergyNet(WorldPipeNet worldNet) {
        super(CableFactory.INSTANCE, worldNet);
    }

    @Override
    protected void transferNodeDataTo(Collection<? extends BlockPos> nodeToTransfer, PipeNet<Insulation, WireProperties, IEnergyContainer> toNet) {
        EnergyNet net = (EnergyNet) toNet;
        for (BlockPos pos : nodeToTransfer) {
            if (passingPackets.containsKey(pos)) net.passingPackets.put(pos, passingPackets.get(pos));
            if (statistics.containsKey(pos)) net.statistics.put(pos, statistics.get(pos));
        }
    }

    @Override
    protected void removeData(BlockPos pos) {
        passingPackets.remove(pos);
        statistics.remove(pos);
    }

    public long acceptEnergy(CableEnergyContainer energyContainer, long voltage, long amperage, EnumFacing ignoredFacing) {
        if (energyContainer.pathsCache == null || energyContainer.lastCachedPathsTime < lastUpdate) {
            energyContainer.lastCachedPathsTime = lastUpdate;
            energyContainer.lastWeakUpdate = lastWeakUpdate;
            energyContainer.pathsCache = computeRoutePaths(energyContainer.tileEntityCable.getTilePos(), checkConnectionMask(),
                Collectors.summingLong(node -> node.property.getLossPerBlock()), null);
        } else if (energyContainer.lastWeakUpdate < lastWeakUpdate) {
            energyContainer.lastWeakUpdate = lastWeakUpdate;
            updateNodeChain(energyContainer.pathsCache);
        }
        long amperesUsed = 0L;
        for (RoutePath<WireProperties, ?, Long> path : energyContainer.pathsCache) {
            if (path.getAccumulatedVal() >= voltage) continue; //do not emit if loss is too high
            amperesUsed += dispatchEnergyToNode(path, voltage, amperage - amperesUsed, ignoredFacing);
            if (amperesUsed == amperage) break; //do not continue if all amperes are exhausted
        }
        return amperesUsed;
    }

    public long dispatchEnergyToNode(RoutePath<WireProperties, ?, Long> path, long voltage, long amperage, EnumFacing ignoredFacing) {
        long amperesUsed = 0L;
        Node<WireProperties> destination = path.getEndNode();
        int tileMask = destination.getActiveMask();
        if (ignoredFacing != null && destination.equals(path.getStartNode())) tileMask &= ~(1 << ignoredFacing.getIndex());
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
        World world = worldNets.getWorld();
        int[] indices = {0, 1, 2, 3, 4, 5};
        for (int i = 6, r; i > 0 && amperesUsed < amperage; indices[r] = indices[--i]) { // shuffle & traverse
            r = worldNets.getWorld().rand.nextInt(i);
            EnumFacing facing = EnumFacing.VALUES[indices[r]];
            if (0 != (tileMask & 1 << facing.getIndex())) {
                pos.setPos(destination).move(facing);
                if (!world.isBlockLoaded(pos)) continue; //do not allow cables to load chunks
                TileEntity tile = world.getTileEntity(pos);
                if (tile == null) continue;
                IEnergyContainer energyContainer = tile.getCapability(IEnergyContainer.CAPABILITY_ENERGY_CONTAINER, facing.getOpposite());
                if (energyContainer == null) continue;
                amperesUsed += onEnergyPacket(path, voltage,
                    energyContainer.acceptEnergyFromNetwork(facing.getOpposite(), voltage - path.getAccumulatedVal(), amperage - amperesUsed));
            }
        }
        pos.release();
        return amperesUsed;
    }

    private long onEnergyPacket(RoutePath<WireProperties, ?, Long> path, long voltage, long amperage) {
        for (Node<WireProperties> node : path) {
            onEnergyPacket(node, voltage, amperage);
            voltage -= node.property.getLossPerBlock();
        }
        return amperage;
    }

    private void onEnergyPacket(Node<WireProperties> node, long voltage, long amperage) {
        WireProperties prop = node.property;
        long timer = getTickTimer();
        long amp = passingPackets.compute(node, (pos, ePacket) -> {
            if (ePacket == null || ePacket.lastTickTime < timer) {
                EnergyPacket packet = EnergyPacket.create(amperage, voltage, timer);
                Statistics stat = getStatistic(node);
                if (stat != null) stat.addData(packet, timer);
                return packet;
            }
            return ePacket.accumulate(amperage, voltage);
        }).amperage;
        if (voltage > prop.getVoltage() || amp > prop.getAmperage()) burnCable(node);
    }

    private void burnCable(BlockPos pos) {
        World world = worldNets.getWorld();
        world.setBlockToAir(pos);
        world.setBlockState(pos, Blocks.FIRE.getDefaultState());
        ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            5 + world.rand.nextInt(3), 0.0, 0.0, 0.0, 0.1);
    }

    private Statistics getStatistic(BlockPos pos) {
        Statistics stat = statistics.get(pos);
        if (stat != null && getTickTimer() - stat.lastTickTime > STATISTIC_COUNT * 5) {
            statistics.remove(pos);
            stat = null;
        }
        return stat;
    }

    // amperage, energy
    public double[] getStatisticData(BlockPos pos) {
        long timer = getTickTimer();
        return statistics.computeIfAbsent(pos, p -> new Statistics(timer)).getData(timer);
    }

    public static final int STATISTIC_COUNT = 20;

    public static class EnergyPacket {
        public long amperage;
        public double energy;
        public long lastTickTime;

        EnergyPacket(long amperage, double energy, long timer) {
            this.amperage = amperage;
            this.energy = energy;
            this.lastTickTime = timer;
        }

        static EnergyPacket create(long amperage, long voltage, long timer) {
            return new EnergyPacket(amperage, (double) amperage * (double) voltage, timer);
        }

        EnergyPacket accumulate(long amperage, long voltage) {
            this.amperage += amperage;
            this.energy += (double) amperage * (double) voltage;
            return this;
        }
    }

    public static final double[] NO_DATA = {0.0, 0.0};

    static class Statistics {
        List<EnergyPacket> bufferedPackets = Lists.newArrayListWithCapacity(STATISTIC_COUNT);
        long lastTickTime;

        Statistics(long tickCounter) {
            this.lastTickTime = tickCounter;
        }

        EnergyPacket addData(EnergyPacket ePacket, long timer) {
            bufferedPackets.removeIf(packet -> timer - packet.lastTickTime > STATISTIC_COUNT);
            bufferedPackets.add(ePacket);
            lastTickTime = timer;
            return ePacket;
        }

        double[] getData(long timer) {
            bufferedPackets.removeIf(packet -> timer - packet.lastTickTime > STATISTIC_COUNT);
            lastTickTime = timer;
            int size = bufferedPackets.size();
            if (size == 0) return NO_DATA;
            double amperage = 0.0, energy = 0.0;
            for (EnergyPacket ePacket : bufferedPackets) {
                amperage += ePacket.amperage;
                energy += ePacket.energy;
            }
            return amperage > 0.0 ? new double[]{amperage / size, energy / amperage} : NO_DATA;
        }
    }
}
