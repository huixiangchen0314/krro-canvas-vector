package top.kzre.krro.canvas.vector;

/**
 * 描边端点（Cap）生成策略。
 * <p>
 * 实现类负责根据给定的端点几何上下文，向多边形构建器（{@link DoubleList}）中添加
 * 用于形成端点轮廓的顶点序列。
 * </p>
 *
 * <h3>实现注意事项</h3>
 * <ul>
 *   <li>实现应无状态（或线程安全），以便在多个渲染任务中共享。</li>
 *   <li>生成的顶点应按顺序形成闭合轮廓的一部分，通常从右侧边缘开始，到左侧边缘结束。</li>
 *   <li>对于 BUTT 样式，无需添加任何顶点。</li>
 *   <li>对于 SQUARE 样式，应添加矩形端头的外角顶点。</li>
 *   <li>对于 ROUND 样式，应生成半圆弧顶点序列。</li>
 * </ul>
 *
 * @see CapContext
 * @see DoubleList

 */
@FunctionalInterface
public interface CapStrategy {
    /**
     * 生成端点轮廓顶点并将其添加到构建器中。
     *
     * @param context 端点几何参数，包含中心点、切线、半宽及左右边缘点。
     * @param builder 用于收集顶点的构建器（可变长度）。
     */
    void addCap(CapContext context, DoubleList builder);
}