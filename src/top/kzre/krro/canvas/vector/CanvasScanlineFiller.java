package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.Canvas;
import top.kzre.krro.util.tile.Tile;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;


public class CanvasScanlineFiller {
    public enum AAMode { NONE, ANALYTIC, SSAA2x2 }

    private final AAMode aaMode;

    public CanvasScanlineFiller(AAMode aaMode) {
        this.aaMode = aaMode;
    }

    /** 填充单个瓦片 */
    public void fillTile(Canvas dest, int w, int h, double[] polygon,
                         float[] color, FillRule rule,
                         int tx, int ty, int tileSize) {
        int x0 = tx * tileSize;
        int y0 = ty * tileSize;
        int x1 = Math.min(x0 + tileSize, w);
        int y1 = Math.min(y0 + tileSize, h);
        int tw = x1 - x0;
        int th = y1 - y0;
        if (tw <= 0 || th <= 0) return;

        // 多边形裁剪到瓦片矩形，并偏移到局部坐标
        double[] clipped = TileClipper.clip(polygon, x0, y0, tw, th);
        if (clipped == null) return;

        for (int i = 0; i < clipped.length; i += 2) {
            clipped[i] -= x0;
            clipped[i + 1] -= y0;
        }

        Tile tile = dest.ensureTile(tx, ty);
        float[] dst = tile.getPixelsForWrite();
        int stride = tileSize * 4;                     // 瓦片行步幅（float 单位）
        int localY0 = TiledCanvas.localY(y0, tileSize);
        int localX0 = TiledCanvas.localX(x0, tileSize);

        switch (aaMode) {
            case NONE:
                fillNone(dst, stride, clipped, color, rule, localX0, localY0, tw, th);
                break;
            case ANALYTIC:
                fillNone(dst, stride, clipped, color, rule, localX0, localY0, tw, th);
//                fillAnalytic(dst, stride, clipped, color, rule, localX0, localY0, tw, th);
                break;
            case SSAA2x2:
                fillSSAA2x2(dst, stride, clipped, color, rule, localX0, localY0, tw, th);
                break;
        }
    }
    // ---------- 普通填充（无抗锯齿） ----------
    private static void fillNone(float[] dst, int stride, double[] polygon, float[] color, FillRule rule,
                                 int startX, int startY, int w, int h) {
        List<Edge>[] buckets = buildBuckets(polygon, h);
        List<Edge> active = new ArrayList<>();
        float r = color[0], g = color[1], b = color[2], a = color[3];

        for (int y = 0; y < h; y++) {
            if (y < buckets.length) active.addAll(buckets[y]);
            int finalY = y;
            active.removeIf(e -> e.ymax <= finalY);
            active.sort(Comparator.comparingDouble(e -> e.x));

            if (rule == FillRule.EVEN_ODD) {
                for (int i = 0; i + 1 < active.size(); i += 2) {
                    int x1 = Math.max(0, (int) Math.ceil(active.get(i).x));
                    int x2 = Math.min(w, (int) Math.floor(active.get(i + 1).x) + 1);
                    for (int x = x1; x < x2; x++) {
                        int pixIdx = (startY + y) * stride + (startX + x) * 4;
                        dst[pixIdx] = r;
                        dst[pixIdx + 1] = g;
                        dst[pixIdx + 2] = b;
                        dst[pixIdx + 3] = a;
                    }
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
                        int x1 = Math.max(0, (int) Math.ceil(spanStart));
                        int x2 = Math.min(w, (int) Math.floor(edge.x) + 1);
                        for (int x = x1; x < x2; x++) {
                            int pixIdx = (startY + y) * stride + (startX + x) * 4;
                            dst[pixIdx] = r;
                            dst[pixIdx + 1] = g;
                            dst[pixIdx + 2] = b;
                            dst[pixIdx + 3] = a;
                        }
                        inside = false;
                    }
                }
                if (inside) {
                    int x1 = Math.max(0, (int) Math.ceil(spanStart));
                    for (int x = x1; x < w; x++) {
                        int pixIdx = (startY + y) * stride + (startX + x) * 4;
                        dst[pixIdx] = r;
                        dst[pixIdx + 1] = g;
                        dst[pixIdx + 2] = b;
                        dst[pixIdx + 3] = a;
                    }
                }
            }
            for (Edge e : active) e.x += e.dx;
        }
    }

    // ---------- 分析抗锯齿 ----------
    private static void fillAnalytic(float[] dst, int stride, double[] polygon,
                                     float[] color,
                                     FillRule rule,
                                     int startX, int startY, int w, int h) {
        List<Edge>[] buckets = buildBuckets(polygon, h);
        List<Edge> active = new ArrayList<>();
        float r = color[0], g = color[1], b = color[2], a = color[3];

        for (int y = 0; y < h; y++) {
            if (y < buckets.length) active.addAll(buckets[y]);
            int finalY = y;
            active.removeIf(e -> e.ymax <= finalY);
            active.sort(Comparator.comparingDouble(e -> e.x));

            if (rule == FillRule.EVEN_ODD) {
                for (int i = 0; i + 1 < active.size(); i += 2) {
                    fillSpanAnalytic(dst, stride, startX, startY, y,
                            active.get(i).x, active.get(i + 1).x, r, g, b, a, w);
                }
            } else {
                int wind = 0;
                double spanStart = 0;
                boolean inside = false;
                for (Edge edge : active) {
                    wind += edge.winding;
                    if (wind != 0 && !inside) {
                        spanStart = edge.x;
                        inside = true;
                    } else if (wind == 0 && inside) {
                        fillSpanAnalytic(dst, stride, startX, startY, y,
                                spanStart, edge.x, r, g, b, a, w);
                        inside = false;
                    }
                }
                if (inside) fillSpanAnalytic(dst, stride, startX, startY, y,
                        spanStart, w, r, g, b, a, w);
            }
            for (Edge e : active) e.x += e.dx;
        }
    }

    private static void fillSpanAnalytic(float[] dst, int stride, int startX, int startY, int y,
                                         double x1, double x2, float r, float g, float b, float a, int maxW) {
        if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }
        int ix1 = (int) Math.floor(x1);
        int ix2 = (int) Math.floor(x2);
        if (ix1 >= maxW || ix2 < 0) return;
        for (int x = Math.max(0, ix1); x <= Math.min(maxW - 1, ix2); x++) {
            double cover = 1.0;
            if (x == ix1) cover = (ix1 + 1) - x1;
            if (x == ix2) cover = x2 - ix2;
            if (ix1 == ix2) cover = x2 - x1;
            if (cover <= 0.0) continue;
            float alpha = (float) (a * cover);
            int pixIdx = (startY + y) * stride + (startX + x) * 4;
            if (alpha >= 1.0f) {
                dst[pixIdx] = r;
                dst[pixIdx + 1] = g;
                dst[pixIdx + 2] = b;
                dst[pixIdx + 3] = a;
            } else {
                float dR = dst[pixIdx], dG = dst[pixIdx + 1];
                float dB = dst[pixIdx + 2], dA = dst[pixIdx + 3];
                float invAlpha = 1.0f - alpha;
                dst[pixIdx] = r * alpha + dR * invAlpha;
                dst[pixIdx + 1] = g * alpha + dG * invAlpha;
                dst[pixIdx + 2] = b * alpha + dB * invAlpha;
                dst[pixIdx + 3] = alpha + dA * invAlpha;
            }
        }
    }

    // ---------- SSAA 2x2 ----------
    private static void fillSSAA2x2(float[] dst, int stride, double[] polygon, float[] color, FillRule rule,
                                    int startX, int startY, int w, int h) {
        int scale = 2;
        int sw = w * scale;
        int sh = h * scale;
        int len = sw * sh * 4;
        FloatsPool pool = FloatsPools.getPool(len);
        float[] temp = pool.acquire();
        try {
            double[] scaledPoly = new double[polygon.length];
            for (int i = 0; i < polygon.length; i += 2) {
                scaledPoly[i] = polygon[i] * scale;
                scaledPoly[i + 1] = polygon[i + 1] * scale;
            }
            // 用普通填充绘制到 temp
            fillNone(temp, sw * 4, scaledPoly, color, rule, 0, 0, sw, sh);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float r = 0, g = 0, b = 0, a = 0;
                    int count = 0;
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            int sx = x * scale + dx;
                            int sy = y * scale + dy;
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
                        int pixIdx = (startY + y) * stride + (startX + x) * 4;
                        float srcR = r / count, srcG = g / count, srcB = b / count, srcA = a / count;
                        float dR = dst[pixIdx], dG = dst[pixIdx + 1];
                        float dB = dst[pixIdx + 2], dA = dst[pixIdx + 3];
                        float invSrcA = 1.0f - srcA;
                        dst[pixIdx] = srcR * srcA + dR * invSrcA;
                        dst[pixIdx + 1] = srcG * srcA + dG * invSrcA;
                        dst[pixIdx + 2] = srcB * srcA + dB * invSrcA;
                        dst[pixIdx + 3] = srcA + dA * invSrcA;
                    }
                }
            }
        } finally {
            pool.release(temp);
        }
    }

    // ---------- 辅助：建立边缘表 ----------
    @SuppressWarnings("unchecked")
    private static ArrayList<Edge>[] buildBuckets(double[] polygon, int h) {
        ArrayList<Edge>[] buckets = new ArrayList[h];
        for (int i = 0; i < h; i++) {
            buckets[i] = new ArrayList<>();
        }
        int n = polygon.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x1 = polygon[2 * i], y1 = polygon[2 * i + 1];
            double x2 = polygon[2 * j], y2 = polygon[2 * j + 1];
            if (y1 == y2) continue;
            Edge edge = new Edge(x1, y1, x2, y2);
            int y = Math.max(0, edge.ymin);
            if (y < h) {
                buckets[y].add(edge);
            }
        }
        return buckets;
    }

}