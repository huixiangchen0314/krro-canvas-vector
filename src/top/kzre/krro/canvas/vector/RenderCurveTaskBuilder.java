package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * 构建渲染曲线任务的 Builder，支持链式配置。
 * <p>
 * 使用示例：
 * <pre>{@code
 * RenderCurveTask task = RenderCurveTaskBuilder.create()
 *     .config(cfg -> cfg.canvas(canvas).size(800,600).dirtyTiles(dirty).aa(AntiAlias.ANALYTIC))
 *     .curve(curve1)
 *         .flatness(0.1)
 *         .fill().color(new float[]{1,0,0,1}).fillRule(FillRule.NON_ZERO).and()
 *         .stroke().color(new float[]{0,0,1,1}).width(2).cap(Cap.ROUND).join(Join.ROUND).and()
 *         .and()
 *     .curve(curve2)
 *         .fill().color(new float[]{0,1,0,1}).fillRule(FillRule.EVEN_ODD)
 *     .build();
 * task.execute();
 * }</pre>
 */
public final class RenderCurveTaskBuilder {

    private final List<Configurer> configurers = new ArrayList<>();
    private final List<RenderableCurve> curves = new ArrayList<>();
    private RenderContext renderContext;
    private final RenderConfigurationConfigurer globalConfig = new RenderConfigurationConfigurer();

    private RenderCurveTaskBuilder() {}

    public static RenderCurveTaskBuilder create() {
        return new RenderCurveTaskBuilder();
    }

    // ---- 全局配置 ----

    /**
     * 配置全局渲染参数（画布、尺寸、脏瓦片、抗锯齿）。
     */
    public RenderConfigurationConfigurer config() {
        return globalConfig;
    }

    // ---- 曲线配置 ----

    /**
     * 开始配置一条曲线。
     * @param curve 待渲染的曲线
     * @return 曲线配置器
     */
    public RenderableCurveConfigurer curve(Curve curve) {
        RenderableCurveConfigurer configurer = new RenderableCurveConfigurer(curve);
        configurers.add(configurer);
        return configurer;
    }

    // ---- 构建任务 ----

    /**
     * 构建渲染任务。
     * @return 可执行的渲染任务
     * @throws IllegalStateException 如果全局配置或曲线配置不完整
     */
    public RenderCurveTask build() {
        // 1. 应用全局配置
        globalConfig.config();
        if (renderContext == null) {
            throw new IllegalStateException(
                    "RenderContext not configured. Call config() with canvas, size, dirtyTiles, aa.");
        }

        // 2. 应用所有曲线配置
        for (Configurer c : configurers) {
            c.config();
        }

        if (curves.isEmpty()) {
            throw new IllegalStateException("No curves configured.");
        }

        return new RenderCurveTask(curves, renderContext);
    }

    // ---- 内部配置器基类 ----

    private abstract static class Configurer {
        public abstract void config();
    }

    // ---- 全局配置器 ----

    public final class RenderConfigurationConfigurer extends Configurer {
        private TiledCanvas canvas;
        private int width = -1;
        private int height = -1;
        private Set<Long> dirtyTiles;
        private AntiAlias aaMode;
        private double scaleX = 1.0;
        private double scaleY = 1.0;

        public RenderConfigurationConfigurer canvas(TiledCanvas canvas) {
            this.canvas = canvas;
            return this;
        }

        public RenderConfigurationConfigurer scale(double sx, double sy) {
            this.scaleX = sx;
            this.scaleY = sy;
            return this;
        }

        public RenderConfigurationConfigurer size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public RenderConfigurationConfigurer dirtyTiles(Set<Long> dirtyTiles) {
            this.dirtyTiles = dirtyTiles;
            return this;
        }

        public RenderConfigurationConfigurer aa(AntiAlias aaMode) {
            this.aaMode = aaMode;
            return this;
        }

        @Override
        public void config() {
            if (canvas == null) {
                throw new IllegalStateException("canvas must be set in global config");
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException("width and height must be set");
            }
            if (dirtyTiles == null) {
                throw new IllegalStateException("dirtyTiles must be set (use empty set if none)");
            }
            if (aaMode == null) {
                throw new IllegalStateException("aaMode must be set");
            }
            AntiAliasStrategy aaStrategy = createAAStrategy(aaMode);
            RenderCurveTaskBuilder.this.renderContext = RenderContext.builder()
                    .destCanvas(canvas)
                    .width(width)
                    .height(height)
                    .scale(scaleX, scaleY)
                    .dirtyTiles(dirtyTiles)
                    .antiAlias(aaStrategy)
                    .build();
        }

        private AntiAliasStrategy createAAStrategy(AntiAlias mode) {
            switch (mode) {
                case NONE: return new NoAAStrategy();
                case ANALYTIC: return new AnalyticAAStrategy();
                default: throw new IllegalArgumentException("Unsupported AA mode: " + mode);
            }
        }
    }

    // ---- 曲线配置器 ----

    public final class RenderableCurveConfigurer extends Configurer {
        private final Curve curve;
        private FillConfigurer fill;
        private StrokeConfigurer stroke;
        // TODO flatness 移动到 RenderContext 作为全局数据
        private double flatness = 0.25f;
        private double widthTolerance = 0.02f;

