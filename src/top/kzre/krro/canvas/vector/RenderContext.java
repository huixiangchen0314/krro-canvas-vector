package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

import java.util.Set;

/**
 * 渲染上下文，封装一次渲染操作所需的所有输入数据。
 * 使用 Builder 模式构建，所有字段均为不可变。
 */
public final class RenderContext {
    private static final double MIN_SCALE = 1e-6;
    private final TiledCanvas destCanvas;  // 目标画布
    private final int viewWidth;               // 画布宽度（像素）
    private final int viewHeight;              // 画布高度（像素）
    private final Set<Long> dirtyTiles;    // 需要更新的瓦片编码集合（可为 null 表示全部）
    private final AntiAliasStrategy antiAlias;
    private final double scaleX;
    private final double scaleY;
    private final double effectiveScale;
    private final double flatness;

    private RenderContext(Builder builder) {
        this.destCanvas = builder.destCanvas;
        this.viewWidth = builder.viewWidth;
        this.viewHeight = builder.viewHeight;
        this.dirtyTiles = builder.dirtyTiles;
        this.scaleX = builder.scaleX;
        this.scaleY = builder.scaleY;
        this.flatness = builder.flatness;

        this.antiAlias = builder.antiAlias;

        // 非均匀缩放取几何平均作为有效缩放，作为阈值换算与半径估计的参考量。
        double s = Math.sqrt(Math.abs(builder.scaleX * builder.scaleY));
        this.effectiveScale = (s < MIN_SCALE) ? MIN_SCALE : s;
    }

    public TiledCanvas getDestCanvas() {
        return destCanvas;
    }
    public AntiAliasStrategy getAntiAlias() {
        return antiAlias;
    }
    public int getViewWidth() {
        return viewWidth;
    }

    public int getViewHeight() {
        return viewHeight;
    }

    public Set<Long> getDirtyTiles() {
        return dirtyTiles;
    }


    public static Builder builder() {
        return new Builder();
    }

    public double getScaleX() {
        return scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public double getFlatness() {
        return flatness;
    }

    public double getEffectiveScale() {
        return effectiveScale;
    }

    public static class Builder {
        public double scaleX = 1.0;
        public double flatness = 0.25;
        private double scaleY = 1.0;
        private AntiAliasStrategy antiAlias;
        private TiledCanvas destCanvas;
        private int viewWidth;
        private int viewHeight;
        private Set<Long> dirtyTiles;



        public Builder destCanvas(TiledCanvas canvas) {
            this.destCanvas = canvas;
            return this;
        }

        public Builder flatness(double flatness) {
            this.flatness = flatness;
            return this;
        }

        public Builder viewWidth(int w) {
            this.viewWidth = w;
            return this;
        }

        public Builder viewHeight(int h) {
            this.viewHeight = h;
            return this;
        }

        public Builder scale(double sx, double sy) {
            this.scaleX = sx;
            this.scaleY = sy;
            return this;
        }

        public Builder antiAlias(AntiAliasStrategy antiAlias) {
            this.antiAlias = antiAlias;
            return this;
        }

        public Builder dirtyTiles(Set<Long> tiles) {
            this.dirtyTiles = tiles;
            return this;
        }

        public RenderContext build() {
            // 必要参数校验
            if (destCanvas == null) {
                throw new IllegalArgumentException("destCanvas must not be null");
            }
            if (viewWidth <= 0 || viewHeight <= 0) {
                throw new IllegalArgumentException("width and height must be positive");
            }

            if (dirtyTiles == null) {
                throw new IllegalArgumentException("dirtyTiles must not be null");
            }

            return new RenderContext(this);
        }
    }
}