package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;
import top.kzre.krro.util.pool.DoublesPools;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * 矢量光栅化器：将矢量形状（填充、描边）渲染到图层自己的像素缓冲区。
 * 支持基于瓦片脏区域的增量渲染。
 * 所有临时 double[] 使用 {@link DoublesPools} 进行池化，避免频繁 GC。
 */
public final class CurveRasterizer {

    private static final double FLATNESS = 0.25;
    private static final double MITER_LIMIT = 4.0;

    // ═══ 填充（全画布） ═══
    public static void fill(float[] dst, int w, int h,
                            Curve curve, float[] color, FillRule rule) {
        fill(dst, w, h, curve, color, rule, null, 0);
    }

    /**
     * 填充，支持脏瓦片集合增量渲染。
     *
     * @param dst        目标数组
     * @param w          画布宽度
     * @param h          画布高度
     * @param curve      曲线
     * @param color      RGBA 颜色
     * @param rule       填充规则
     * @param dirtyTiles 脏瓦片键集合（null 表示全量渲染，空集合表示无脏，非空表示增量渲染）
     * @param tileSize   瓦片边长（仅在 dirtyTiles 非空时有效）
     */
    public static void fill(float[] dst, int w, int h,
                            Curve curve, float[] color, FillRule rule,
                            Set<Long> dirtyTiles, int tileSize) {
        double[] flat = CurveUtils.flattenCurveAdaptive(curve, FLATNESS);
        if (flat.length < 4) {
            DoublesPools.getPool(flat.length).release(flat);
            return;
        }
        if (dirtyTiles == null) {
            // 全量渲染
            fillPolygon(dst, w, h, flat, color, rule);
        } else if (dirtyTiles.isEmpty()) {
            // 无脏区域，直接返回
        } else {
            for (long key : dirtyTiles) {
                int tx = TiledCanvas.unpackTx(key);
                int ty = TiledCanvas.unpackTy(key);
                int startX = Math.max(0, tx * tileSize);
                int startY = Math.max(0, ty * tileSize);
                int endX = Math.min(startX + tileSize, w);
                int endY = Math.min(startY + tileSize, h);
                if (startX >= endX || startY >= endY) continue;
                int rectW = endX - startX;
                int rectH = endY - startY;
                double[] clipped = clipPolygonToRect(flat, startX, startY, rectW, rectH);
                if (clipped != null && clipped.length >= 6) {
                    fillPolygon(dst, w, h, clipped, color, rule);
                    DoublesPools.getPool(clipped.length).release(clipped);
                }
            }
        }
        DoublesPools.getPool(flat.length).release(flat);
    }

    // ═══ 固定宽度描边（全画布） ═══
    public static void strokeFixed(float[] dst, int w, int h,
                                   Curve curve, float width,
                                   float[] color, Cap cap, Join join) {
        strokeVariable(dst, w, h, curve, t -> width, color, cap, join, null, 0);
    }

    /**
     * 固定宽度描边，支持脏瓦片集合。
     */
    public static void strokeFixed(float[] dst, int w, int h,
                                   Curve curve, float width,
                                   float[] color, Cap cap, Join join,
                                   Set<Long> dirtyTiles, int tileSize) {
        strokeVariable(dst, w, h, curve, t -> width, color, cap, join, dirtyTiles, tileSize);
    }

    // ═══ 可变宽度描边（全画布） ═══
    public static void strokeVariable(float[] dst, int w, int h,
                                      Curve curve, DoubleUnaryOperator widthFunc,
                                      float[] color, Cap cap, Join join) {
        strokeVariable(dst, w, h, curve, widthFunc, color, cap, join, null, 0);
    }

    /**
     * 可变宽度描边，支持脏瓦片集合。
     */
    public static void strokeVariable(float[] dst, int w, int h,
                                      Curve curve, DoubleUnaryOperator widthFunc,
                                      float[] color, Cap cap, Join join,
                                      Set<Long> dirtyTiles, int tileSize) {
        double[] flat = CurveUtils.flattenCurveAdaptive(curve, FLATNESS);
        if (flat.length < 4) {
            DoublesPools.getPool(flat.length).release(flat);
            return;
        }
        double[] outline = Shape.createStrokeOutline(flat, widthFunc, cap, join, MITER_LIMIT);
        DoublesPools.getPool(flat.length).release(flat);

        if (outline.length < 6) {
            DoublesPools.getPool(outline.length).release(outline);
            return;
        }
        if (dirtyTiles == null) {
            fillPolygon(dst, w, h, outline, color, FillRule.EVEN_ODD);
        } else if (dirtyTiles.isEmpty()) {
            // 无脏，直接返回
        } else {
            for (long key : dirtyTiles) {
                int tx = TiledCanvas.unpackTx(key);
                int ty = TiledCanvas.unpackTy(key);
                int startX = Math.max(0, tx * tileSize);
                int startY = Math.max(0, ty * tileSize);
                int endX = Math.min(startX + tileSize, w);
                int endY = Math.min(startY + tileSize, h);
                if (startX >= endX || startY >= endY) continue;
                int rectW = endX - startX;
                int rectH = endY - startY;
                double[] clipped = clipPolygonToRect(outline, startX, startY, rectW, rectH);
                if (clipped != null && clipped.length >= 6) {
                    fillPolygon(dst, w, h, clipped, color, FillRule.EVEN_ODD);
                    DoublesPools.getPool(clipped.length).release(clipped);
                }
            }
        }
        DoublesPools.getPool(outline.length).release(outline);
    }

