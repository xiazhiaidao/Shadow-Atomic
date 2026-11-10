package local.shadowatomic;

/** A server-selected, per-domain snapshot; clients never choose damaging modes. */
public enum AtomicMode {
    PREVIEW, COMBAT, DESTRUCTION;
    public boolean damagesEntities(){return this!=PREVIEW;}
    public boolean destroysTerrain(){return this==DESTRUCTION;}
    public String translationKey(){return "mode.shadow_atomic."+name().toLowerCase(java.util.Locale.ROOT);}
}
