package io.github.hbmcompat.network;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import appeng.container.AEBaseContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.github.hbmcompat.ae2.HbmDuality;
import io.github.hbmcompat.ae2.TileHbmAdapter;
import io.github.hbmcompat.machine.FactoryAllocationMode;
import io.github.hbmcompat.machine.FeedingMode;

/** Network threads only enqueue requests; all world access happens on the server tick. */
public final class FactoryModeNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("hbmFactoryMode");
    private static final Map<EntityPlayerMP, Request> REQUESTS = new ConcurrentHashMap<EntityPlayerMP, Request>();
    private final Map<EntityPlayerMP, State> sent = new WeakHashMap<EntityPlayerMP, State>();

    public static void init() {
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(StateHandler.class, State.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(new FactoryModeNetwork());
    }

    public static TileHbmAdapter adapter(Container container) {
        if (!(container instanceof appeng.container.implementations.ContainerInterface)) return null;
        Object target = ((AEBaseContainer) container).getTarget();
        return target instanceof TileHbmAdapter ? (TileHbmAdapter) target : null;
    }

    public static class State implements IMessage {
        public int window, x, y, z;
        public boolean factory, variety, supported, continuous;
        public State() {}
        public State(int window, TileHbmAdapter tile, boolean factory, boolean variety) {
            this.window = window;
            this.x = tile.xCoord; this.y = tile.yCoord; this.z = tile.zCoord;
            this.factory = factory; this.variety = variety;
        }
        @Override public void fromBytes(ByteBuf buf) {
            window = buf.readInt(); x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
            factory = buf.readBoolean(); variety = buf.readBoolean();
            supported = buf.readBoolean(); continuous = buf.readBoolean();
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(window); buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
            buf.writeBoolean(factory); buf.writeBoolean(variety);
            buf.writeBoolean(supported); buf.writeBoolean(continuous);
        }
        public boolean matches(Container container, TileHbmAdapter tile) {
            return tile != null && container.windowId == window
                    && tile.xCoord == x && tile.yCoord == y && tile.zCoord == z;
        }
        boolean same(State other) {
            return other != null && window == other.window && x == other.x && y == other.y && z == other.z
                    && factory == other.factory && variety == other.variety
                    && supported == other.supported && continuous == other.continuous;
        }
    }

    public static final class Request extends State {
        public int setting; // 0 query, 1 allocation, 2 feeding; unknown values are ignored.
        public Request() {}
        public Request(int window, TileHbmAdapter tile, boolean variety, boolean change) {
            super(window, tile, false, variety);
            this.setting = change ? 1 : 0;
        }
        public static Request feeding(int window, TileHbmAdapter tile, boolean continuous) {
            Request request = new Request(window, tile, false, false);
            request.setting = 2;
            request.continuous = continuous;
            return request;
        }
        @Override public void fromBytes(ByteBuf buf) { super.fromBytes(buf); setting = buf.readUnsignedByte(); }
        @Override public void toBytes(ByteBuf buf) { super.toBytes(buf); buf.writeByte(setting); }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override public IMessage onMessage(Request message, MessageContext ctx) {
            // At most one pending request per player, including while the server is stalled.
            REQUESTS.put(ctx.getServerHandler().playerEntity, message);
            return null;
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        @Override public IMessage onMessage(State message, MessageContext ctx) {
            io.github.hbmcompat.HbmCompat.proxy.receiveFactoryState(message);
            return null;
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.player);
        sent.remove(event.player);
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Request request = REQUESTS.remove(player);
        Container container = player.openContainer;
        TileHbmAdapter tile = adapter(container);
        if (tile == null || tile.isInvalid() || tile.getWorldObj() != player.worldObj
                || !container.canInteractWith(player)) {
            sent.remove(player);
            return;
        }
        HbmDuality duality = (HbmDuality) tile.getInterfaceDuality();
        boolean factory = duality.hasFactoryTarget();
        boolean supported = duality.hasMachineTarget();
        if (request != null && request.setting != 0 && request.matches(container, tile)
                && mayConfigure(tile, player)) {
            if (request.setting == 1 && factory) duality.setAllocationMode(request.variety
                    ? FactoryAllocationMode.VARIETY_FIRST : FactoryAllocationMode.PARALLEL_FIRST);
            if (request.setting == 2 && supported) duality.setFeedingMode(request.continuous
                    ? FeedingMode.CONTINUOUS : FeedingMode.SINGLE_BATCH);
        }
        State state = new State(container.windowId, tile, factory,
                duality.getAllocationMode() == FactoryAllocationMode.VARIETY_FIRST);
        state.supported = supported;
        state.continuous = duality.getFeedingMode() == FeedingMode.CONTINUOUS;
        if (request != null || !state.same(sent.get(player))) {
            CHANNEL.sendTo(state, player);
            sent.put(player, state);
        }
    }

    private boolean mayConfigure(TileHbmAdapter tile, EntityPlayerMP player) {
        appeng.api.networking.IGridNode node = tile.getProxy().getNode();
        if (node == null || node.getGrid() == null) return false;
        appeng.api.networking.security.ISecurityGrid security = node.getGrid().getCache(
                appeng.api.networking.security.ISecurityGrid.class);
        return security.hasPermission(player, appeng.api.config.SecurityPermissions.BUILD);
    }
}
