package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.ControlPoint;
import top.kzre.curve.bezier2d.Curve;
import top.kzre.krro.util.tile.Canvas;
import static top.kzre.krro.canvas.vector.TileClipper.clip;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/**
 * 面向 Canvas 输出的曲线光栅化器。
 * 将填充和描边结果直接写入瓦片画布，避免大块临时数组。
 */
public class CanvasCurveRasterizer {

    private final Flattener flattener;
    private final StrokeOutliner outliner;
    private final AntiAliasPolicy aaPolicy;

    public CanvasCurveRasterizer(RasterizerConfig config) {
        this.flattener = new Flattener(config.getFlatness());
        this.outliner = new StrokeOutliner(config.getMiterLimit(), config.getRoundSteps());
        this.aaPolicy = CanvasAAFactory.create(config.getAntiAlias());
    }

    // ---- 公开渲染 API ----

    /** 填充闭合曲线区域 */
    public void fill(Canvas dest, int w, int h, Curve curve,
                     float[] color, FillRule rule,
                     Set<Long> dirtyTiles, int tileSize) {
        double[] flat = flattener.flatten(curve);
        if (flat.length < 4) return;
        renderPolygon(dest, w, h, flat, color, rule, dirtyTiles, tileSize);
    }


    /**
     * 简单批量描边（固定宽度）。逐条调用 strokeFixed，不做任何合并或加速。
     */
    public void strokeCurvesFixed(Canvas dest, int w, int h, List<StrokeDesc> strokes,
                                  Set<Long> dirtyTiles, int tileSize) {
        for (StrokeDesc sd : strokes) {
            if (sd.isVariableWidth) {
                strokeVariable(dest, w, h, sd.curve, sd.widthFunc,
                        sd.color, sd.cap, sd.join, dirtyTiles, tileSize);
            } else {
                strokeFixed(dest, w, h, sd.curve, sd.fixedWidth,
                        sd.color, sd.cap, sd.join, dirtyTiles, tileSize);
            }
        }
    }

    /** 固定宽度描边 */
    public void strokeFixed(Canvas dest, int w, int h, Curve curve,
                            float width, float[] color,
                            Cap cap, Join join,
                            Set<Long> dirtyTiles, int tileSize) {
        if (width <= 0) return;
        strokeVariable(dest, w, h, curve, t -> (double) width, color, cap, join, dirtyTiles, tileSize);
    }

    /** 可变宽度描边 */
    public void strokeVariable(Canvas dest, int w, int h, Curve curve,
                               DoubleUnaryOperator widthFunc,
                               float[] color, Cap cap, Join join,
                               Set<Long> dirtyTiles, int tileSize) {
        // 1. 对曲线进行段级裁剪，丢弃完全在画布外的部分
        List<Curve> visibleCurves = new ArrayList<>();
        clip(visibleCurves, curve, w, h);

        for (Curve subCurve : visibleCurves) {
            if (isStraightLine(subCurve)) {
                // 直线快速路径
                double w1 = widthFunc.applyAsDouble(0);
                double w2 = widthFunc.applyAsDouble(1);
                strokeLineAsQuad(dest, w, h, subCurve, w1, w2, color, cap, dirtyTiles, tileSize);
            } else {
                // 一般曲线：展平 → 轮廓 → 填充轮廓多边形
                double[] flat = flattener.flatten(subCurve);
                if (flat.length < 4) continue;
                double[] outline = outliner.outline(flat, widthFunc, cap, join);
                if (outline.length >= 6) {
                    renderPolygon(dest, w, h, outline, color, FillRule.NON_ZERO, dirtyTiles, tileSize);
                }
            }
        }
    }

    // ---- 内部渲染 ----

    /** 将多边形渲染到 Canvas 的脏瓦片 */
    private void renderPolygon(Canvas dest, int w, int h,
                               double[] polygon, float[] color,
                               FillRule rule,
                               Set<Long> dirtyTiles, int tileSize) {
        aaPolicy.fill(dest, w, h, polygon, color, rule, dirtyTiles, tileSize);
    }

