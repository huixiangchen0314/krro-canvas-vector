package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

/**
 * 抗锯齿像素混合策略。
 * <p>
 * 负责将指定颜色的像素以抗锯齿方式混合到目标画布上。
 * 实现类应自行计算给定浮点坐标处的覆盖率（例如利用坐标的小数部分），
 * 并进行颜色混合。
 * </p>
 */
@FunctionalInterface
public interface AntiAliasStrategy {

    /**
     * 在给定浮点坐标处混合一个像素。
     *
     * @param x      像素的浮点 x 坐标（子像素精度）
     * @param y      像素的浮点 y 坐标（子像素精度）
     * @param color  填充颜色（RGBA 浮点数组，长度至少为 4）
     * @param canvas 目标画布
     */
    void fillPixel(double x, double y, float[] color, TiledCanvas canvas);
}