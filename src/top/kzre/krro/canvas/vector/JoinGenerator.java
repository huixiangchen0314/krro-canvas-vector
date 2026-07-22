package top.kzre.krro.canvas.vector;

/**
 * 路径拐角连接几何生成器。
 * 参考 Skia 的 SkStroke::addJoin 实现，仅处理外侧转角（内侧保持尖锐连接）。
 */
public final class JoinGenerator {

    private static final int ROUND_STEPS = 16;

    /**
     * 在顶点处生成左边缘的连接顶点（外侧转角）。
     * 当转角为凸（外侧）时，根据 join 类型添加 Miter/Bevel/Round 几何；
     * 转角为凹（内侧）时不做任何处理，直接连接 prev 与 curr 即可。
     *
     * @param join            连接样式
     * @param prevX, prevY    前一段的左边缘点
     * @param currX, currY    当前段的左边缘点
     * @param vertexX, vertexY 中心顶点坐标
     * @param halfWidth       当前半宽
     * @param miterLimit      斜接限制（与半宽的比值）
     * @param builder         顶点输出
     */
    public static void addJoin(Join join,
                               double prevX, double prevY,
                               double currX, double currY,
                               double vertexX, double vertexY,
                               double halfWidth,
                               double miterLimit,
                               DoubleArrayBuilder builder) {
        // 计算两个方向向量（从顶点指向边缘点）
        double prevDX = prevX - vertexX;
        double prevDY = prevY - vertexY;
        double currDX = currX - vertexX;
        double currDY = currY - vertexY;

        // 通过叉积判断旋转方向（z 分量）：正 => 左转（外侧），负 => 右转（内侧）
        double cross = prevDX * currDY - prevDY * currDX;
        if (cross <= 0) {
            // 内侧转角，不需要额外几何（直接连接 prev 和 curr）
            return;
        }

        // 外侧转角，计算两条边缘线在顶点处的角度
        double angle1 = Math.atan2(prevDY, prevDX);
        double angle2 = Math.atan2(currDY, currDX);
        double angleDiff = angle2 - angle1;
        // 保证角度差在 (-π, π]
        if (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        else if (angleDiff <= -Math.PI) angleDiff += 2 * Math.PI;

        if (join == Join.BEVEL ||
                (join == Join.MITER && isTooSharp(angleDiff, miterLimit))) {
            // Bevel 或超限 Miter：不添加额外点，直接连接
            return;
        }

        if (join == Join.MITER) {
            // 计算两条边缘延伸线的交点
            double perp1x = -Math.sin(angle1);
            double perp1y = Math.cos(angle1);
            double perp2x = -Math.sin(angle2);
            double perp2y = Math.cos(angle2);

            double[] intersect = lineIntersection(
                    prevX, prevY, prevX + perp1x, prevY + perp1y,
                    currX, currY, currX + perp2x, currY + perp2y);
            if (intersect != null) {
                double dist = Math.hypot(intersect[0] - vertexX, intersect[1] - vertexY);
                if (dist <= halfWidth * miterLimit) {
                    builder.add(intersect[0], intersect[1]);
                }
            }
        } else if (join == Join.ROUND) {
            // 绘制圆角弧（从 angle1 到 angle2）
            addArc(builder, vertexX, vertexY, halfWidth, angle1, angle2, false);
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
        if (Math.abs(d) < 1e-12) return null;
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
        return new double[]{x1 + t * (x2 - x1), y1 + t * (y2 - y1)};
    }

    private static void addArc(DoubleArrayBuilder poly, double cx, double cy,
                               double r, double startAngle, double endAngle,
                               boolean clockwise) {
        double delta = endAngle - startAngle;
        if (clockwise) {
            if (delta > 0) delta -= 2 * Math.PI;
        } else {
            if (delta < 0) delta += 2 * Math.PI;
        }
        for (int i = 0; i <= ROUND_STEPS; i++) {
            double a = startAngle + delta * i / ROUND_STEPS;
            poly.add(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
    }
}