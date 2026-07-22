package top.kzre.krro.canvas.vector;

import java.util.*;

public class ScanlineFiller {

    public void fill(float[] dst, int w, int h,
                     double[] polygon, float[] color,
                     FillRule rule) {
        int n = polygon.length / 2;
        if (n < 3) return;

        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (float) polygon[2*i];
            ys[i] = (float) polygon[2*i+1];
        }

        // 建立边桶
        List<List<Edge>> buckets = new ArrayList<>(h);
        for (int i = 0; i < h; i++) buckets.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float x1 = xs[i], y1 = ys[i];
            float x2 = xs[j], y2 = ys[j];
            if (y1 == y2) continue;
            Edge edge = new Edge(x1, y1, x2, y2);
            int y = Math.max(0, edge.ymin);
            if (y < h) buckets.get(y).add(edge);
        }

        List<Edge> active = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            active.addAll(buckets.get(y));
            int finalY = y;
            active.removeIf(e -> e.ymax <= finalY);
            active.sort(Comparator.comparingDouble(e -> e.x));

            if (rule == FillRule.EVEN_ODD) {
                for (int i = 0; i + 1 < active.size(); i += 2) {
                    fillSpan(dst, w, y, active.get(i).x, active.get(i+1).x, color);
                }
            } else { // NON_ZERO
                int wind = 0;
                double spanStart = 0;
                boolean inside = false;
                for (Edge edge : active) {
                    wind += edge.winding;
                    if (wind != 0 && !inside) {
                        spanStart = edge.x;
                        inside = true;
                    } else if (wind == 0 && inside) {
                        fillSpan(dst, w, y, spanStart, edge.x, color);
                        inside = false;
                    }
                }
                if (inside) {
                    fillSpan(dst, w, y, spanStart, w, color);
                }
            }

            for (Edge e : active) e.x += e.dx;
        }
    }

    private void fillSpan(float[] dst, int w, int y,
                          double x1, double x2, float[] color) {
        if (x1 > x2) { double tmp = x1; x1 = x2; x2 = tmp; }
        int start = (int) Math.floor(x1);
        int end   = (int) Math.floor(x2);
        if (start >= w || end < 0 || start > end) return;

        float srcR = color[0], srcG = color[1], srcB = color[2], srcA = color[3];
        for (int x = start; x <= end && x < w; x++) {
            if (x < 0) continue;
            double cover = 1.0;
            if (x == start) cover = (start + 1) - x1;
            if (x == end)   cover = x2 - end;
            if (start == end) cover = x2 - x1;
            if (cover <= 0.0) continue;
            float alpha = (float)(srcA * cover);
            if (alpha >= 1.0f) {
                int idx = (y * w + x) * 4;
                dst[idx]   = srcR;
                dst[idx+1] = srcG;
                dst[idx+2] = srcB;
                dst[idx+3] = srcA;
            } else {
                int idx = (y * w + x) * 4;
                float dR = dst[idx], dG = dst[idx+1], dB = dst[idx+2], dA = dst[idx+3];
                float invAlpha = 1.0f - alpha;
                dst[idx]   = srcR * alpha + dR * invAlpha;
                dst[idx+1] = srcG * alpha + dG * invAlpha;
                dst[idx+2] = srcB * alpha + dB * invAlpha;
                dst[idx+3] = alpha + dA * invAlpha;
            }
        }
    }

    // ---------- 修复后的 Edge 类 ----------
    private static class Edge {
        final int ymin, ymax;
        final double dx;
        final int winding;
        double x;

        Edge(float x1, float y1, float x2, float y2) {
            if (y1 < y2) {
                // 边从下到上，winding = +1
                this.ymin = (int) Math.ceil(y1);
                this.ymax = (int) Math.ceil(y2);
                this.dx = (x2 - x1) / (y2 - y1);
                this.x = x1 + dx * (ymin - y1);
                this.winding = 1;
            } else {
                // 边从上到下，winding = -1
                this.ymin = (int) Math.ceil(y2);
                this.ymax = (int) Math.ceil(y1);
                this.dx = (x2 - x1) / (y2 - y1);  // 分母为负，dx 自动正确
                this.x = x2 + dx * (ymin - y2);   // 从 y2 开始插值
                this.winding = -1;
            }
        }
    }
}