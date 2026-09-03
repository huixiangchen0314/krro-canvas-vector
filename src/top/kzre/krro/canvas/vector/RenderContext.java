package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

import java.util.Set;

/**
 * 渲染上下文，封装一次渲染操作所需的所有输入数据。
 * 使用 Builder 模式构建，所有字段均为不可变。
 */
public final class RenderContext {
    private final TiledCanvas destCanvas;  // 目标画布
    private final int width;               // 画布宽度（像素）
    private final int height;              // 画布高度（像素）
    private final Set<Long> dirtyTiles;    // 需要更新的瓦片编码集合（可为 null 表示全部）
    private final AntiAliasStrategy antiAlias;


    private RenderContext(Builder builder) {
        this.destCanvas = builder.destCanvas;
        this.width = builder.width;
        this.height = builder.height;
        this.dirtyTiles = builder.dirtyTiles;


        this.antiAlias = builder.antiAlias;
    }

    public TiledCanvas getDestCanvas() {
        return destCanvas;
    }
    public AntiAliasStrategy getAntiAlias() {
        return antiAlias;
    }
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Set<Long> getDirtyTiles() {
        return dirtyTiles;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public AntiAliasStrategy antiAlias;
        private TiledCanvas destCanvas;
        private int width;
        private int height;
        private Set<Long> dirtyTiles;



        public Builder destCanvas(TiledCanvas canvas) {
            this.destCanvas = canvas;
            return this;
        }

        public Builder width(int w) {
            this.width = w;
            return this;
        }

        public Builder height(int h) {
            this.height = h;
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
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width and height must be positive");
            }

            if (dirtyTiles == null) {
                throw new IllegalArgumentException("dirtyTiles must not be null");
            }

            return new RenderContext(this);
        }
    }
}