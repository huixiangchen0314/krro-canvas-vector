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
public final class RoundJoin extends AbstractJoinStrategy {

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
    private static final double TURN_BACK_THRESHOLD = 1e-3;

    @Override
    public void addJoin(JoinContext context, DoubleList builder) {

        boolean tangentOutSide = context.isTangentOutSide();
        if(!tangentOutSide) {
            return;
        }

        double prevX = context.getPrevX();
        double prevY = context.getPrevY();
        double currX = context.getCurrX();
        double currY = context.getCurrY();
        double cx = context.getCenterX();
        double cy = context.getCenterY();
        double r = context.getHalfWidth();
        double normalX = context.getOutsideNormalX();
        double normalY = context.getOutsideNormalY();
        RenderContext renderContext = context.getRenderContext();
        double scaleX = renderContext.getScaleX();
        double scaleY = renderContext.getScaleY();

        // 计算两条边缘的方向向量
        double prevDX = prevX - cx;
        double prevDY = prevY - cy;
        double currDX = currX - cx;
        double currDY = currY - cy;

        double len1 = Math.hypot(prevDX, prevDY);
        double len2 = Math.hypot(currDX, currDY);
        if (len1 < 1e-12 || len2 < 1e-12) {
            return;
        }

        double cross = prevDX * currDY - prevDY * currDX;

        if (cross < - TURN_BACK_THRESHOLD * scaleX *scaleY) {
            return;
        }

        // 计算两条边缘点相对于中心点的角度
        double angle1 = Math.atan2(prevY - cy, prevX - cx);
        double angle2 = Math.atan2(currY - cy, currX - cx);


        double normalAngle = Math.atan2(normalY, normalX);
        double diff1 = normalAngle - angle1;
        double diff2 = angle2 - normalAngle;

        double halfSteps = (double) steps / 2;
        // 生成圆弧顶点
        if (Math.abs(diff1) > 1e-6){
            for (int i = 1; i <halfSteps; i++) {
                double t = i / halfSteps;
                double angle = angle1 + diff1 * t;
                builder.add(cx + Math.cos(angle) * r * scaleX, cy + Math.sin(angle) * r * scaleY);
            }
        }

        builder.add(cx + Math.cos(normalAngle) * r * scaleX, cy + Math.sin(normalAngle) * r * scaleY);

        if (Math.abs(diff2) > 1e-6) {
            for (int i = 1; i < halfSteps; i++) {
                double t = i / halfSteps;

                double angle = normalAngle + diff2 * t;
                builder.add(cx + Math.cos(angle) * r * scaleX, cy + Math.sin(angle) * r * scaleY);
            }
        }

    }

    public int getSteps() {
        return steps;
    }
}