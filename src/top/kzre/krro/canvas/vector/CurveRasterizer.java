package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayList;
import java.util.List;

public final class CurveRasterizer {
    private final CurveFlattener flattener;
    private final CurveRenderer renderer;

    public CurveRasterizer(CurveFlattener flattener,
                           CurveRenderer renderer) {
        this.flattener = flattener;
        this.renderer = renderer;
    }

    public void rasterize(Curve curve, RenderContext context){
        int w = context.getWidth();
        int h = context.getHeight();
        List<Curve> visibleCurves = new ArrayList<>();
        clipCurve(visibleCurves, curve, w, h);
        for (Curve c : visibleCurves) {
            rasterizeSingleCurve(c, context);
        }

    }

    private void rasterizeSingleCurve(Curve curve, RenderContext context) {
        Path path = flattener.flatten(curve);
        renderer.render(path, context);
    }

    private static void clipCurve(List<Curve> out, Curve curve, int width, int height) {
        if (curve == null || curve.getPoints().isEmpty()) return;

        // 闭合曲线直接添加，无需裁剪
        if (curve.isClosed()) {
            out.add(curve);
            return;
        }

        int segCount = curve.getSegmentCount();
        List<ControlPoint> currentPoints = new ArrayList<>();

        for (int i = 0; i < segCount; i++) {
            ControlPoint cpStart = curve.getPoints().get(i);
            ControlPoint cpEnd   = curve.getPoints().get((i + 1) % curve.getPoints().size());

            boolean visible;
            if (Segments.isStraightLine(cpStart, cpEnd)) {
                // 快速路径：退化（直线）段，仅用端点判断包围盒
                double minX = Math.min(cpStart.getX(), cpEnd.getX());
                double maxX = Math.max(cpStart.getX(), cpEnd.getX());
                double minY = Math.min(cpStart.getY(), cpEnd.getY());
                double maxY = Math.max(cpStart.getY(), cpEnd.getY());
                visible = !(maxX < 0 || minX > width || maxY < 0 || minY > height);
            } else {
                // 曲线段：计算精确的贝塞尔包围盒
                Segment seg = curve.getSegment(i);
                AABB aabb = Segments.aabb(seg);
                visible = !(aabb.getMaxX() < 0 || aabb.getMinX() > width ||
                        aabb.getMaxY() < 0 || aabb.getMinY() > height);
            }

            if (visible) {
                if (currentPoints.isEmpty()) {
                    currentPoints.add(cpStart);
                }
                currentPoints.add(cpEnd);
            } else {
                if (!currentPoints.isEmpty()) {
                    out.add(new Curve(currentPoints, false));
                    currentPoints = new ArrayList<>();
                }
            }
        }
        if (!currentPoints.isEmpty()) {
            out.add(new Curve(currentPoints, false));
        }
    }

    public static Builder builder(){
        return new Builder();
    }

    public static final class Builder {

        private CurveFlattener flattener;
        private final List<CurveRenderer> renderers = new ArrayList<>();


        /**
         * 设置曲线展平器（必须调用）。
         *
         * @param flattener 展平器实例，不能为 null
         * @return 当前构建器
         * @throws IllegalArgumentException 如果 flattener 为 null
         */
        public Builder flattener(CurveFlattener flattener) {
            if (flattener == null) {
                throw new IllegalArgumentException("flattener must not be null");
            }
            this.flattener = flattener;
            return this;
        }

        /**
         * 添加一个纯色填充渲染器。
         *
         * @param color 填充颜色，RGBA 浮点数组，长度至少为 4
         * @param rule  填充规则（Even‑Odd 或 Non‑Zero）
         * @return 当前构建器
         */
        public Builder addFill(float[] color, FillRule rule) {
            PolygonFiller filler = (rule == FillRule.EVEN_ODD)
                    ? new EvenOddPolygonFiller(color)
                    : new NonZeroPolygonFiller(color);
            renderers.add(new CurveFill(filler));
            return this;
        }

        /**
         * 添加一个描边渲染器。
         *
         * @param cap         端点样式
         * @param join        连接样式
         * @param color       描边颜色，RGBA 浮点数组，长度至少为 4
         * @param miterLimit  斜接限制（相对于半宽的倍数）
         * @return 当前构建器
         */
        public Builder addStroke(CapStrategy cap, JoinStrategy join,
                                 float[] color, double miterLimit) {
            PolygonFiller filler = new NonZeroPolygonFiller(color);
            renderers.add(new CurveStroke(cap, join, filler, miterLimit));
            return this;
        }

        /**
         * 添加任意自定义渲染器。
         *
         * @param renderer 自定义渲染器实例，不能为 null
         * @return 当前构建器
         * @throws IllegalArgumentException 如果 renderer 为 null
         */
        public Builder addRenderer(CurveRenderer renderer) {
            if (renderer == null) {
                throw new IllegalArgumentException("renderer must not be null");
            }
            renderers.add(renderer);
            return this;
        }

        /**
         * 构建最终的 {@link CurveRasterizer} 实例。
         *
         * @return 构建完成的曲线光栅化器
         * @throws IllegalStateException 如果未设置展平器或未添加任何渲染器
         */
        public CurveRasterizer build() {
            if (flattener == null) {
                throw new IllegalStateException(
                        "CurveFlattener must be set via flattener() before build()");
            }
            if (renderers.isEmpty()) {
                throw new IllegalStateException(
                        "At least one renderer must be added via addFill(), addStroke(), or addRenderer() before build()");
            }

            // 组合所有渲染器
            CurveRenderer combined = renderers.stream()
                    .reduce(CurveRenderer::and)
                    .orElseThrow(() -> new IllegalStateException("No renderers available"));

            return new CurveRasterizer(flattener, combined);
        }
    }

}
