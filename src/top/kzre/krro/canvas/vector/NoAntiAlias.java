package top.kzre.krro.canvas.vector;

public class NoAntiAlias implements AntiAliasStrategy {
    private final ScanlineFiller filler = new ScanlineFiller();

    @Override
    public void fill(float[] dst, int w, int h,
                     double[] polygon, float[] color,
                     FillRule rule) {
        filler.fill(dst, w, h, polygon, color, rule);
    }
}