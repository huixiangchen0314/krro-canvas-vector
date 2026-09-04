package top.kzre.krro.canvas.vector;

/**
 * 斜接连接（Miter Join）策略。
 * <p>
 * 当两条边缘相交时，计算其延长线的交点作为外侧顶点。
 * 如果交点到中心顶点的距离超过斜接限制（miterLimit * halfWidth），
 * 则退化为斜角连接（bevel），避免过长的尖角。
 * </p>
 * <p>
 * 该策略需要计算两条边缘的法线方向，并求其交点。
 * 如果无法计算交点（平行边缘），则不添加额外顶点。
 * </p>
 *
 * @see JoinStrategy
 * @see JoinContext
 */
public final class MiterJoin extends AbstractJoinStrategy {

    public static final MiterJoin INSTANCE = new MiterJoin();

    private static final double EPSILON = 1e-12;

    private MiterJoin() {
        // 私有构造，确保单例
    }

    @Override
    public void addJoin(JoinContext context, DoubleList builder) {
        double prevX = context.getPrevX();
        double prevY = context.getPrevY();
        double currX = context.getCurrX();
        double currY = context.getCurrY();
        double vertexX = context.getCenterX();
        double vertexY = context.getCenterY();
        double halfWidth = context.getHalfWidth();
        double miterLimit = context.getMiterLimit();

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

        // 计算两个边缘的法线方向（单位向量）
        double lenPrev = Math.hypot(prevDX, prevDY);
        double lenCurr = Math.hypot(currDX, currDY);
        if (lenPrev < EPSILON || lenCurr < EPSILON) {
            return;
        }

        // 法线方向（垂直向量，指向外侧）
        double nx1 = prevDY / lenPrev;  // 垂直于 prevDX, prevDY 的右侧法线
        double ny1 = -prevDX / lenPrev;
        double nx2 = currDY / lenCurr;
        double ny2 = -currDX / lenCurr;

        // 计算两条法线的交点（从 prev 和 curr 边缘点沿法线方向延伸）
        // 实际应计算两条边缘的延长线交点，但这里使用法线方向更容易
        // 更准确的做法：从 prev 和 curr 沿其法线方向延伸，求交点
        // 但为了与 StrokeOutliner 现有逻辑一致，我们可以直接使用交叉法线求交点
        // 参考原始 StrokeOutliner 的 addJoin 实现
        double angle1 = Math.atan2(prevDY, prevDX);
        double angle2 = Math.atan2(currDY, currDX);
        double angleDiff = angle2 - angle1;
        if (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        else if (angleDiff <= -Math.PI) angleDiff += 2 * Math.PI;

        if (isTooSharp(angleDiff, miterLimit)) {
            return; // 超过斜接限制，退化为斜角
        }

        // 计算两条法线的交点
        double[] intersect = lineIntersection(
                prevX, prevY, prevX + nx1, prevY + ny1,
                currX, currY, currX + nx2, currY + ny2);

        if (intersect != null) {
            double dist = Math.hypot(intersect[0] - vertexX, intersect[1] - vertexY);
            if (dist <= halfWidth * miterLimit) {
                builder.add(intersect[0], intersect[1]);
            }
        }
    }

    private static boolean isTooSharp(double angleDiff, double miterLimit) {
        double absDiff = Math.abs(angleDiff);
        if (absDiff < 0.001) return false;
        double miterLen = 1.0 / Math.sin(absDiff / 2.0);
        return miterLen > miterLimit;
    }

    private static double[] lineIntersection(double x1, double y1, double x2, double y2,
                                             double x3, double y3, double x4, double y4) {
        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(d) < EPSILON) return null;
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
        return new double[]{x1 + t * (x2 - x1), y1 + t * (y2 - y1)};
    }
}