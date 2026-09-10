package top.kzre.krro.canvas.vector;

import top.kzre.colorutils.blend.PorterDuff;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;
import top.kzre.krro.util.tile.TiledCanvas;

public final class AnalyticAAStrategy implements AntiAliasStrategy {

    private static final FloatsPool pool4f;

    static {
        FloatsHolder holder = PoolManagers.floats().getHolder();
        pool4f =  holder.getPool(4);
    }

    @Override
    public void fillPixel(double x, double y, float[] color, TiledCanvas canvas) {
        // 取坐标的小数部分作为覆盖率（0~1）
        double cx = x - Math.floor(x);
        double cy = y - Math.floor(y);
        // 简单取平均值作为覆盖率，也可使用其他方式
        double coverage = Math.min(1.0, Math.max(0.0, (cx + cy) * 0.5));
        // 如果全覆盖或几乎为零，直接处理
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        if (coverage >= 1.0) {
            canvas.setPixel(ix, iy, color);
        } else if (coverage > 1e-6) {
            // 读取现有像素并混合
            float[] dest = new float[4];
            canvas.getPixel(ix, iy, dest);
            float alpha = color[3] * (float) coverage;
            float invAlpha = 1.0f - alpha;
            dest[0] = color[0] * alpha + dest[0] * invAlpha;
            dest[1] = color[1] * alpha + dest[1] * invAlpha;
            dest[2] = color[2] * alpha + dest[2] * invAlpha;
            dest[3] = alpha + dest[3] * invAlpha;
            canvas.setPixel(ix, iy, dest);

            float[] colorCopy = pool4f.acquire();
            try {
                colorCopy[0] = color[0];
                colorCopy[1] = color[1];
                colorCopy[2] = color[2];
                colorCopy[3] = (float) (color[3] * coverage);


                int tileSize = canvas.getTileSize();
                int tx = TiledCanvas.tile(ix, tileSize);
                int ty = TiledCanvas.tile(iy, tileSize);
                float[] pixels = canvas.getTile(tx, ty).getPixelsSnapshot();
                int offset = TiledCanvas.localOffset(ix, iy, tileSize, 4);

                PorterDuff.over(pixels, offset,
                        pixels, offset,
                        colorCopy, 0);
            }finally {
                pool4f.release(colorCopy);
            }

        }
        // 否则忽略
    }
}