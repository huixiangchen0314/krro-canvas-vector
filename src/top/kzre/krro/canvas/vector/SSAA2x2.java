package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;

public class SSAA2x2 implements AntiAliasStrategy {
    private static final int SCALE = 2;
    private final ScanlineFiller filler = new ScanlineFiller();

    @Override
    public void fill(float[] dst, int w, int h,
                     double[] polygon, float[] color,
                     FillRule rule) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < polygon.length; i += 2) {
            double x = polygon[i]; double y = polygon[i + 1];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        int bx = Math.max(0, (int) Math.floor(minX) - 1);
        int by = Math.max(0, (int) Math.floor(minY) - 1);
        int bw = Math.min(w, (int) Math.ceil(maxX) + 2) - bx;
        int bh = Math.min(h, (int) Math.ceil(maxY) + 2) - by;
        if (bw <= 0 || bh <= 0) return;

        int sw = bw * SCALE;
        int sh = bh * SCALE;
        int len = sw * sh * 4;
        FloatsPool pool = FloatsPools.getPool(len);
        float[] temp = pool.acquire();
        try {
            double[] scaledPoly = new double[polygon.length];
            for (int i = 0; i < polygon.length; i += 2) {
                scaledPoly[i]     = (polygon[i]     - bx) * SCALE;
                scaledPoly[i + 1] = (polygon[i + 1] - by) * SCALE;
            }
            filler.fill(temp, sw, sh, scaledPoly, color, rule);
            // 降采样到原图
            for (int y = 0; y < bh; y++) {
                for (int x = 0; x < bw; x++) {
                    float r = 0, g = 0, b = 0, a = 0;
                    int count = 0;
                    for (int dy = 0; dy < SCALE; dy++) {
                        for (int dx = 0; dx < SCALE; dx++) {
                            int sx = x * SCALE + dx;
                            int sy = y * SCALE + dy;
                            int idx = (sy * sw + sx) * 4;
                            float sa = temp[idx + 3];
                            if (sa > 0) {
                                r += temp[idx];
                                g += temp[idx + 1];
                                b += temp[idx + 2];
                                a += sa;
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        int didx = ((by + y) * w + (bx + x)) * 4;
                        float srcR = r / count, srcG = g / count, srcB = b / count, srcA = a / count;
                        float dR = dst[didx], dG = dst[didx+1], dB = dst[didx+2], dA = dst[didx+3];
                        float invSrcA = 1.0f - srcA;
                        dst[didx]   = srcR * srcA + dR * dA * invSrcA;
                        dst[didx+1] = srcG * srcA + dG * dA * invSrcA;
                        dst[didx+2] = srcB * srcA + dB * dA * invSrcA;
                        dst[didx+3] = srcA + dA * invSrcA;
                    }
                }
            }
        } finally {
            pool.release(temp);
        }
    }
}