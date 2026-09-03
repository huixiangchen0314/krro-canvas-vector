package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * 为 Clojure 提供的矢量曲线光栅化门面。
 * <p>
 * 使用 Builder 模式配置所有参数（无默认值），构建后调用 {@link #render()} 执行渲染。
 * 所有必要参数（画布、尺寸、脏瓦片、抗锯齿策略、填充/描边样式等）都必须显式设置。
 * 配置使用旧的枚举类型，策略实例由 Builder 内部构造。
 * </p>
 */
public final class RasterizeCurveFacade {

    // ---- 必填参数 ----
    private final List<Curve> curves;
    private final TiledCanvas canvas;
    private final int width;
    private final int height;
    private final Set<Long> dirtyTiles;
    private final AntiAlias aaMode;          // 使用旧的 AntiAlias 枚举

    // ---- 填充参数 ----
    private final float[] fillColor;
    private final FillRule fillRule;

    // ---- 描边参数 ----
    private final float[] strokeColor;
    private final Cap capStyle;              // 使用旧的 Cap 枚举
    private final Join joinStyle;            // 使用旧的 Join 枚举
    private final double strokeWidth;
    private final DoubleUnaryOperator widthFunc;

    // ---- 展平参数 ----
    private final double flatness;

    private RasterizeCurveFacade(Builder builder) {
        this.curves = builder.curves;
        this.canvas = builder.canvas;
        this.width = builder.width;
        this.height = builder.height;
        this.dirtyTiles = builder.dirtyTiles;
        this.aaMode = builder.aaMode;
        this.fillColor = builder.fillColor;
        this.fillRule = builder.fillRule;
        this.strokeColor = builder.strokeColor;
        this.capStyle = builder.capStyle;
        this.joinStyle = builder.joinStyle;
        this.strokeWidth = builder.strokeWidth;
        this.widthFunc = builder.widthFunc;
        this.flatness = builder.flatness;
    }

    public void render() {
        // 1. 构建 CurveRenderer
        CurveRenderer renderer = buildRenderer();

        // 2. 构建 CurveFlattener
        DoubleUnaryOperator widthProvider = widthFunc != null ? widthFunc : t -> strokeWidth;
        CurveFlattener flattener = new AdaptiveFlattener(flatness, widthProvider, 0.5);

        // 3. 构建 CurveRasterizer
        CurveRasterizer rasterizer = new CurveRasterizer(flattener, renderer);

        // 4. 构建 RenderContext（抗锯齿策略通过 aaMode 创建）
        AntiAliasStrategy aaStrategy = createAAStrategy(aaMode);
        RenderContext context = RenderContext.builder()
                .destCanvas(canvas)
                .width(width)
                .height(height)
                .dirtyTiles(dirtyTiles)
                .antiAlias(aaStrategy)
                .build();

        for (Curve curve : curves) {
            rasterizer.rasterize(curve, context);
        }
    }

    private CurveRenderer buildRenderer() {
        List<CurveRenderer> renderers = new ArrayList<>();

        // 填充
        if (fillColor != null && fillRule != null) {
            PolygonFiller filler = (fillRule == FillRule.EVEN_ODD)
                    ? new EvenOddPolygonFiller(fillColor)
                    : new NonZeroPolygonFiller(fillColor);
            renderers.add(new CurveFill(filler));
        }

        // 描边
        if (strokeColor != null && capStyle != null && joinStyle != null) {
            CapStrategy capStrategy = createCapStrategy(capStyle);
            JoinStrategy joinStrategy = createJoinStrategy(joinStyle);
            PolygonFiller filler = new NonZeroPolygonFiller(strokeColor);
            renderers.add(new CurveStroke(capStrategy, joinStrategy, filler, 4.0));
        }

        if (renderers.isEmpty()) {
            throw new IllegalStateException("At least one renderer (fill or stroke) must be configured");
        }

        CurveRenderer combined = renderers.get(0);
        for (int i = 1; i < renderers.size(); i++) {
            combined = combined.and(renderers.get(i));
        }
        return combined;
    }

    // ---- 策略工厂方法（根据旧枚举创建策略实例） ----
    private static AntiAliasStrategy createAAStrategy(AntiAlias mode) {
        switch (mode) {
            case DISABLED:
                return new NoAAStrategy();
            case ANALYTIC:
                return new AnalyticAAStrategy();

            default:
                throw new IllegalArgumentException("Unsupported AA mode: " + mode);
        }
    }

    private static CapStrategy createCapStrategy(Cap cap) {
        switch (cap) {
            case BUTT:
                return Caps.butt();
            case ROUND:
                return Caps.round();
            case SQUARE:
                return Caps.square();
            default:
                throw new IllegalArgumentException("Unsupported Cap: " + cap);
        }
    }

    private static JoinStrategy createJoinStrategy(Join join) {
        switch (join) {
            case MITER:
                return Joins.miter();
            case ROUND:
                return Joins.round();
            case BEVEL:
                return Joins.bevel();
            default:
                throw new IllegalArgumentException("Unsupported Join: " + join);
        }
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Curve> curves = new ArrayList<>();
        private TiledCanvas canvas;
        private int width;
        private int height;
        private Set<Long> dirtyTiles;
        private AntiAlias aaMode;

        private float[] fillColor;
        private FillRule fillRule;

        private float[] strokeColor;
        private Cap capStyle;
        private Join joinStyle;
        private double strokeWidth = Double.NaN;
        private DoubleUnaryOperator widthFunc;

        private double flatness = Double.NaN;

        // ---- 必要参数 ----
        public Builder curves(Collection<Curve> curves) {
            if (curves == null || curves.isEmpty()) {
                throw new IllegalArgumentException("curves must not be null or empty");
            }
            this.curves.addAll(curves);
            return this;
        }
        public Builder curves(Curve curve) {
            if (curve == null) {
                throw new IllegalArgumentException("curve must not be null or empty");
            }
            this.curves.add(curve);
            return this;
        }

        public Builder canvas(TiledCanvas canvas) {
            if (canvas == null) {
                throw new IllegalArgumentException("canvas must not be null");
            }
            this.canvas = canvas;
            return this;
        }

        public Builder size(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width and height must be positive");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder dirtyTiles(Set<Long> dirtyTiles) {
            if (dirtyTiles == null) {
                throw new IllegalArgumentException("dirtyTiles must not be null (use empty set if none)");
            }
            this.dirtyTiles = dirtyTiles;
            return this;
        }

        public Builder aaMode(AntiAlias aaMode) {
            if (aaMode == null) {
                throw new IllegalArgumentException("aaMode must not be null");
            }
            this.aaMode = aaMode;
            return this;
        }

        // ---- 填充配置 ----
        public Builder fill(float[] color, FillRule rule) {
            if (color == null || color.length < 4) {
                throw new IllegalArgumentException("fill color must be float[4]");
            }
            if (rule == null) {
                throw new IllegalArgumentException("fill rule must not be null");
            }
            this.fillColor = color.clone();
            this.fillRule = rule;
            return this;
        }

        // ---- 描边配置 ----
        public Builder strokeFixed(float[] color, Cap cap, Join join, double width) {
            if (color == null || color.length < 4) {
                throw new IllegalArgumentException("stroke color must be float[4]");
            }
            if (cap == null || join == null) {
                throw new IllegalArgumentException("cap and join must not be null");
            }
            if (width <= 0) {
                throw new IllegalArgumentException("stroke width must be positive");
            }
            this.strokeColor = color.clone();
            this.capStyle = cap;
            this.joinStyle = join;
            this.strokeWidth = width;
            this.widthFunc = null;
            return this;
        }

        public Builder stroke(float[] color, Cap cap, Join join, DoubleUnaryOperator widthFunc) {
            if (color == null || color.length < 4) {
                throw new IllegalArgumentException("stroke color must be float[4]");
            }
            if (cap == null || join == null) {
                throw new IllegalArgumentException("cap and join must not be null");
            }
            if (widthFunc == null) {
                throw new IllegalArgumentException("widthFunc must not be null");
            }
            this.strokeColor = color.clone();
            this.capStyle = cap;
            this.joinStyle = join;
            this.widthFunc = widthFunc;
            this.strokeWidth = Double.NaN;
            return this;
        }

        // ---- 展平配置 ----
        public Builder flatness(double flatness) {
            if (flatness <= 0) {
                throw new IllegalArgumentException("flatness must be positive");
            }
            this.flatness = flatness;
            return this;
        }

        public RasterizeCurveFacade build() {
            // 检查必填
            if (curves == null || curves.isEmpty()) {
                throw new IllegalStateException("curves must be set");
            }
            if (canvas == null) {
                throw new IllegalStateException("canvas must be set");
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
            if (Double.isNaN(flatness)) {
                throw new IllegalStateException("flatness must be set");
            }

            boolean hasFill = fillColor != null && fillRule != null;
            boolean hasStroke = strokeColor != null && capStyle != null && joinStyle != null
                    && (!Double.isNaN(strokeWidth) || widthFunc != null);
            if (!hasFill && !hasStroke) {
                throw new IllegalStateException("At least one of fill or stroke must be configured");
            }

            if (widthFunc != null && !Double.isNaN(strokeWidth)) {
                strokeWidth = Double.NaN;
            }
            if (Double.isNaN(strokeWidth) && widthFunc == null && hasStroke) {
                throw new IllegalStateException("Stroke must have either fixed width or widthFunc");
            }

            return new RasterizeCurveFacade(this);
        }
    }
}