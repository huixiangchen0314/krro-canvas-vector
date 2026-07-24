package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;
import top.kzre.curve.bezier2d.Segment;
import top.kzre.curve.bezier2d.Segments;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

public class CurveRasterizer {
    private final Flattener flattener;
    private final StrokeOutliner outliner;
    private final TileClipper clipper;
    private final AntiAliasStrategy aaStrategy;

    public CurveRasterizer(RasterizerConfig config) {
        this.flattener = new Flattener(config.getFlatness());
        this.outliner = new StrokeOutliner(config.getMiterLimit(), config.getRoundSteps());
        this.clipper = new TileClipper();
        // 根据配置选择抗锯齿策略
        if (config.getAntiAlias() == AntiAlias.SSAA_2x2) {
            this.aaStrategy = new SSAA2x2();
        } else {
            this.aaStrategy = new NoAntiAlias();
        }
    }

    // ---------- 填充 ----------
    public void fill(float[] dst, int w, int h, Curve curve,
                     float[] color, FillRule rule,
                     Set<Long> dirtyTiles, int tileSize) {
        double[] flat = flattener.flatten(curve);
        if (flat.length < 4) return;
        renderPolygon(dst, w, h, flat, color, rule, dirtyTiles, tileSize);
    }

    // ---------- 固定宽度描边 ----------
    public void strokeFixed(float[] dst, int w, int h, Curve curve,
                            float width, float[] color,
                            Cap cap, Join join,
                            Set<Long> dirtyTiles, int tileSize) {

        // 快速路径
        if (width <= 0) return;
        if (isStraightLine(curve)) {
            strokeLineAsQuad(dst, w, h, curve, width, width, color, dirtyTiles, tileSize);
            return;
        }

        strokeVariable(dst, w, h, curve, t -> width, color, cap, join, dirtyTiles, tileSize);
    }

    // ---------- 可变宽度描边 ----------
    public void strokeVariable(float[] dst, int w, int h, Curve curve,
                               DoubleUnaryOperator widthFunc,
                               float[] color, Cap cap, Join join,
                               Set<Long> dirtyTiles, int tileSize) {
        if (isStraightLine(curve)) {
            double w1 = widthFunc.applyAsDouble(0);
            double w2 = widthFunc.applyAsDouble(1);
            strokeLineAsQuad(dst, w, h, curve, w1, w2, color, dirtyTiles, tileSize);
            return;
        }


        double[] flat = flattener.flatten(curve);
        if (flat.length < 4) return;
        double[] outline = outliner.outline(flat, widthFunc, cap, join);
        if (outline.length < 6) return;
        // 描边始终使用 NON_ZERO 规则
        renderPolygon(dst, w, h, outline, color, FillRule.NON_ZERO, dirtyTiles, tileSize);
    }

    // ---------- 内部渲染 ----------
    private void renderPolygon(float[] dst, int w, int h,
                               double[] polygon, float[] color,
                               FillRule rule,
                               Set<Long> dirtyTiles, int tileSize) {
        if (dirtyTiles == null) {
            aaStrategy.fill(dst, w, h, polygon, color, rule);
        } else {
            for (long key : dirtyTiles) {
                int tx = TiledCanvas.unpackTx(key);
                int ty = TiledCanvas.unpackTy(key);
                int startX = Math.max(0, tx * tileSize);
                int startY = Math.max(0, ty * tileSize);
                int endX = Math.min(startX + tileSize, w);
                int endY = Math.min(startY + tileSize, h);
                if (startX >= endX || startY >= endY) continue;
                double[] clipped = clipper.clip(polygon, startX, startY, endX-startX, endY-startY);
                if (clipped != null) {
                    aaStrategy.fill(dst, w, h, clipped, color, rule);
                }
            }
        }
    }

    private void strokeLineAsQuad(float[] dst, int w, int h, Curve curve,
                                  double startWidth, double endWidth,
                                  float[] color, Set<Long> dirtyTiles, int tileSize) {
        double[] ends = getLineEndpoints(curve);
        double x1 = ends[0], y1 = ends[1];
        double x2 = ends[2], y2 = ends[3];
        double hw1 = startWidth * 0.5;
        double hw2 = endWidth * 0.5;

        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        double px = -dy / len, py = dx / len;  // 垂直方向

        double[] poly = new double[8];
        poly[0] = x1 + hw1 * px;  poly[1] = y1 + hw1 * py;
        poly[2] = x2 + hw2 * px;  poly[3] = y2 + hw2 * py;
        poly[4] = x2 - hw2 * px;  poly[5] = y2 - hw2 * py;
        poly[6] = x1 - hw1 * px;  poly[7] = y1 - hw1 * py;

        renderPolygon(dst, w, h, poly, color, FillRule.NON_ZERO, dirtyTiles, tileSize);
    }

    private static double[] getLineEndpoints(Curve curve) {
        Segment seg = curve.getSegments().get(0);
        return new double[]{ seg.getA().getX(), seg.getA().getY(),
                seg.getD().getX(), seg.getD().getY() };
    }

    private static boolean isStraightLine(Curve curve) {
        List<Segment> segs = curve.getSegments();
        if (segs.size() != 1) return false;
        Segment seg = segs.get(0);
        // 获取四个控制点
        double x0 = seg.getA().getX(), y0 = seg.getA().getY();
        double x1 = seg.getB().getX(), y1 = seg.getB().getY();
        double x2 = seg.getC().getX(), y2 = seg.getC().getY();
        double x3 = seg.getD().getX(), y3 = seg.getD().getY();

        // 检查 P1 和 P2 是否都在线段 P0-P3 上
        return isPointOnLineSegment(x1, y1, x0, y0, x3, y3) &&
                isPointOnLineSegment(x2, y2, x0, y0, x3, y3);
    }

    private static boolean isPointOnLineSegment(double px, double py,
                                                double ax, double ay,
                                                double bx, double by) {
        // 叉积判断共线性
        double cross = (py - ay) * (bx - ax) - (px - ax) * (by - ay);
        if (Math.abs(cross) > 0.001) return false; // 不共线
        // 点积判断是否在线段范围内
        double dot = (px - ax) * (bx - ax) + (py - ay) * (by - ay);
        if (dot < 0) return false;
        double len2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay);
        return !(dot > len2);
    }
}