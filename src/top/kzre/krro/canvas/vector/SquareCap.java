package top.kzre.krro.canvas.vector;

/**
 * 方形端点（Square Cap）策略。
 * <p>
 * 在路径端点处添加一个矩形端头，向外延伸半个线宽的距离。
 * 端头的边与路径切线方向垂直，形成方头效果。
 * </p>
 * <p>
 * 该策略会向构建器中添加两个顶点：右侧外角和左侧外角。
 * 顶点顺序为：先右外角，后左外角，便于与路径边缘连接。
 * </p>
 *
 * @see CapStrategy
 * @see CapContext
 */
public final class SquareCap implements CapStrategy {

    public static final SquareCap INSTANCE = new SquareCap();

    private SquareCap() {
        // 私有构造，确保单例
    }

    @Override
    public void addCap(CapContext context, DoubleList builder) {
        double half = context.getHalfWidth();
        double tx = context.getTangentX();
        double ty = context.getTangentY();

        // 右侧外角：右侧边缘沿切线方向延伸 half 距离
        double extRightX = context.getRightX() - tx * half;
        double extRightY = context.getRightY() - ty * half;

        // 左侧外角：左侧边缘沿切线方向延伸 half 距离
        double extLeftX = context.getLeftX() - tx * half;
        double extLeftY = context.getLeftY() - ty * half;

        // 先添加右侧外角，再添加左侧外角（顺序与现有轮廓生成逻辑一致）
        builder.add(extRightX, extRightY);
        builder.add(extLeftX, extLeftY);
    }
}