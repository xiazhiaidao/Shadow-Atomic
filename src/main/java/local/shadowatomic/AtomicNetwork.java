package local.shadowatomic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;

public final class AtomicNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(AtomicMod.ID,"main"),()->"15","15"::equals,"15"::equals);
    public record Stasis(int entity,UUID uuid,boolean frozen){}
    public enum Phase { READY,HOLDING,RELEASING,COOLDOWN,WORLD_BUSY }
    public record Status(long serverTick,int remainingTicks,AtomicMode mode,Phase phase,ResourceLocation dimension){}
    public record Begin(int caster, UUID event, Vec3 origin, long startTick,float scale,AtomicMode mode,boolean releasing,long elapsedMillis,boolean impactLocked) {
        // Compatibility for in-process fixtures. Production always supplies server elapsed time.
        public Begin(int caster,UUID event,Vec3 origin,long startTick,float scale,AtomicMode mode,boolean releasing){this(caster,event,origin,startTick,scale,mode,releasing,-1);}
        public Begin(int caster,UUID event,Vec3 origin,long startTick,float scale,AtomicMode mode,boolean releasing,long elapsedMillis){this(caster,event,origin,startTick,scale,mode,releasing,elapsedMillis,releasing&&elapsedMillis>=5000);}
        void encode(FriendlyByteBuf b) {b.writeInt(caster);b.writeUUID(event);b.writeDouble(origin.x);b.writeDouble(origin.y);b.writeDouble(origin.z);b.writeLong(startTick);b.writeFloat(scale);b.writeEnum(mode);b.writeBoolean(releasing);b.writeLong(elapsedMillis);b.writeBoolean(impactLocked);}
        static Begin decode(FriendlyByteBuf b) {return new Begin(b.readInt(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readLong(),b.readFloat(),b.readEnum(AtomicMode.class),b.readBoolean(),b.readLong(),b.readBoolean());}
    }
    private record Toggle(boolean preview){}
    private record End(UUID event){}
    public record Terrain(UUID event,boolean success,long[] sections){}
    private record TerrainAck(UUID event){}
    private record PlanetRequest(boolean preview){}
    /** Render-only public tracked appearance, not private AI/inventory NBT or damage authority. */
    public record PlanetLook(ResourceLocation type,UUID uuid,List<SynchedEntityData.DataValue<?>> data,List<Pair<EquipmentSlot,ItemStack>> equipment){
        public static PlanetLook capture(LivingEntity target){
            if(target==null)return null;var values=target.getEntityData().getNonDefaultValues();var gear=new ArrayList<Pair<EquipmentSlot,ItemStack>>();
            for(var slot:EquipmentSlot.values())gear.add(Pair.of(slot,target.getItemBySlot(slot).copy()));
            return new PlanetLook(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType()),target.getUUID(),values==null?List.of():List.copyOf(values),List.copyOf(gear));
        }
        void encode(FriendlyByteBuf b){b.writeResourceLocation(type);b.writeUUID(uuid);new ClientboundSetEntityDataPacket(0,data).write(b);new ClientboundSetEquipmentPacket(0,equipment).write(b);}
        static PlanetLook decode(FriendlyByteBuf b){return new PlanetLook(b.readResourceLocation(),b.readUUID(),new ClientboundSetEntityDataPacket(b).packedItems(),new ClientboundSetEquipmentPacket(b).getSlots());}
    }
    public record PlanetBegin(int caster,UUID event,Vec3 origin,int target,Vec3 targetPos,String targetName,AtomicMode mode,long elapsedMillis,PlanetLook look){}
    private record PlanetEnd(UUID event){}
    public record PlanetStatus(int remainingTicks,boolean active,ResourceLocation dimension){}
    private record RecoveryListRequest(){}
    private record RecoveryRequest(UUID event){}
    private record RecoveryCancel(){}
    private record RecoveryEnd(UUID event){}
    /** A normal hit was refused by a hooded sword holder. Client-only feedback, no sound. */
    public record Guard(int player, Vec3 point, Vec3 direction, float amount){};
    public record Powers(boolean active,float normal,float boost,float jump){}
    private record FlightToggle(){}
    public record Ranges(int attack,int recovery,int damage){public Ranges(int attack,int recovery){this(attack,recovery,AtomicMod.SWORD_BASE_DAMAGE);}}
    private record SwordDamageSet(int damage){}
    private record RangeRequest(){}
    private record RangeSet(int attack,int recovery){}
    private record DashRequest(){}
    public record Motion(int player,Vec3 from,Vec3 to,boolean mach){}
    public record RecoveryEntry(UUID event,Vec3 origin,int radius,long blocks,int entities,long recovered,long revived){}
    public record RecoveryCatalog(ResourceLocation dimension,List<RecoveryEntry> entries,String message){}
    public record RecoveryState(UUID event,int caster,Vec3 origin,int radius,ResourceLocation dimension,long elapsedMillis,boolean committed,boolean done,boolean success,long total,long processed,long restored,long revived,long already,long conflicts,long unloaded,long unsupported,long uncertain,List<Vec3> samples){
        void encode(FriendlyByteBuf b){b.writeUUID(event);b.writeInt(caster);vec(b,origin);b.writeVarInt(radius);b.writeResourceLocation(dimension);b.writeLong(elapsedMillis);b.writeBoolean(committed);b.writeBoolean(done);b.writeBoolean(success);for(long n:new long[]{total,processed,restored,revived,already,conflicts,unloaded,unsupported,uncertain})b.writeVarLong(n);b.writeVarInt(samples.size());for(var p:samples)vec(b,p);}
        static RecoveryState decode(FriendlyByteBuf b){UUID event=b.readUUID();int caster=b.readInt();Vec3 origin=vec(b);int radius=b.readVarInt();ResourceLocation dimension=b.readResourceLocation();long age=b.readLong();boolean committed=b.readBoolean(),done=b.readBoolean(),success=b.readBoolean();long[] n=new long[9];for(int i=0;i<n.length;i++)n[i]=b.readVarLong();int size=b.readVarInt();if(size<0||size>24)throw new IllegalArgumentException("Recovery sample limit");var samples=new ArrayList<Vec3>();for(int i=0;i<size;i++)samples.add(vec(b));return new RecoveryState(event,caster,origin,radius,dimension,age,committed,done,success,n[0],n[1],n[2],n[3],n[4],n[5],n[6],n[7],n[8],List.copyOf(samples));}
    }
    private static void vec(FriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 vec(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public static void init() {
        CHANNEL.messageBuilder(Stasis.class,24,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeInt(m.entity);b.writeUUID(m.uuid);b.writeBoolean(m.frozen);})
            .decoder(b->new Stasis(b.readInt(),b.readUUID(),b.readBoolean()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.DomainStasisClient.state(m))).add();
        CHANNEL.messageBuilder(Begin.class,0,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(Begin::encode).decoder(Begin::decode)
            .consumerMainThread((msg,ctx)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.AtomicClient.begin(msg)))
            .add();
        CHANNEL.messageBuilder(Toggle.class,1,NetworkDirection.PLAY_TO_SERVER)
            .encoder((msg,b)->b.writeBoolean(msg.preview)).decoder(b->new Toggle(b.readBoolean()))
            .consumerMainThread((msg,ctx)->{var player=ctx.get().getSender();if(player!=null)AtomicDomains.input(player,msg.preview);}).add();
        CHANNEL.messageBuilder(End.class,2,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((msg,b)->b.writeUUID(msg.event)).decoder(b->new End(b.readUUID()))
            .consumerMainThread((msg,ctx)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.AtomicClient.end(msg.event))).add();
        CHANNEL.messageBuilder(Status.class,3,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((msg,b)->{b.writeLong(msg.serverTick);b.writeVarInt(msg.remainingTicks);b.writeEnum(msg.mode);b.writeEnum(msg.phase);b.writeResourceLocation(msg.dimension);})
            .decoder(b->new Status(b.readLong(),b.readVarInt(),b.readEnum(AtomicMode.class),b.readEnum(Phase.class),b.readResourceLocation()))
            .consumerMainThread((msg,ctx)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.AtomicHud.update(msg))).add();
        CHANNEL.messageBuilder(Terrain.class,4,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((msg,b)->{b.writeUUID(msg.event);b.writeBoolean(msg.success);b.writeVarInt(msg.sections.length);for(long p:msg.sections)b.writeLong(p);})
            .decoder(b->{UUID id=b.readUUID();boolean success=b.readBoolean();int count=b.readVarInt();if(count<0||count>65536)throw new IllegalArgumentException("Invalid Atomic section count");long[] positions=new long[count];for(int i=0;i<count;i++)positions[i]=b.readLong();return new Terrain(id,success,positions);})
            .consumerMainThread((msg,ctx)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.AtomicClient.terrain(msg))).add();
        CHANNEL.messageBuilder(TerrainAck.class,5,NetworkDirection.PLAY_TO_SERVER)
            .encoder((msg,b)->b.writeUUID(msg.event)).decoder(b->new TerrainAck(b.readUUID()))
            .consumerMainThread((msg,ctx)->{var player=ctx.get().getSender();if(player!=null)AtomicTerrainSync.ownerReady(player,msg.event);}).add();
        CHANNEL.messageBuilder(PlanetRequest.class,6,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->b.writeBoolean(m.preview)).decoder(b->new PlanetRequest(b.readBoolean()))
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)PlanetaryAtomic.request(p,m.preview);}).add();
        CHANNEL.messageBuilder(PlanetBegin.class,7,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeInt(m.caster);b.writeUUID(m.event);b.writeDouble(m.origin.x);b.writeDouble(m.origin.y);b.writeDouble(m.origin.z);b.writeInt(m.target);b.writeDouble(m.targetPos.x);b.writeDouble(m.targetPos.y);b.writeDouble(m.targetPos.z);b.writeUtf(m.targetName,256);b.writeEnum(m.mode);b.writeLong(m.elapsedMillis);b.writeBoolean(m.look!=null);if(m.look!=null)m.look.encode(b);})
            .decoder(b->{int caster=b.readInt();UUID id=b.readUUID();Vec3 origin=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());int target=b.readInt();Vec3 position=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());String name=b.readUtf(256);AtomicMode mode=b.readEnum(AtomicMode.class);long elapsed=b.readLong();return new PlanetBegin(caster,id,origin,target,position,name,mode,elapsed,b.readBoolean()?PlanetLook.decode(b):null);})
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.PlanetaryClient.begin(m))).add();
        CHANNEL.messageBuilder(PlanetEnd.class,8,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->b.writeUUID(m.event)).decoder(b->new PlanetEnd(b.readUUID()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.PlanetaryClient.end(m.event))).add();
        CHANNEL.messageBuilder(PlanetStatus.class,9,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeVarInt(m.remainingTicks);b.writeBoolean(m.active);b.writeResourceLocation(m.dimension);})
            .decoder(b->new PlanetStatus(b.readVarInt(),b.readBoolean(),b.readResourceLocation()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.PlanetaryClient.status=m)).add();
        CHANNEL.messageBuilder(RecoveryListRequest.class,10,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->{}).decoder(b->new RecoveryListRequest()).consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)RecoveryAtomic.catalog(p);}).add();
        CHANNEL.messageBuilder(RecoveryCatalog.class,11,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeResourceLocation(m.dimension);b.writeUtf(m.message,128);b.writeVarInt(m.entries.size());for(var e:m.entries){b.writeUUID(e.event);vec(b,e.origin);b.writeVarInt(e.radius);b.writeVarLong(e.blocks);b.writeVarInt(e.entities);b.writeVarLong(e.recovered);b.writeVarLong(e.revived);}})
            .decoder(b->{ResourceLocation d=b.readResourceLocation();String message=b.readUtf(128);int n=b.readVarInt();if(n<0||n>32)throw new IllegalArgumentException("Recovery catalog limit");var entries=new ArrayList<RecoveryEntry>();for(int i=0;i<n;i++)entries.add(new RecoveryEntry(b.readUUID(),vec(b),b.readVarInt(),b.readVarLong(),b.readVarInt(),b.readVarLong(),b.readVarLong()));return new RecoveryCatalog(d,List.copyOf(entries),message);})
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.RecoveryClient.catalog(m))).add();
        CHANNEL.messageBuilder(RecoveryRequest.class,12,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->b.writeUUID(m.event)).decoder(b->new RecoveryRequest(b.readUUID())).consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)RecoveryAtomic.request(p,m.event);}).add();
        CHANNEL.messageBuilder(RecoveryState.class,13,NetworkDirection.PLAY_TO_CLIENT).encoder(RecoveryState::encode).decoder(RecoveryState::decode)
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.RecoveryClient.state(m))).add();
        CHANNEL.messageBuilder(RecoveryEnd.class,14,NetworkDirection.PLAY_TO_CLIENT).encoder((m,b)->b.writeUUID(m.event)).decoder(b->new RecoveryEnd(b.readUUID()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.RecoveryClient.end(m.event))).add();
        CHANNEL.messageBuilder(RecoveryCancel.class,15,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new RecoveryCancel())
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)RecoveryAtomic.cancel(p,"key");}).add();
        CHANNEL.messageBuilder(Guard.class,16,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeInt(m.player);b.writeDouble(m.point.x);b.writeDouble(m.point.y);b.writeDouble(m.point.z);b.writeDouble(m.direction.x);b.writeDouble(m.direction.y);b.writeDouble(m.direction.z);b.writeFloat(m.amount);})
            .decoder(b->new Guard(b.readInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.ShadowGuardFx.hit(m))).add();
        CHANNEL.messageBuilder(Powers.class,17,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeBoolean(m.active);b.writeFloat(m.normal);b.writeFloat(m.boost);b.writeFloat(m.jump);})
            .decoder(b->new Powers(b.readBoolean(),b.readFloat(),b.readFloat(),b.readFloat()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.ShadowPowersClient.state(m))).add();
        CHANNEL.messageBuilder(FlightToggle.class,18,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->{}).decoder(b->new FlightToggle())
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)ShadowPowers.toggleFlight(p);}).add();
        CHANNEL.messageBuilder(Ranges.class,19,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeVarInt(m.attack);b.writeVarInt(m.recovery);b.writeVarInt(m.damage);}).decoder(b->new Ranges(b.readVarInt(),b.readVarInt(),b.readVarInt()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.SwordInspectionClient.ranges(m))).add();
        CHANNEL.messageBuilder(RangeRequest.class,20,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new RangeRequest())
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null&&ShadowGuard.holding(p))ShadowRanges.sync(p);}).add();
        CHANNEL.messageBuilder(RangeSet.class,21,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->{b.writeVarInt(m.attack);b.writeVarInt(m.recovery);}).decoder(b->new RangeSet(b.readVarInt(),b.readVarInt()))
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)ShadowRanges.set(p,m.attack,m.recovery);}).add();
        CHANNEL.messageBuilder(SwordDamageSet.class,25,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->b.writeVarInt(m.damage)).decoder(b->new SwordDamageSet(b.readVarInt()))
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)ShadowRanges.setDamage(p,m.damage);}).add();
        CHANNEL.messageBuilder(DashRequest.class,22,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new DashRequest())
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)ShadowCombat.dash(p);}).add();
        CHANNEL.messageBuilder(Motion.class,23,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeInt(m.player);vec(b,m.from);vec(b,m.to);b.writeBoolean(m.mach);})
            .decoder(b->new Motion(b.readInt(),vec(b),vec(b),b.readBoolean()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.shadowatomic.client.ShadowMachFx.motion(m))).add();
    }
    public static void send(ServerPlayer player,Begin packet) {CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);AtomicTerrainSync.send(player,packet.event);}
    public static void stasis(LivingEntity entity,Stasis packet){CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(()->entity),packet);}
    public static void stasis(ServerPlayer player,Stasis packet){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
    public static void end(ServerPlayer player,UUID event){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new End(event));}
    public static void request(boolean preview){CHANNEL.sendToServer(new Toggle(preview));}
    public static void status(ServerPlayer player,Status status){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),status);}
    public static void terrain(ServerPlayer player,Terrain packet){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
    public static void terrainAck(UUID event){CHANNEL.sendToServer(new TerrainAck(event));}
    public static void requestPlanet(boolean preview){CHANNEL.sendToServer(new PlanetRequest(preview));}
    public static void planetBegin(ServerPlayer player,PlanetBegin value){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),value);}
    public static void planetEnd(ServerPlayer player,UUID id){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new PlanetEnd(id));}
    public static void planetStatus(ServerPlayer player,PlanetStatus value){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),value);}
    public static void requestRecoveryCatalog(){CHANNEL.sendToServer(new RecoveryListRequest());}
    public static void requestRecovery(UUID event){CHANNEL.sendToServer(new RecoveryRequest(event));}
    public static void cancelRecovery(){CHANNEL.sendToServer(new RecoveryCancel());}
    public static void recoveryCatalog(ServerPlayer p,RecoveryCatalog value){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
    public static void recoveryState(ServerPlayer p,RecoveryState value){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
    public static void recoveryEnd(ServerPlayer p,UUID id){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new RecoveryEnd(id));}
    public static void guard(ServerPlayer p,Guard value){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
    public static void powers(ServerPlayer p,Powers value){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
    public static void toggleFlight(){CHANNEL.sendToServer(new FlightToggle());}
    public static void ranges(ServerPlayer p,Ranges value){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
    public static void requestRanges(){CHANNEL.sendToServer(new RangeRequest());}
    public static void setRanges(int attack,int recovery){CHANNEL.sendToServer(new RangeSet(attack,recovery));}
    public static void setSwordDamage(int damage){CHANNEL.sendToServer(new SwordDamageSet(damage));}
    public static void dash(){CHANNEL.sendToServer(new DashRequest());}
    public static void motion(ServerPlayer p,Motion value){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
}
