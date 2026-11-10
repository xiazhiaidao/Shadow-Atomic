package local.shadowatomic.client;

public final class AtomicMath {
    public static double clamp(double x){return Math.max(0,Math.min(1,x));}
    public static double smooth(double a,double b,double t){double x=clamp((t-a)/(b-a));return x*x*(3-2*x);}
    public static double fade(double t){return 1-smooth(10,14.5,t);}
    public static double radius(double t){return 2+126*(1-Math.exp(-Math.max(0,t-7)*2.0));}
}
