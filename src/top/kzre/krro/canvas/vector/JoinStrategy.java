package top.kzre.krro.canvas.vector;

/**
 * 描边连接点（Join）生成策略。
 * <p>
 * 实现类负责根据给定的连接点几何上下文，向多边形构建器（{@link DoubleList}）中添加
 * 用于形成连接处外侧轮廓的顶点。
 * </p>
 *
 * <h3>实现注意事项</h3>
 * <ul>
 *   <li>实现应无状态（或线程安全），以便在多个渲染任务中共享。</li>
 *   <li>生成的顶点应放置在两条边缘之间的外侧转角处。</li>
 *   <li>对于 BEVEL 样式，无需添加额外顶点（直接连接两条边缘）。</li>
 *   <li>对于 MITER 样式，应计算两条边缘法线的交点，并检查是否超过斜接限制。</li>
 *   <li>对于 ROUND 样式，应生成一段圆弧连接两条边缘。</li>
 * </ul>
 *
 * @see JoinContext
 * @see DoubleList
 * @see StrokeOutliner
 */
@FunctionalInterface
public interface JoinStrategy {
    /**
     * 生成连接处轮廓顶点并将其添加到构建器中。
     *
     * @param context 连接点几何参数，包含前一段边缘点、当前段边缘点、中心顶点、半宽和斜接限制。
     * @param builder 用于收集顶点的构建器（可变长度）。
     */
    void addJoin(JoinContext context, DoubleList builder);
}