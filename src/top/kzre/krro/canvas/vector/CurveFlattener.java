package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;

/**
 * 曲线展平策略：将贝塞尔曲线转换为由直线段组成的折线路径。
 * <p>
 * 实现类负责控制展平的精度和性能。
 * </p>
 */
@FunctionalInterface
public interface CurveFlattener {

    /**
     * 展平曲线为折线路径。
     * @param curve 待展平的曲线（开放或闭合）
     * @return 折线路径，包含顶点和闭合标志
     */
    Path flatten(Curve curve, RenderContext context);
}