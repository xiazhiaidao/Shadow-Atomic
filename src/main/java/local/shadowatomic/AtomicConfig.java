package local.shadowatomic;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AtomicConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DESTRUCTION;
    public static final ForgeConfigSpec.EnumValue<AtomicMode> MODE;
    public static final ForgeConfigSpec.IntValue RADIUS,DEPTH,WORK,PLANET_COOLDOWN,RECOVERY_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue DAMAGE,TERRAIN_BUDGET;
    public static final ForgeConfigSpec.BooleanValue SHADOW_GUARD;
    public static final ForgeConfigSpec.BooleanValue SHADOW_POWERS;
    public static final ForgeConfigSpec.BooleanValue STARTER_SWORD;
    public static final ForgeConfigSpec.DoubleValue SHADOW_SPEED,SHADOW_JUMP,SHADOW_FLY,SHADOW_BOOST,SHADOW_DAMAGE;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Server-authoritative Atomic gameplay. Every domain pauses non-player living entities; /atomic preview causes no damage or terrain changes.").push("atomic");
        MODE=b.comment("PREVIEW: no damage or terrain changes; COMBAT: journaled entity damage, no terrain changes; DESTRUCTION: damage and journaled excavation. All modes pause mobs until the domain ends. Captured when opening the domain.").defineEnum("mode",AtomicMode.DESTRUCTION);
        DESTRUCTION=b.comment("Legacy safety switch: false downgrades DESTRUCTION to PREVIEW. Explicit COMBAT remains damage-only.").define("terrainDestruction",true);
        RADIUS=b.defineInRange("radius",200,10,384);
        DEPTH=b.defineInRange("craterDepth",48,4,80);
        WORK=b.comment("Maximum scanned/changed blocks per terrain tick. Time budget remains shared; existing custom limits are respected.").defineInRange("workPerTick",100000,256,200000);
        TERRAIN_BUDGET=b.comment("Soft main-thread excavation budget in milliseconds, also reduced by the current tick's available slack. Journal compression runs on a bounded IO pipeline.").defineInRange("terrainBudgetMillis",20.0,2.0,30.0);
        DAMAGE=b.comment("Atomic center / Planetary hit damage. New key upgrades old worlds from centerDamage=200. Atomic retains its falloff down to 20% at the edge; dragon hits are routed through its head.").defineInRange("skillDamage",10000.0,0.0,1000000000.0);
        b.pop();b.push("starter");
        STARTER_SWORD=b.comment("首次进入自动赠送暗影之剑；老存档同样适用。每位玩家每个存档只领一次，已有剑不重复赠送，背包满时延后。关闭此项不影响死亡保留。", "Give one sword per player per world, including existing players. Full inventory defers delivery. Existing inventory/ender-chest swords count as received. Sword death retention is independent of this switch.").define("giveSwordOnFirstJoin",true);
        b.pop();b.push("planetary");PLANET_COOLDOWN=b.defineInRange("cooldownTicks",600,20,72000);b.pop();
        b.push("recovery");RECOVERY_COOLDOWN=b.defineInRange("cooldownTicks",200,20,72000);b.pop();
        b.push("guard");
        SHADOW_GUARD=b.comment("Survival/adventure players holding the shadow sword in either hand ignore ordinary damage on the server (attacks, mobs, explosions, fire, falling). Void damage and /kill still kill, and dropping or stowing the sword removes the protection on the same tick. Purely a damage filter: no knockback, no terrain or recovery-journal changes.").define("invincibleWithSword",true);
        b.pop();b.push("shadow");
        SHADOW_POWERS=b.comment("Held-sword survival/adventure mobility and journaled melee. Independent of the ordinary-damage guard; no terrain destruction or player-target damage enhancement.").define("enhancedBody",true);
        SHADOW_SPEED=b.defineInRange("groundSpeedMultiplier",2.0,1.0,4.0);
        SHADOW_JUMP=b.defineInRange("jumpMultiplier",1.8,1.0,3.0);
        SHADOW_FLY=b.comment("Creative-style flight: double jump or G toggles, WASD moves, jump/sneak ascends/descends. Hold the rebound vanilla sprint key plus a direction for controlled high-speed flight. New key replaces old flightSpeed=0.08.").defineInRange("creativeFlightSpeed",0.05,0.05,0.2);
        SHADOW_BOOST=b.comment("Flying speed reached gradually while deliberately holding the vanilla sprint key and a direction. Release sprint to brake. Sprint held before takeoff must be released before boosting; no automatic look-directed propulsion.").defineInRange("supersonicFlightSpeed",1.0,0.05,1.5);
        SHADOW_DAMAGE=b.comment("Legacy compatibility bonus for other-weapon attacks powered by an offhand sword. The main-hand slime sword uses its per-player -1000 to 1000 inspection setting instead. Positive empowered hits save a Recovery before-image before hurt().").defineInRange("additionalMeleeDamage",0.0,0.0,200.0);
        b.pop();SPEC=b.build();
    }
    public static AtomicMode mode(){AtomicMode mode=MODE.get();return mode==AtomicMode.DESTRUCTION&&!DESTRUCTION.get()?AtomicMode.PREVIEW:mode;}
}