        private RenderableCurveConfigurer(Curve curve) {
            this.curve = curve;
        }

        // 展平参数
        public RenderableCurveConfigurer flatness(double flatness) {
            this.flatness = flatness;
            return this;
        }

        public RenderableCurveConfigurer widthTolerance(double widthTolerance) {
            this.widthTolerance = widthTolerance;
            return this;
        }

        // 填充配置
        public FillConfigurer fill() {
            if (fill == null) {
                fill = new FillConfigurer();
            }
            return fill;
        }

        // 描边配置
        public StrokeConfigurer stroke() {
            if (stroke == null) {
                stroke = new StrokeConfigurer();
            }
            return stroke;
        }

        // 返回父级构建器
        public RenderCurveTaskBuilder and() {
            return RenderCurveTaskBuilder.this;
        }

        @Override
        public void config() {
            // 应用填充和描边的内部配置
            if (fill != null) fill.config();
            if (stroke != null) stroke.config();

            // 检查是否至少有一种样式
            CurveStyle fillStyle = fill != null ? fill.getStyle() : null;
            CurveStyle strokeStyle = stroke != null ? stroke.getStyle() : null;
            if (fillStyle == null && strokeStyle == null) {
                return; // 没有样式，跳过此曲线
            }

            // 构建样式列表
            List<CurveStyle> styles = new ArrayList<>();
            if (fillStyle != null) styles.add(fillStyle);
            if (strokeStyle != null) styles.add(strokeStyle);

            // 构建展平器
            CurveFlattener flattener;
            if (stroke != null && stroke.widthFunc != null) {
                flattener = new AdaptiveFlattener(flatness, stroke.widthFunc, widthTolerance);
            } else {
                float width = (stroke != null) ? stroke.width : 1.0f;
                flattener = new AdaptiveFlattener(flatness, new FixedWidthFunction(width), widthTolerance);
            }

            // 添加到曲线列表
            RenderCurveTaskBuilder.this.curves.add(
                    new RenderableCurve(curve, flattener, styles)
            );
        }

        // ---- 填充配置器 ----

        public final class FillConfigurer extends Configurer {
            private float[] color;
            private FillRule fillRule = FillRule.NON_ZERO;
            private CurveStyle style;

            public FillConfigurer color(float[] color) {
                this.color = color.clone();
                return this;
            }

            public FillConfigurer fillRule(FillRule fillRule) {
                this.fillRule = fillRule;
                return this;
            }

            public CurveStyle getStyle() {
                return style;
            }

            public RenderableCurveConfigurer and() {
                return RenderableCurveConfigurer.this;
            }

            @Override
            public void config() {
                if (color == null) {
                    throw new IllegalStateException("Fill color must be set");
                }
                PolygonFiller filler = (fillRule == FillRule.EVEN_ODD)
                        ? new EvenOddPolygonFiller(color)
                        : new NonZeroPolygonFiller(color);
                this.style = new CurveStyle(PathRenderer.fill(), filler);
            }
        }

        // ---- 描边配置器 ----

        public final class StrokeConfigurer extends Configurer {
            private float width = 1.0f;
            private WidthFunction widthFunc;
            private float[] color;
            private Cap cap = Cap.ROUND;
            private Join join = Join.ROUND;
            private float miterLimit = 4.0f;
            private CurveStyle style;

            public StrokeConfigurer width(float width) {
                this.width = width;
                return this;
            }

            public StrokeConfigurer widthFunc(WidthFunction widthFunc) {
                this.widthFunc = widthFunc;
                return this;
            }

            public StrokeConfigurer color(float[] color) {
                this.color = color.clone();
                return this;
            }

            public StrokeConfigurer cap(Cap cap) {
                this.cap = cap;
                return this;
            }

            public StrokeConfigurer join(Join join) {
                this.join = join;
                return this;
            }

            public StrokeConfigurer miterLimit(float miterLimit) {
                this.miterLimit = miterLimit;
                return this;
            }

            public CurveStyle getStyle() {
                return style;
            }

            public RenderableCurveConfigurer and() {
                return RenderableCurveConfigurer.this;
            }

            @Override
            public void config() {
                if (color == null) {
                    throw new IllegalStateException("Stroke color must be set");
                }
                CapStrategy capStrategy = createCapStrategy(cap);
                JoinStrategy joinStrategy = createJoinStrategy(join);
                PolygonFiller filler = new NonZeroPolygonFiller(color);
                this.style = new CurveStyle(
                        PathRenderer.stroke(capStrategy, joinStrategy, miterLimit),
                        filler
                );
            }

            private CapStrategy createCapStrategy(Cap cap) {
                switch (cap) {
                    case BUTT: return ButtCap.INSTANCE;
                    case SQUARE: return SquareCap.INSTANCE;
                    case ROUND: return new RoundCap();
                    default: return new RoundCap();
                }
            }

            private JoinStrategy createJoinStrategy(Join join) {
                switch (join) {
                    case MITER: return MiterJoin.INSTANCE;
                    case BEVEL: return BevelJoin.INSTANCE;
                    case ROUND: return new RoundJoin();
                    default: return new RoundJoin();
                }
            }
        }
    }

}