package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 矢量光栅化器：将矢量形状（填充、描边）渲染到图层自己的像素缓冲区。
 * <p>
 * 职责：
 * <ul>
 *   <li>曲线自适应扁平化（委托 {@link CurveUtils}）</li>
 *   <li>描边轮廓生成（委托 {@link Shape}）</li>
 *   <li>多边形扫描线填充（带 alpha 混合的简单 source-over）</li>
 *   <li>脏矩形优化：仅渲染指定的单个矩形区域，减少像素操作</li>
 * </ul>
 * 不处理图层级混合模式、不透明度或变换——这些由光栅图层的合成器完成。
 */
public final class Rasterizer {

    private static final double FLATNESS = 0.25;
    private static final double MITER_LIMIT = 4.0;

    // ═══ 填充（全画布） ═══
    public static void fill(float[] dst, int w, int h,
                            Curve curve, float[] color, FillRule rule) {
        fill(dst, w, h, curve, color, rule, null);
    }

    // ═══ 填充（脏矩形优化，接受单个 int[] 矩形或 null） ═══
    public static void fill(float[] dst, int w, int h,
                            Curve curve, float[] color, FillRule rule,
                            int[] dirtyRect) {
        double[] flat = CurveUtils.flattenCurveAdaptive(curve, FLATNESS);
        if (flat.length < 4) {
            DoubleArrayPool.returnArray(flat);
            return;
        }
        if (dirtyRect == null || dirtyRect.length < 4) {
            fillPolygon(dst, w, h, flat, color, rule);
        } else {
            double[] clipped = clipPolygonToRect(flat,
                    dirtyRect[0], dirtyRect[1], dirtyRect[2], dirtyRect[3]);
            if (clipped != null && clipped.length >= 6) {
                fillPolygon(dst, w, h, clipped, color, rule);
            }
        }
        DoubleArrayPool.returnArray(flat);
    }

    // ═══ 固定宽度描边（全画布） ═══
    public static void strokeFixed(float[] dst, int w, int h,
                                   Curve curve, float width,
                                   float[] color, Cap cap, Join join) {
        strokeVariable(dst, w, h, curve, t -> width, color, cap, join, null);
    }

    // ═══ 固定宽度描边（脏矩形优化） ═══
    public static void strokeFixedDirty(float[] dst, int w, int h,
                                        Curve curve, float width,
                                        float[] color, Cap cap, Join join,
                                        int[] dirtyRect) {
        strokeVariable(dst, w, h, curve, t -> width, color, cap, join, dirtyRect);
    }

    // ═══ 可变宽度描边（全画布） ═══
    public static void strokeVariable(float[] dst, int w, int h,
                                      Curve curve, DoubleUnaryOperator widthFunc,
                                      float[] color, Cap cap, Join join) {
        strokeVariable(dst, w, h, curve, widthFunc, color, cap, join, null);
    }

    // ═══ 可变宽度描边（脏矩形优化，核心实现） ═══
    public static void strokeVariable(float[] dst, int w, int h,
                                      Curve curve, DoubleUnaryOperator widthFunc,
                                      float[] color, Cap cap, Join join,
                                      int[] dirtyRect) {
        double[] flat = CurveUtils.flattenCurveAdaptive(curve, FLATNESS);
        if (flat.length < 4) {
            DoubleArrayPool.returnArray(flat);
            return;
        }
        double[] outline = Shape.createStrokeOutline(flat, widthFunc, cap, join, MITER_LIMIT);
        DoubleArrayPool.returnArray(flat);

        if (outline.length < 6) {
            DoubleArrayPool.returnArray(outline);
            return;
        }
        if (dirtyRect == null || dirtyRect.length < 4) {
            fillPolygon(dst, w, h, outline, color, FillRule.EVEN_ODD);
        } else {
            double[] clipped = clipPolygonToRect(outline,
                    dirtyRect[0], dirtyRect[1], dirtyRect[2], dirtyRect[3]);
            if (clipped != null && clipped.length >= 6) {
                fillPolygon(dst, w, h, clipped, color, FillRule.EVEN_ODD);
            }
        }
        DoubleArrayPool.returnArray(outline);
    }
    // ═══ 曲线间填充（暂不提供脏矩形版本） ═══
    public static void fillBetweenCurves(float[] dst, int w, int h,
                                         float[] x1, float[] y1,
                                         float[] x2, float[] y2,
                                         float[] color) {
        int n = Math.min(x1.length, x2.length);
        if (n < 2) return;
        double[] poly = DoubleArrayPool.borrow(n * 4);
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
        DoubleArrayPool.returnArray(poly);
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
        if (x1 > x2) { double tmp = x1; x1 = x2; x2 = tmp; } // 确保升序
        int start = (int) Math.floor(x1);
        int end   = (int) Math.floor(x2);
        if (start >= w || end < 0 || start > end) return;

        float srcR = color[0], srcG = color[1], srcB = color[2], srcA = color[3];
        for (int x = start; x <= end && x < w; x++) {
            if (x < 0) continue; // 简单跳过负像素
            double cover = 1.0;
            if (x == start) cover = (start + 1) - x1;      // 左边缘部分覆盖
            if (x == end)   cover = x2 - end;               // 右边缘部分覆盖
            // 处理单像素跨度 (start == end) 的情况
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
            } else if (inside1 && !inside2) {
                double t = (limit - v1) / (v2 - v1);
                double ix = x1 + t * (x2 - x1);
                double iy = y1 + t * (y2 - y1);
                out.add(ix, iy);
            } else if (!inside1 && inside2) {
                double t = (limit - v1) / (v2 - v1);
                double ix = x1 + t * (x2 - x1);
                double iy = y1 + t * (y2 - y1);
                out.add(ix, iy);
                out.add(x2, y2);
            }
        }
        double[] result = out.toArray();
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