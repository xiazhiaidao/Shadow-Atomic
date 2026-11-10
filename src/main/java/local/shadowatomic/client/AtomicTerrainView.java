package local.shadowatomic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import local.shadowatomic.AtomicMod;
import java.lang.reflect.Field;
import java.util.*;

/** A post-packet mesh revision barrier for the actual return frustum, not all world jobs. */
public final class AtomicTerrainView {
    private final Set<Long> chunks=new HashSet<>();
    private final Map<ChunkRenderDispatcher.RenderChunk,Revision> revisions=new IdentityHashMap<>();
    private Field viewArea;
    private boolean unavailable;
    public int visible,pending;
    private record Revision(long origin,ChunkRenderDispatcher.CompiledChunk before){}
    public AtomicTerrainView(long[] affected){
        for(long key:affected)chunks.add(key);
        try{viewArea=ObfuscationReflectionHelper.findField(LevelRenderer.class,"f_109469_");}
        catch(RuntimeException failure){unavailable=true;AtomicMod.LOG.warn("ATOMIC_TERRAIN_VIEW_UNAVAILABLE",failure);}
    }
    public boolean ready(){
        visible=0;pending=0;if(unavailable)return false;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.levelRenderer.getFrustum()==null)return false;
        try{
            var area=(ViewArea)viewArea.get(mc.levelRenderer);if(area==null)return false;
            var cache=new RenderRegionCache();
            for(var section:area.chunks){
                var origin=section.getOrigin();
                if(!chunks.contains(SectionPos.asLong(origin.getX()>>4,origin.getY()>>4,origin.getZ()>>4))||!mc.levelRenderer.getFrustum().isVisible(section.getBoundingBox()))continue;
                visible++;
                var chunk=mc.level.getChunkSource().getChunk(origin.getX()>>4,origin.getZ()>>4,ChunkStatus.FULL,false);
                if(chunk==null){pending++;continue;}
                var compiled=section.getCompiledChunk();
                int index=chunk.getSectionIndex(origin.getY());
                // Empty final data and an already empty mesh agree without a rebuild.
                if(index>=0&&index<chunk.getSections().length&&chunk.getSection(index).hasOnlyAir()&&compiled.hasNoRenderableLayers()&&compiled.getRenderableBlockEntities().isEmpty())continue;
                var old=revisions.get(section);
                if(old==null||old.origin()!=origin.asLong()){
                    revisions.put(section,new Revision(origin.asLong(),compiled));
                    // Cancel pre-packet builds and enqueue current real blocks once.
                    // The compiled object changes only after vanilla buffer uploads.
                    section.rebuildChunkAsync(mc.levelRenderer.getChunkRenderDispatcher(),cache);
                    section.setNotDirty();pending++;
                }else if(compiled==old.before()||compiled==ChunkRenderDispatcher.CompiledChunk.UNCOMPILED)pending++;
            }
            return pending==0;
        }catch(ReflectiveOperationException|RuntimeException failure){
            unavailable=true;AtomicMod.LOG.warn("ATOMIC_TERRAIN_VIEW_UNAVAILABLE",failure);return false;
        }
    }
}
