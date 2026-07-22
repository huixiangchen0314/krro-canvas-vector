package top.kzre.krro.canvas.vector;

/**
 * 路径端点帽状几何生成器。
 * 参考 Skia 的 SkStroke::addCap 实现，负责在描边起点和终点生成
 * 从右侧边缘到左侧边缘的过渡顶点。
 */
public final class CapGenerator {

    private static final int ROUND_STEPS = 16; // 半圆细分数

    /**
     * 生成起点或终点 Cap 的顶点序列，按逆时针方向追加到 {@code builder}。
     *
     * @param cap       帽样式
     * @param cx, cy    端点中心坐标
     * @param tangentX   端点处切线的 X 分量（指向路径内部）
     * @param tangentY   端点处切线的 Y 分量
     * @param halfWidth  端点处半宽
     * @param rightX, rightY  右侧边缘点
     * @param leftX, leftY    左侧边缘点
     * @param builder   顶点输出
     * @param isStart   是否为起点（true 表示起点，false 表示终点）
     */
    public static void addCap(Cap cap,
                              double cx, double cy,
                              double tangentX, double tangentY,
                              double halfWidth,
                              double rightX, double rightY,
                              double leftX, double leftY,
                              DoubleList builder,
                              boolean isStart) {
        switch (cap) {
            case BUTT:
                break;
            case SQUARE:
                double extRightX = rightX - tangentX * halfWidth;
                double extRightY = rightY - tangentY * halfWidth;
                double extLeftX  = leftX  - tangentX * halfWidth;
                double extLeftY  = leftY  - tangentY * halfWidth;
                builder.add(extRightX, extRightY);
                builder.add(extLeftX, extLeftY);
                break;
            case ROUND:
                double startAngle, endAngle;
                if (isStart) {
                    // 起点：从右边缘顺时针转到左边缘
                    startAngle = Math.atan2(rightY - cy, rightX - cx);
                    endAngle   = Math.atan2(leftY  - cy, leftX  - cx);
                } else {
                    // 终点：从左边缘顺时针转到右边缘
                    startAngle = Math.atan2(leftY  - cy, leftX  - cx);
                    endAngle   = Math.atan2(rightY - cy, rightX - cx);
                }
                // 顺时针旋转半圆
                double delta = endAngle - startAngle;
                if (delta > 0) delta -= 2 * Math.PI;
                for (int i = 0; i <= ROUND_STEPS; i++) {
                    double a = startAngle + delta * i / ROUND_STEPS;
                    builder.add(cx + Math.cos(a) * halfWidth, cy + Math.sin(a) * halfWidth);
                }
                break;
        }
    }
}