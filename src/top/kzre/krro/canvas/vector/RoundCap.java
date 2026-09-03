package top.kzre.krro.canvas.vector;

/**
 * 圆形端点（Round Cap）策略。
 * <p>
 * 在路径端点处生成一个半圆弧，使端点呈圆形。
 * 圆弧的半径等于线宽的一半，从右侧边缘平滑过渡到左侧边缘。
 * </p>
 * <p>
 * 该策略会向构建器中添加一组圆弧顶点（包括起点和终点），
 * 按顺时针方向从右边缘到左边缘（或反之），形成平滑的圆形端头。
 * </p>
 * <p>
 * 默认使用 16 步绘制圆弧，可通过构造函数指定更高的步数以获得更平滑的效果。
 * </p>
 *
 * @see CapStrategy
 * @see CapContext
 */
public final class RoundCap implements CapStrategy {

    private final int steps;

    /**
     * 使用默认步数（16）创建圆形端点策略。
     */
    public RoundCap() {
        this(16);
    }

    /**
     * 使用指定的步数创建圆形端点策略。
     *
     * @param steps 圆弧插值步数，步数越多圆弧越平滑（但顶点数也越多）。
     *              必须大于 0。
     * @throws IllegalArgumentException 如果 steps <= 0
     */
    public RoundCap(int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }
        this.steps = steps;
    }

    @Override
    public void addCap(CapContext context, DoubleList builder) {
        double cx = context.getCenterX();
        double cy = context.getCenterY();
        double half = context.getHalfWidth();

        double startAngle, endAngle;
        double angle1 = Math.atan2(context.getRightY() - cy, context.getRightX() - cx);
        double angle2 = Math.atan2(context.getLeftY() - cy, context.getLeftX() - cx);
        if (context.isStart()) {
            // 起点：从右边缘（角度）到左边缘（角度）
            startAngle = angle1;
            endAngle   = angle2;
        } else {
            // 终点：从左边缘到右边缘
            startAngle = angle2;
            endAngle   = angle1;
        }

        // 确保顺时针旋转（从 startAngle 到 endAngle 沿负方向）
        double delta = endAngle - startAngle;
        if (delta > 0) {
            delta -= 2 * Math.PI;
        }

        // 生成圆弧顶点（包含起点和终点）
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = startAngle + delta * t;
            builder.add(cx + Math.cos(angle) * half, cy + Math.sin(angle) * half);
        }
    }

    public int getSteps() {
        return steps;
    }
}