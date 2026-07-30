package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.Canvas;
import java.util.Set;

public interface AntiAliasPolicy {
    /**
     * 将多边形渲染到 Canvas 的指定脏瓦片中。
     * @param dest       目标画布
     * @param w, h      画布像素尺寸
     * @param polygon   闭合多边形顶点（世界坐标）
     * @param color     填充颜色 [R,G,B,A]
     * @param rule      填充规则
     * @param dirtyTiles 需要绘制的瓦片键集合
     * @param tileSize  瓦片尺寸
     */
    void fill(Canvas dest, int w, int h,
              double[] polygon, float[] color, FillRule rule,
              Set<Long> dirtyTiles, int tileSize);
}