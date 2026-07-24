package top.kzre.krro.canvas.vector;

import java.util.*;

public class ScanlineFiller {

    /** 子类可重写此方法实现不同抗锯齿 / 像素风格 */
    protected void fillSpan(float[] dst, int w, int y,
                            double x1, double x2, float[] color) {
        if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }
        int start = (int) Math.floor(x1);
        int end   = (int) Math.floor(x2);
        if (start >= w || end < 0 || start > end) return;

        float r = color[0], g = color[1], b = color[2], a = color[3];
        for (int x = start; x <= end && x < w; x++) {
            if (x < 0) continue;
            int idx = (y * w + x) * 4;
            dst[idx] = r; dst[idx + 1] = g;
            dst[idx + 2] = b; dst[idx + 3] = a;
        }
    }

    public void fill(float[] dst, int w, int h,
                     double[] polygon, float[] color, FillRule rule) {
        int n = polygon.length / 2;
        if (n < 3) return;

        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (float) polygon[2 * i];
            ys[i] = (float) polygon[2 * i + 1];
        }

        List<List<Edge>> buckets = new ArrayList<>(h);
        for (int i = 0; i < h; i++) buckets.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float x1 = xs[i], y1 = ys[i], x2 = xs[j], y2 = ys[j];
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
                    fillSpan(dst, w, y, active.get(i).x, active.get(i + 1).x, color);
                }
            } else {
                int wind = 0;
                double spanStart = 0;
                boolean inside = false;
                for (Edge edge : active) {
                    wind += edge.winding;
                    if (wind != 0 && !inside) { spanStart = edge.x; inside = true; }
                    else if (wind == 0 && inside) {
                        fillSpan(dst, w, y, spanStart, edge.x, color);
                        inside = false;
                    }
                }
                if (inside) fillSpan(dst, w, y, spanStart, w, color);
            }
            for (Edge e : active) e.x += e.dx;
        }
    }

    // ---------- Edge 类保持不变 ----------
    private static class Edge {
        final int ymin, ymax;
        final double dx;
        double x;
        final int winding;
        Edge(float x1, float y1, float x2, float y2) {
            if (y1 < y2) {
                ymin = (int) Math.ceil(y1); ymax = (int) Math.ceil(y2);
                dx = (x2 - x1) / (y2 - y1);
                x = x1 + dx * (ymin - y1);
                winding = 1;
            } else {
                ymin = (int) Math.ceil(y2); ymax = (int) Math.ceil(y1);
                dx = (x2 - x1) / (y2 - y1);
                x = x2 + dx * (ymin - y2);
                winding = -1;
            }
        }
    }
}