    /** 直线描边快速路径：直接生成四边形轮廓 */
    private void strokeLineAsQuad(Canvas dest, int w, int h, Curve curve,
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

        double rightX1 = x1 + hw1 * px, rightY1 = y1 + hw1 * py;
        double leftX1  = x1 - hw1 * px, leftY1  = y1 - hw1 * py;
        double rightX2 = x2 + hw2 * px, rightY2 = y2 + hw2 * py;
        double leftX2  = x2 - hw2 * px, leftY2  = y2 - hw2 * py;

        DoubleList poly = new DoubleList(32);

        // 起点 cap
        outliner.addCap(cap, x1, y1, dx/len, dy/len, hw1,
                rightX1, rightY1, leftX1, leftY1, poly, true);

        // 右侧边
        poly.add(rightX1, rightY1);
        poly.add(rightX2, rightY2);

        // 终点 cap
        outliner.addCap(cap, x2, y2, -dx/len, -dy/len, hw2,
                rightX2, rightY2, leftX2, leftY2, poly, false);

        // 左侧边反向
        poly.add(leftX2, leftY2);
        poly.add(leftX1, leftY1);

        // 闭合
        poly.add(rightX1, rightY1);

        double[] polygon = poly.toArray();
        renderPolygon(dest, w, h, polygon, color, FillRule.NON_ZERO, dirtyTiles, tileSize);
    }

    // ---- 加速结构辅助方法 ----

    /** 生成固定宽度描边的多边形列表（用于加速结构） */
    private List<double[]> generateFixedStrokePolygons(StrokeDesc sd) {
        List<double[]> result = new ArrayList<>();
        if (isStraightLine(sd.curve) && sd.cap == Cap.BUTT) {
            // 直线且无端点装饰：直接生成矩形四边形（利于合并）
            double[] quad = generateLineQuad(sd.curve, sd.fixedWidth);
            if (quad != null) result.add(quad);
        } else {
            // 曲线或带 cap 的直线：使用标准轮廓生成
            double[] outline = generateOutline(sd.curve, t -> (double) sd.fixedWidth, sd.cap, sd.join);
            if (outline != null) result.add(outline);
        }
        return result;
    }

    /** 生成可变宽度描边的多边形列表（用于加速结构） */
    private List<double[]> generateVariableStrokePolygons(StrokeDesc sd) {
        List<double[]> result = new ArrayList<>();
        double[] outline = generateOutline(sd.curve, sd.widthFunc, sd.cap, sd.join);
        if (outline != null) result.add(outline);
        return result;
    }

    /** 通用轮廓生成（展平 + StrokeOutliner） */
    private double[] generateOutline(Curve curve, DoubleUnaryOperator widthFunc, Cap cap, Join join) {
        double[] flat = flattener.flatten(curve);
        if (flat.length < 4) return null;
        double[] outline = outliner.outline(flat, widthFunc, cap, join);
        return (outline.length >= 6) ? outline : null;
    }

    /** 生成直线段的矩形四边形（忽略 cap） */
    private double[] generateLineQuad(Curve curve, double width) {
        double[] ends = getLineEndpoints(curve);
        double x1 = ends[0], y1 = ends[1], x2 = ends[2], y2 = ends[3];
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return null;
        double hw = width * 0.5;
        double px = -dy / len * hw;
        double py = dx / len * hw;

        return new double[] {
                x1 + px, y1 + py,
                x2 + px, y2 + py,
                x2 - px, y2 - py,
                x1 - px, y1 - py
        };
    }

    // ---- 通用辅助方法 ----

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
        if (points.size() != 2 || curve.isClosed()) return false;
        ControlPoint p0 = points.get(0);
        ControlPoint p1 = points.get(1);
        boolean p0Straight = Math.abs(p0.getDx2()) < 1e-6 && Math.abs(p0.getDy2()) < 1e-6;
        boolean p1Straight = Math.abs(p1.getDx1()) < 1e-6 && Math.abs(p1.getDy1()) < 1e-6;
        return p0Straight && p1Straight;
    }
}