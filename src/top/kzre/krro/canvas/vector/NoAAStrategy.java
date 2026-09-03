package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

public final class NoAAStrategy implements AntiAliasStrategy {
    @Override
    public void fillPixel(double x, double y, float[] color, TiledCanvas canvas) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        // 直接设置像素（忽略透明度混合）
        canvas.setPixel(ix, iy, color);
    }
}