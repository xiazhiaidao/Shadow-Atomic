package local.shadowatomic;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraftforge.registries.*;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import java.util.*;

@Mod(AtomicMod.ID)
public final class AtomicMod {
    public static final String ID="shadow_atomic";
    public static final int SWORD_BASE_DAMAGE=999;
    public static final Logger LOG=LogUtils.getLogger();
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    // SwordItem adds the tier bonus, and the player supplies 1 base damage.
    public static final RegistryObject<Item> SWORD=ITEMS.register("shadow_sword",()->new SwordItem(Tiers.NETHERITE,SWORD_BASE_DAMAGE-1-(int)Tiers.NETHERITE.getAttackDamageBonus(),-2.4f,new Item.Properties().fireResistant().stacksTo(1)) {
        // The sword is an ability carrier. These item rules also cover pre-existing stacks.
        @Override public boolean canBeDepleted(){return false;}
        @Override public int getMaxDamage(ItemStack stack){return 0;}
        @Override public int getDamage(ItemStack stack){return 0;}
        @Override public void setDamage(ItemStack stack,int damage){stack.removeTagKey("Damage");}
        @Override public boolean isBarVisible(ItemStack stack){return false;}
        @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot,ItemStack stack){
            if(slot!=net.minecraft.world.entity.EquipmentSlot.MAINHAND)return super.getAttributeModifiers(slot,stack);
            var b=com.google.common.collect.ImmutableMultimap.<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier>builder();
            b.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",Math.max(0,ShadowRanges.stackDamage(stack))-1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            b.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_SPEED_UUID,"Weapon modifier",-2.4,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            return b.build();
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
            consumer.accept(local.shadowatomic.client.AtomicSwordPresentation.EXTENSIONS);
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BLOCK;}
        @Override public int getUseDuration(ItemStack stack){return 72000;}
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("tooltip.shadow_atomic.sword"));
            lines.add(Component.translatable("tooltip.shadow_atomic.body"));
            lines.add(Component.translatable("tooltip.shadow_atomic.carrier"));
            int attack=ShadowRanges.stackDamage(stack);
            if(attack<=0)lines.add(Component.translatable(attack<0?"tooltip.shadow_atomic.healing":"tooltip.shadow_atomic.zero_attack",Math.abs(attack)));
        }
    });
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ID);
    public static final RegistryObject<SoundEvent> CHARGE=sound("charge"), BLAST=sound("blast"), TAIL=sound("tail");
    public static final RegistryObject<SoundEvent> MUSIC=sound("music_theme"),VOICE=sound("voice_atomic");
    public static final RegistryObject<SoundEvent> DOMAIN_LOOP=sound("domain_loop");
    public static final RegistryObject<SoundEvent> RECOVERY_CHARGE=sound("recovery_charge"),RECOVERY_WEAVE=sound("recovery_weave"),RECOVERY_END=sound("recovery_end");
    public static final RegistryObject<SoundEvent> RECOVERY_VOICE=sound("recovery_voice");
    public static final RegistryObject<SoundEvent> SONIC_BOOM=sound("sonic_boom");
    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(new ResourceLocation(ID,name)));
    }
    public AtomicMod() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus); SOUNDS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,AtomicConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,AtomicClientConfig.SPEC);
        bus.addListener(this::tabs);
        // Explicit client shader registration; the nested @EventBusSubscriber scan no longer fires.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> {local.shadowatomic.client.AtomicVolume.attach(bus);local.shadowatomic.client.AtomicInput.attach(bus);});
        AtomicNetwork.init();
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }
    private void tabs(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.COMBAT) event.accept(SWORD);
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("atomic").requires(s->s.hasPermission(2))
            .then(Commands.literal("planetary").executes(c->{PlanetaryAtomic.request(c.getSource().getPlayerOrException(),false);return 1;})
                .then(Commands.literal("preview").executes(c->{PlanetaryAtomic.request(c.getSource().getPlayerOrException(),true);return 1;})))
            .then(Commands.literal("preview").executes(c->{AtomicDomains.input(c.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("cancel").executes(c->{var p=c.getSource().getPlayerOrException();AtomicDomains.cancel(p,"command");PlanetaryAtomic.cancel(p,"command");RecoveryAtomic.cancel(p,"command");return 1;}))
            .then(Commands.literal("cast").executes(c->{cast(c.getSource().getPlayerOrException());return 1;})
                .then(Commands.argument("radius",IntegerArgumentType.integer(10,384)).executes(c->{cast(c.getSource().getPlayerOrException(),AtomicConfig.mode(),IntegerArgumentType.getInteger(c,"radius"));return 1;})))
            .then(Commands.literal("recover").then(Commands.argument("event",UuidArgument.uuid()).executes(c->{
                try{AtomicServer.recover(c.getSource().getPlayerOrException().serverLevel(),UuidArgument.getUuid(c,"event"));c.getSource().sendSuccess(()->Component.literal("Recovery queued"),false);return 1;}
                catch(Exception ex){c.getSource().sendFailure(Component.literal(ex.getMessage()==null?"Recovery failed":ex.getMessage()));return 0;}
            })))
            .then(Commands.literal("give").executes(c->{c.getSource().getPlayerOrException().getInventory().placeItemBackInInventory(new ItemStack(SWORD.get()));return 1;})));
    }
    public static void cast(ServerPlayer player) {
        cast(player,AtomicConfig.mode(),ShadowRanges.get(player).attack());
    }
    public static void cast(ServerPlayer player,AtomicMode mode,int radius) {
        AtomicDomains.toggle(player,mode,radius);
    }
}
