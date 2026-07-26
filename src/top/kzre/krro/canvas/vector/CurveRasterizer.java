package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.ControlPoint;
import top.kzre.curve.bezier2d.Curve;
import top.kzre.curve.bezier2d.Segment;
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
        switch (config.getAntiAlias()) {
            case SSAA_2x2:
                this.aaStrategy = new SSAA2x2();
                break;
            case ANALYTIC:
                this.aaStrategy = new AnalyticAA();
                break;
            default:
                this.aaStrategy = new NoAntiAlias();
                break;
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
            strokeLineAsQuad(dst, w, h, curve, width, width, color,cap, dirtyTiles, tileSize);
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
            strokeLineAsQuad(dst, w, h, curve, w1, w2, color,cap, dirtyTiles, tileSize);
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


    /**
     * 直线快速路径
     */
    private void strokeLineAsQuad(float[] dst, int w, int h, Curve curve,
                                  double startWidth, double endWidth,
                                  float[] color, Cap cap,
                                  Set<Long> dirtyTiles, int tileSize) {
        double[] ends = getLineEndpoints(curve);
        double x1 = ends[0], y1 = ends[1];
        double x2 = ends[2], y2 = ends[3];

        double hw1 = startWidth * 0.5;
        double hw2 = endWidth * 0.5;
        if (hw1 <= 0 && hw2 <= 0) return;

        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        double px = -dy / len, py = dx / len;  // 右侧法线

        // 计算左右侧边缘点
        double rightX1 = x1 + hw1 * px, rightY1 = y1 + hw1 * py;
        double leftX1  = x1 - hw1 * px, leftY1  = y1 - hw1 * py;
        double rightX2 = x2 + hw2 * px, rightY2 = y2 + hw2 * py;
        double leftX2  = x2 - hw2 * px, leftY2  = y2 - hw2 * py;

        DoubleList polyBuilder = new DoubleList(32);

        // 起点 cap (isStart = true)
        // 切线方向指向线段内部，即从 x1 到 x2 的方向
        outliner.addCap(cap, x1, y1, dx/len, dy/len, hw1,
                rightX1, rightY1, leftX1, leftY1, polyBuilder, true);

        // 右侧边（从起点到终点）
        polyBuilder.add(rightX1, rightY1);
        // 如果需要 join，这里可以处理，但直线只有一个段，没有 join
        polyBuilder.add(rightX2, rightY2);

        // 终点 cap (isStart = false)
        outliner.addCap(cap, x2, y2, -dx/len, -dy/len, hw2,
                rightX2, rightY2, leftX2, leftY2, polyBuilder, false);

        // 左侧边反向（从终点回到起点）
        polyBuilder.add(leftX2, leftY2);
        polyBuilder.add(leftX1, leftY1);

        // 闭合多边形
        polyBuilder.add(rightX1, rightY1); // 连接回起点 cap 的起点

        double[] poly = polyBuilder.toArray();
        renderPolygon(dst, w, h, poly, color, FillRule.NON_ZERO, dirtyTiles, tileSize);
    }

    private static double[] getLineEndpoints(Curve curve) {
        List<ControlPoint> points = curve.getPoints();
        if (points.size() != 2) {
            throw new IllegalArgumentException("Curve must have exactly 2 control points for a straight line");
        }
        ControlPoint p0 = points.get(0);
        ControlPoint p1 = points.get(1);
        return new double[]{ p0.getX(), p0.getY(), p1.getX(), p1.getY() };
    }

    private static boolean isStraightLine(Curve curve) {
        List<ControlPoint> points = curve.getPoints();
        // 直线只有两个控制点（起点和终点），且曲线不闭合
        if (points.size() != 2 || curve.isClosed()) return false;

        ControlPoint p0 = points.get(0);
        ControlPoint p1 = points.get(1);

        // 检查内部控制点是否与端点重合（手柄为0）
        boolean p0Straight = Math.abs(p0.getDx2()) < 1e-6 && Math.abs(p0.getDy2()) < 1e-6;   // 起点出射手柄
        boolean p1Straight = Math.abs(p1.getDx1()) < 1e-6 && Math.abs(p1.getDy1()) < 1e-6;   // 终点入射手柄

        return p0Straight && p1Straight;
    }

}