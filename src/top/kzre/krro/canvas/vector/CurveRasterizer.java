package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;
import top.kzre.krro.util.pool.DoublesPools;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * 矢量光栅化器：将矢量形状（填充、描边）渲染到图层自己的像素缓冲区。
 * 支持基于瓦片脏区域的增量渲染，以及可选的超采样抗锯齿。
 * 所有临时 double[] 使用 {@link DoublesPools} 进行池化，避免频繁 GC。
 */
public final class CurveRasterizer {

    private static final double FLATNESS = 0.25;
    private static final double MITER_LIMIT = 4.0;
    private static final int SSAA_2x2_SCALE = 2;

    // ═══ 填充（全画布便捷版） ═══
    public static void fill(float[] dst, int w, int h,
                            Curve curve, float[] color, FillRule rule,
                            AntiAlias antiAlias) {
        fill(dst, w, h, curve, color, rule, antiAlias, null, 0);
    }

    // ═══ 填充（完整版，支持脏瓦片） ═══
    public static void fill(float[] dst, int w, int h,
                            Curve curve, float[] color, FillRule rule,
                            AntiAlias antiAlias,
                            Set<Long> dirtyTiles, int tileSize) {
        double[] flat = CurveUtils.flattenCurveAdaptive(curve, FLATNESS);
        if (flat.length < 4) {
            DoublesPools.getPool(flat.length).release(flat);
            return;
        }
        if (dirtyTiles == null) {
            fillPolygon(dst, w, h, flat, color, rule, antiAlias);
        } else if (!dirtyTiles.isEmpty()) {
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
                    fillPolygon(dst, w, h, clipped, color, rule, antiAlias);
                    DoublesPools.getPool(clipped.length).release(clipped);
                }
            }
        }
        DoublesPools.getPool(flat.length).release(flat);
    }

    // ═══ 固定宽度描边 ═══
    public static void strokeFixed(float[] dst, int w, int h,
                                   Curve curve, float width,
                                   float[] color, Cap cap, Join join,
                                   AntiAlias antiAlias,
                                   Set<Long> dirtyTiles, int tileSize) {
        strokeVariable(dst, w, h, curve, t -> width, color, cap, join,
                antiAlias, dirtyTiles, tileSize);
    }

    // ═══ 可变宽度描边（核心实现） ═══
    public static void strokeVariable(float[] dst, int w, int h,
                                      Curve curve, DoubleUnaryOperator widthFunc,
                                      float[] color, Cap cap, Join join,
                                      AntiAlias antiAlias,
                                      Set<Long> dirtyTiles, int tileSize) {
        List<Segment> segments = curve.getSegments();
        if (segments.isEmpty()) return;

        int totalSegments = segments.size();
        double prevRightEndX = 0, prevRightEndY = 0;
        double prevVertexX = 0, prevVertexY = 0;
        boolean hasPrev = false;

        for (int i = 0; i < totalSegments; i++) {
            Segment seg = segments.get(i);
            double[] segFlat = CurveUtils.flattenSegment(seg, FLATNESS);
            if (segFlat.length < 4) {
                DoublesPools.getPool(segFlat.length).release(segFlat);
                continue;
            }

            // 为当前段计算宽度函数（线性插值 t 到整个曲线）
            final double segStartT = (double) i / totalSegments;
            final double segEndT = (double) (i + 1) / totalSegments;
            DoubleUnaryOperator segWidthFunc = t -> {
                double globalT = segStartT + t * (segEndT - segStartT);
                return widthFunc.applyAsDouble(globalT);
            };

            // 计算当前段起点和终点的法线及关键边缘点（用于段间连接）
            int lastIdx = (segFlat.length / 2 - 1) * 2;
            double dx0 = segFlat[2] - segFlat[0];
            double dy0 = segFlat[3] - segFlat[1];
            double len0 = Math.hypot(dx0, dy0);
            double nx0 = 0, ny0 = 1;
            if (len0 > 1e-12) { nx0 = -dy0 / len0; ny0 = dx0 / len0; }
            double hw0 = segWidthFunc.applyAsDouble(0.0) / 2.0;
            double currLeftX = segFlat[0] + nx0 * hw0;
            double currLeftY = segFlat[1] + ny0 * hw0;

            double dx1 = segFlat[lastIdx] - segFlat[lastIdx - 2];
            double dy1 = segFlat[lastIdx + 1] - segFlat[lastIdx - 1];
            double len1 = Math.hypot(dx1, dy1);
            double nx1 = 0, ny1 = 1;
            if (len1 > 1e-12) { nx1 = -dy1 / len1; ny1 = dx1 / len1; }
            double hw1 = segWidthFunc.applyAsDouble(1.0) / 2.0;
            double endRightX = segFlat[lastIdx] - nx1 * hw1;
            double endRightY = segFlat[lastIdx + 1] - ny1 * hw1;

            // 非首段：绘制连接前一段右侧终点到当前段左侧起点的多边形
            if (hasPrev) {
                double[] joinOutline = buildJoinPolygon(join,
                        prevRightEndX, prevRightEndY,
                        currLeftX, currLeftY,
                        prevVertexX, prevVertexY, hw0, MITER_LIMIT);
                if (joinOutline != null && joinOutline.length >= 6) {
                    renderOutlinePolygon(dst, w, h, joinOutline, color, dirtyTiles, tileSize, antiAlias);
                    DoublesPools.getPool(joinOutline.length).release(joinOutline);
                }
            }

            // 仅在第一段添加起点 Cap，最后一段添加终点 Cap
            boolean capStart = (i == 0);
            boolean capEnd   = (i == totalSegments - 1);

            // 生成该段的描边轮廓
            double[] outline = createStrokeOutline(segFlat, segWidthFunc, cap, join, MITER_LIMIT, capStart, capEnd);
            DoublesPools.getPool(segFlat.length).release(segFlat);

            if (outline.length < 6) {
                DoublesPools.getPool(outline.length).release(outline);
                continue;
            }

            // 立即填充该段轮廓到目标缓冲区
            renderOutlinePolygon(dst, w, h, outline, color, dirtyTiles, tileSize, antiAlias);
            DoublesPools.getPool(outline.length).release(outline);

            // 保存当前段终点信息用于下一段连接
            prevRightEndX = endRightX;
            prevRightEndY = endRightY;
            prevVertexX = segFlat[lastIdx];
            prevVertexY = segFlat[lastIdx + 1];
            hasPrev = true;
        }
    }

    // ═══ 描边轮廓生成（支持 Cap 控制） ═══
    private static double[] createStrokeOutline(double[] coords,
                                                DoubleUnaryOperator widthFunc,
                                                Cap cap, Join join,
                                                double miterLimit,
                                                boolean capStart, boolean capEnd) {
        int n = coords.length / 2;
        if (n < 2) return new double[0];

        double[] halfW = new double[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            halfW[i] = widthFunc.applyAsDouble(t) / 2.0;
        }

        double[] nx = new double[n], ny = new double[n];
        for (int i = 0; i < n; i++) {
            double dx, dy;
            if (i == 0) {
                dx = coords[2] - coords[0]; dy = coords[3] - coords[1];
            } else if (i == n - 1) {
                dx = coords[2*i] - coords[2*i-2]; dy = coords[2*i+1] - coords[2*i-1];
            } else {
                dx = coords[2*i+2] - coords[2*i-2]; dy = coords[2*i+3] - coords[2*i-1];
            }
            double len = Math.hypot(dx, dy);
            if (len < 1e-12) {
                nx[i] = 0; ny[i] = 1;
            } else {
                nx[i] = -dy / len; ny[i] = dx / len;
            }
        }

        double[] leftX = new double[n], leftY = new double[n];
        double[] rightX = new double[n], rightY = new double[n];
        for (int i = 0; i < n; i++) {
            leftX[i] = coords[2*i] + nx[i] * halfW[i]; leftY[i] = coords[2*i+1] + ny[i] * halfW[i];
            rightX[i] = coords[2*i] - nx[i] * halfW[i]; rightY[i] = coords[2*i+1] - ny[i] * halfW[i];
        }

        DoubleArrayBuilder poly = new DoubleArrayBuilder(n * 8);

        // 起点 Cap（仅当 capStart 为真）
        if (capStart) {
            double tx = coords[2] - coords[0], ty = coords[3] - coords[1];
            double tlen = Math.hypot(tx, ty);
            if (tlen > 1e-12) { tx /= tlen; ty /= tlen; }
            CapGenerator.addCap(cap, coords[0], coords[1], tx, ty, halfW[0],
                    rightX[0], rightY[0], leftX[0], leftY[0], poly, true);
        }

        // 左侧边 + Join
        poly.add(leftX[0], leftY[0]);
        for (int i = 1; i < n; i++) {
            JoinGenerator.addJoin(join,
                    leftX[i-1], leftY[i-1], leftX[i], leftY[i],
                    coords[2*i], coords[2*i+1], halfW[i], miterLimit, poly);
            poly.add(leftX[i], leftY[i]);
        }

        // 终点 Cap（仅当 capEnd 为真）
        if (capEnd) {
            int last = n - 1;
            double lx = coords[2*last] - coords[2*last-2], ly = coords[2*last+1] - coords[2*last-1];
            double ll = Math.hypot(lx, ly);
            if (ll > 1e-12) { lx /= ll; ly /= ll; }
            CapGenerator.addCap(cap, coords[2*last], coords[2*last+1], -lx, -ly, halfW[last],
                    rightX[last], rightY[last], leftX[last], leftY[last], poly, false);
        }

        // 右侧边反向 + Join
        poly.add(rightX[n - 1], rightY[n - 1]);
        for (int i = n - 2; i >= 0; i--) {
            JoinGenerator.addJoin(join,
                    rightX[i+1], rightY[i+1],   // 逆序中的前一个点
                    rightX[i], rightY[i],       // 当前点
                    coords[2*i], coords[2*i+1], halfW[i], miterLimit, poly);
            poly.add(rightX[i], rightY[i]);
        }

        return poly.toArray();
    }

    // 辅助：构建段间连接多边形
    private static double[] buildJoinPolygon(Join join,
                                             double prevRightX, double prevRightY,
                                             double currLeftX, double currLeftY,
                                             double vertexX, double vertexY,
                                             double halfWidth, double miterLimit) {
        DoubleArrayBuilder b = new DoubleArrayBuilder(12);
        b.add(prevRightX, prevRightY);
        JoinGenerator.addJoin(join, prevRightX, prevRightY, currLeftX, currLeftY,
                vertexX, vertexY, halfWidth, miterLimit, b);
        b.add(currLeftX, currLeftY);
        b.add(vertexX, vertexY);
        return b.toArray();
    }

    // 辅助：将轮廓多边形渲染到目标（支持脏区域裁剪）
    private static void renderOutlinePolygon(float[] dst, int w, int h,
                                             double[] outline, float[] color,
                                             Set<Long> dirtyTiles, int tileSize,
                                             AntiAlias antiAlias) {
        if (dirtyTiles == null) {
            fillPolygon(dst, w, h, outline, color, FillRule.EVEN_ODD, antiAlias);
        } else if (!dirtyTiles.isEmpty()) {
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
                    fillPolygon(dst, w, h, clipped, color, FillRule.EVEN_ODD, antiAlias);
                    DoublesPools.getPool(clipped.length).release(clipped);
                }
            }
        }
    }

    // ═══ 多边形填充（集成超采样） ═══
    private static void fillPolygon(float[] dst, int w, int h,
                                    double[] coords, float[] color, FillRule rule,
                                    AntiAlias antialiasing) {
        int n = coords.length / 2;
        if (n < 3) return;

        if (antialiasing == AntiAlias.SSAA_2x2) {
            int sw = w * SSAA_2x2_SCALE;
            int sh = h * SSAA_2x2_SCALE;
            float[] temp = new float[sw * sh * 4]; // 超采样缓冲区
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = (float)(coords[2*i] * SSAA_2x2_SCALE);
                ys[i] = (float)(coords[2*i+1] * SSAA_2x2_SCALE);
            }
            scanlineFill(temp, sw, sh, xs, ys, color, rule);
            downsample(temp, sw, sh, dst, w, h, SSAA_2x2_SCALE);
        } else {
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = (float) coords[2*i];
                ys[i] = (float) coords[2*i+1];
            }
            scanlineFill(dst, w, h, xs, ys, color, rule);
        }
    }

    private static void downsample(float[] src, int sw, int sh,
                                   float[] dst, int dw, int dh, int scale) {
        float scaleSq = scale * scale;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                float r = 0, g = 0, b = 0, a = 0;
                int count = 0;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int sx = x * scale + dx;
                        int sy = y * scale + dy;
                        if (sx < sw && sy < sh) {
                            int idx = (sy * sw + sx) * 4;
                            float sa = src[idx+3];
                            if (sa > 0) {
                                r += src[idx];
                                g += src[idx+1];
                                b += src[idx+2];
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
        out.dispose();
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