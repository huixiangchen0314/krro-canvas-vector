package top.kzre.krro.canvas.vector;

/**
 * 圆形连接（Round Join）策略。
 * <p>
 * 在两条边缘之间生成一段圆弧，使连接处呈圆角过渡。
 * 圆弧的半径等于线宽的一半，从第一条边缘的法线方向平滑过渡到第二条边缘的法线方向。
 * </p>
 * <p>
 * 该策略会向构建器中添加一组圆弧顶点（包括起点和终点），
 * 按顺时针方向从第一条边缘到第二条边缘，形成平滑的圆角。
 * </p>
 * <p>
 * 默认使用 16 步绘制圆弧，可通过构造函数指定更高的步数以获得更平滑的效果。
 * </p>
 *
 * @see JoinStrategy
 * @see JoinContext
 */
public final class RoundJoin implements JoinStrategy {

    private final int steps;

    /**
     * 使用默认步数（16）创建圆形连接策略。
     */
    public RoundJoin() {
        this(16);
    }

    /**
     * 使用指定的步数创建圆形连接策略。
     *
     * @param steps 圆弧插值步数，步数越多圆弧越平滑（但顶点数也越多）。
     *              必须大于 0。
     * @throws IllegalArgumentException 如果 steps <= 0
     */
    public RoundJoin(int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }
        this.steps = steps;
    }

    @Override
    public void addJoin(JoinContext context, DoubleList builder) {
        double prevX = context.getPrevX();
        double prevY = context.getPrevY();
        double currX = context.getCurrX();
        double currY = context.getCurrY();
        double vertexX = context.getVertexX();
        double vertexY = context.getVertexY();
        double halfWidth = context.getHalfWidth();

        // 计算两条边缘的方向向量（从顶点到边缘点）
        double prevDX = prevX - vertexX;
        double prevDY = prevY - vertexY;
        double currDX = currX - vertexX;
        double currDY = currY - vertexY;

        // 计算叉积，判断是否为外侧转角
        double cross = prevDX * currDY - prevDY * currDX;
        if (cross <= 0) {
            return; // 内侧转角，不需要处理
        }

        // 计算角度
        double angle1 = Math.atan2(prevDY, prevDX);
        double angle2 = Math.atan2(currDY, currDX);
        double angleDiff = angle2 - angle1;
        if (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        else if (angleDiff <= -Math.PI) angleDiff += 2 * Math.PI;

        // 如果角度差太小，不生成圆弧
        if (Math.abs(angleDiff) < 1e-6) {
            return;
        }

        // 生成圆弧顶点（从 angle1 到 angle2，顺时针）
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = angle1 + angleDiff * t;
            builder.add(vertexX + Math.cos(angle) * halfWidth,
                    vertexY + Math.sin(angle) * halfWidth);
        }
    }

    public int getSteps() {
        return steps;
    }
}