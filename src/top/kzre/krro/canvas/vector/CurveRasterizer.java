package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;
import top.kzre.krro.util.tile.TiledCanvas;
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
        strokeVariable(dst, w, h, curve, t -> width, color, cap, join, dirtyTiles, tileSize);
    }

    // ---------- 可变宽度描边 ----------
    public void strokeVariable(float[] dst, int w, int h, Curve curve,
                               DoubleUnaryOperator widthFunc,
                               float[] color, Cap cap, Join join,
                               Set<Long> dirtyTiles, int tileSize) {
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
}