    // ═══ 曲线间填充（暂不支持脏瓦片） ═══
    public static void fillBetweenCurves(float[] dst, int w, int h,
                                         float[] x1, float[] y1,
                                         float[] x2, float[] y2,
                                         float[] color) {
        int n = Math.min(x1.length, x2.length);
        if (n < 2) return;
        double[] poly = DoublesPools.getPool(n * 4).acquire();
        int idx = 0;
        for (int i = 0; i < n; i++) {
            poly[idx++] = x1[i];
            poly[idx++] = y1[i];
        }
        for (int i = n - 1; i >= 0; i--) {
            poly[idx++] = x2[i];
            poly[idx++] = y2[i];
        }
        fillPolygon(dst, w, h, poly, color, FillRule.EVEN_ODD);
        DoublesPools.getPool(poly.length).release(poly);
    }

    // ═══ 多边形填充（扫描线） ═══
    private static void fillPolygon(float[] dst, int w, int h,
                                    double[] coords, float[] color, FillRule rule) {
        int n = coords.length / 2;
        if (n < 3) return;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (float) coords[i * 2];
            ys[i] = (float) coords[i * 2 + 1];
        }
        scanlineFill(dst, w, h, xs, ys, color, rule);
    }

    private static void scanlineFill(float[] dst, int w, int h,
                                     float[] xs, float[] ys,
                                     float[] color, FillRule rule) {
        int n = xs.length;
        List<List<Edge>> buckets = new ArrayList<>(h);
        for (int i = 0; i < h; i++) buckets.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float x1 = xs[i], y1 = ys[i];
            float x2 = xs[j], y2 = ys[j];
            if (y1 == y2) continue;
            Edge edge;
            if (y1 < y2) {
                edge = new Edge(x1, y1, x2, y2);
            } else {
                edge = new Edge(x2, y2, x1, y1);
            }
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
            } else {
                int wind = 0;
                for (int i = 0; i < active.size(); i++) {
                    wind += active.get(i).wind;
                    if (wind != 0 && i + 1 < active.size()) {
                        fillSpan(dst, w, y, active.get(i).x, active.get(i+1).x, color);
                    }
                }
            }
            for (Edge e : active) e.x += e.dx;
        }
    }

    private static void fillSpan(float[] dst, int w, int y,
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

    // ═══ 多边形裁剪到矩形（Sutherland–Hodgman） ═══
    private static double[] clipPolygonToRect(double[] poly, int rx, int ry, int rw, int rh) {
        if (poly == null || poly.length < 6) return null;
        double xmin = rx;
        double xmax = rx + rw;
        double ymin = ry;
        double ymax = ry + rh;

        double[] current = poly;
        current = clipEdge(current, xmin, true, true);
        if (current == null) return null;
        current = clipEdge(current, xmax, false, true);
        if (current == null) return null;
        current = clipEdge(current, ymin, true, false);
        if (current == null) return null;
        current = clipEdge(current, ymax, false, false);
        return current;
    }

    private static double[] clipEdge(double[] poly, double limit, boolean keepGreater, boolean isX) {
        if (poly.length < 6) return null;
        DoubleArrayBuilder out = new DoubleArrayBuilder(poly.length / 2 + 4);
        int n = poly.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x1 = poly[2 * i];
            double y1 = poly[2 * i + 1];
            double x2 = poly[2 * j];
            double y2 = poly[2 * j + 1];

            double v1 = isX ? x1 : y1;
            double v2 = isX ? x2 : y2;

            boolean inside1 = keepGreater ? (v1 >= limit) : (v1 <= limit);
            boolean inside2 = keepGreater ? (v2 >= limit) : (v2 <= limit);

            if (inside1 && inside2) {
                out.add(x2, y2);
            } else if (inside1) {
                double t = (limit - v1) / (v2 - v1);
                double ix = x1 + t * (x2 - x1);
                double iy = y1 + t * (y2 - y1);
                out.add(ix, iy);
            } else if (inside2) {
                double t = (limit - v1) / (v2 - v1);
                double ix = x1 + t * (x2 - x1);
                double iy = y1 + t * (y2 - y1);
                out.add(ix, iy);
                out.add(x2, y2);
            }
        }
        double[] result = out.toArray();
        out.dispose(); // 释放构建器内部数组
        return result.length >= 6 ? result : null;
    }

    // ═══ 边数据结构 ═══
    private static class Edge {
        final int ymin, ymax;
        final double dx;
        final int wind;
        double x;
        Edge(float x1, float y1, float x2, float y2) {
            this.ymin = (int) Math.ceil(y1);
            this.ymax = (int) Math.ceil(y2);
            this.dx = (x2 - x1) / (y2 - y1);
            this.x = x1 + dx * (ymin - y1);
            this.wind = (y1 < y2) ? 1 : -1;
        }
    }
}