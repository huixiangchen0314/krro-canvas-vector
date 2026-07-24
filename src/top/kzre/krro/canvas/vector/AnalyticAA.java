package top.kzre.krro.canvas.vector;

public class AnalyticAA extends ScanlineFiller implements AntiAliasStrategy {

    @Override
    protected void fillSpan(float[] dst, int w, int y,
                            double x1, double x2, float[] color) {
        // 分析抗锯齿：计算首末像素覆盖率
        if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }
        int start = (int) Math.floor(x1);
        int end   = (int) Math.floor(x2);
        if (start >= w || end < 0 || start > end) return;

        float r = color[0], g = color[1], b = color[2], a = color[3];
        for (int x = start; x <= end && x < w; x++) {
            if (x < 0) continue;
            double cover = 1.0;
            if (x == start) cover = (start + 1) - x1;
            if (x == end)   cover = x2 - end;
            if (start == end) cover = x2 - x1;
            if (cover <= 0.0) continue;
            float alpha = (float)(a * cover);
            int idx = (y * w + x) * 4;
            if (alpha >= 1.0f) {
                dst[idx] = r; dst[idx+1] = g;
                dst[idx+2] = b; dst[idx+3] = a;
            } else {
                float dR = dst[idx], dG = dst[idx+1], dB = dst[idx+2], dA = dst[idx+3];
                float invA = 1.0f - alpha;
                dst[idx]   = r*alpha + dR*invA;
                dst[idx+1] = g*alpha + dG*invA;
                dst[idx+2] = b*alpha + dB*invA;
                dst[idx+3] = alpha + dA*invA;
            }
        }
    }

    @Override
    public void fill(float[] dst, int w, int h, double[] polygon, float[] color, FillRule rule) {
        super.fill(dst, w, h, polygon, color, rule);
    }
}