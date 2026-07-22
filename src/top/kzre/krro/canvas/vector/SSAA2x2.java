package top.kzre.krro.canvas.vector;

public class SSAA2x2 implements AntiAliasStrategy {
    private static final int SCALE = 2;
    private final ScanlineFiller filler = new ScanlineFiller();

    @Override
    public void fill(float[] dst, int w, int h,
                     double[] polygon, float[] color,
                     FillRule rule) {
        int sw = w * SCALE;
        int sh = h * SCALE;
        float[] temp = new float[sw * sh * 4];

        // 放大多边形坐标
        double[] scaledPoly = new double[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            scaledPoly[i] = polygon[i] * SCALE;
        }
        filler.fill(temp, sw, sh, scaledPoly, color, rule);

        // 降采样到原始分辨率
        downsample(temp, sw, sh, dst, w, h);
    }

    private void downsample(float[] src, int sw, int sh,
                            float[] dst, int dw, int dh) {
        float scaleSq = SCALE * SCALE;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                float r = 0, g = 0, b = 0, a = 0;
                int count = 0;
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        int sx = x * SCALE + dx;
                        int sy = y * SCALE + dy;
                        if (sx < sw && sy < sh) {
                            int idx = (sy * sw + sx) * 4;
                            float sa = src[idx + 3];
                            if (sa > 0) {
                                r += src[idx];
                                g += src[idx + 1];
                                b += src[idx + 2];
                                a += sa;
                                count++;
                            }
                        }
                    }
                }
                if (count > 0) {
                    int didx = (y * dw + x) * 4;
                    float srcR = r / count;
                    float srcG = g / count;
                    float srcB = b / count;
                    float srcA = a / count;
                    float dR = dst[didx], dG = dst[didx+1], dB = dst[didx+2], dA = dst[didx+3];
                    float invSrcA = 1.0f - srcA;
                    dst[didx]   = srcR * srcA + dR * dA * invSrcA;
                    dst[didx+1] = srcG * srcA + dG * dA * invSrcA;
                    dst[didx+2] = srcB * srcA + dB * dA * invSrcA;
                    dst[didx+3] = srcA + dA * invSrcA;
                }
            }
        }
    }
}