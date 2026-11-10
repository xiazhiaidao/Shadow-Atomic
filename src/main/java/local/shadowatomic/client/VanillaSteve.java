package local.shadowatomic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Select the bundled vanilla skin and wide model for this client; no custom geometry. */
public final class VanillaSteve {
    public static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/entity/player/wide/steve.png");
    public static ResourceLocation skin(AbstractClientPlayer player,ResourceLocation original){return player==Minecraft.getInstance().player?TEXTURE:original;}
    public static String model(AbstractClientPlayer player,String original){return player==Minecraft.getInstance().player?"default":original;}
    public static ResourceLocation cape(AbstractClientPlayer player,ResourceLocation original){return player==Minecraft.getInstance().player?null:original;}
}
