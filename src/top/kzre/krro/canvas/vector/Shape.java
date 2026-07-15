package top.kzre.krro.canvas.vector;

import java.util.function.DoubleUnaryOperator;

/**
 * 纯几何工具类：根据中心折线和描边参数生成精确的描边轮廓多边形。
 * 该多边形可直接用于 Renderer 的填充（如扫描线填充），实现高质量的描边效果。
 */
public class Shape {

    private static final int ROUND_STEPS = 16; // 圆角/圆弧的细分数

    /**
     * 生成描边轮廓多边形。
     *
     * @param coords      扁平化后的折线顶点坐标 [x0, y0, x1, y1, ...]，至少含两个点
     * @param widthFunc   沿路径的宽度函数，参数 t ∈ [0,1]，返回总宽度（非半宽）
     * @param cap         端点样式
     * @param join        连接样式
     * @param miterLimit  斜接长度限制（与半宽的比值）
     * @return 封闭多边形的顶点坐标数组 [x0, y0, x1, y1, ...]，按逆时针顺序排列
     */
    public static double[] createStrokeOutline(double[] coords,
                                               DoubleUnaryOperator widthFunc,
                                               Cap cap, Join join,
                                               double miterLimit) {
        int n = coords.length / 2;
        if (n < 2) {
            return new double[0];
        }

        // 1. 计算每个顶点的半宽
        double[] halfW = new double[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            halfW[i] = widthFunc.applyAsDouble(t) / 2.0;
        }

        // 2. 计算每个顶点的法线方向（中心差分，端点用单边）
        double[] nx = new double[n];
        double[] ny = new double[n];
        for (int i = 0; i < n; i++) {
            double dx, dy;
            if (i == 0) {
                dx = coords[2] - coords[0];
                dy = coords[3] - coords[1];
            } else if (i == n - 1) {
                dx = coords[2 * i] - coords[2 * i - 2];
                dy = coords[2 * i + 1] - coords[2 * i - 1];
            } else {
                dx = coords[2 * i + 2] - coords[2 * i - 2];
                dy = coords[2 * i + 3] - coords[2 * i - 1];
            }
            double len = Math.hypot(dx, dy);
            if (len < 1e-12) {
                nx[i] = 0;
                ny[i] = 1; // 默认法线
            } else {
                nx[i] = -dy / len;
                ny[i] = dx / len;
            }
        }

        // 3. 计算左右偏移点列
        double[] leftX = new double[n];
        double[] leftY = new double[n];
        double[] rightX = new double[n];
        double[] rightY = new double[n];
        for (int i = 0; i < n; i++) {
            leftX[i] = coords[2 * i] + nx[i] * halfW[i];
            leftY[i] = coords[2 * i + 1] + ny[i] * halfW[i];
            rightX[i] = coords[2 * i] - nx[i] * halfW[i];
            rightY[i] = coords[2 * i + 1] - ny[i] * halfW[i];
        }

        // 4. 构建多边形轮廓
        DoubleArrayBuilder poly = new DoubleArrayBuilder(n * 8);

        // 4a. 起点 Cap（从右边缘过渡到左边缘）
        addStartCap(poly, coords[0], coords[1],
                leftX[0], leftY[0], rightX[0], rightY[0],
                halfW[0], cap);

        // 4b. 左侧边（包括顶点 Join）
        poly.add(leftX[0], leftY[0]);
        for (int i = 1; i < n; i++) {
//            addLeftJoin(poly,
//                    leftX[i - 1], leftY[i - 1],
//                    rightX[i - 1], rightY[i - 1],
//                    leftX[i], leftY[i],
//                    rightX[i], rightY[i],
//                    coords[2 * i], coords[2 * i + 1],
//                    halfW[i - 1], halfW[i], join, miterLimit);
            poly.add(leftX[i], leftY[i]);
        }

        // 4c. 终点 Cap（从左边缘过渡到右边缘）
        addEndCap(poly, coords[2 * n - 2], coords[2 * n - 1],
                leftX[n - 1], leftY[n - 1], rightX[n - 1], rightY[n - 1],
                halfW[n - 1], cap);

        // 4d. 右侧边反向（从终点回到起点，注意顺序已经是逆序，即从右终点到右起点）
        for (int i = n - 1; i >= 0; i--) {
            poly.add(rightX[i], rightY[i]);
        }

        return poly.toArray();
    }

    // ──────────────────────────────────────────────
    // Cap 生成（起点）
    // ──────────────────────────────────────────────
    private static void addStartCap(DoubleArrayBuilder poly,
                                    double cx, double cy,
                                    double leftX, double leftY,
                                    double rightX, double rightY,
                                    double halfWidth, Cap cap) {
        switch (cap) {
            case BUTT:
                // 不添加额外点，后续连接直接使用左右点
                break;
            case SQUARE:
                // 沿切线方向延伸半宽
                double tx = leftX - rightX;
                double ty = leftY - rightY;
                double tlen = Math.hypot(tx, ty);
                if (tlen > 1e-12) {
                    tx /= tlen;
                    ty /= tlen;
                    // 从右边缘和左边缘向外延伸 halfWidth
                    double extRightX = rightX - tx * halfWidth;
                    double extRightY = rightY - ty * halfWidth;
                    double extLeftX = leftX - tx * halfWidth;
                    double extLeftY = leftY - ty * halfWidth;
                    poly.add(extRightX, extRightY);
                    poly.add(extLeftX, extLeftY);
                }
                break;
            case ROUND:
                // 绘制半圆弧，从右边缘开始，逆时针到左边缘
                double startAngle = Math.atan2(rightY - cy, rightX - cx);
                double endAngle   = Math.atan2(leftY - cy, leftX - cx);
                addArc(poly, cx, cy, halfWidth, startAngle, endAngle, false);
                break;
        }
    }

    // 终点 Cap
    private static void addEndCap(DoubleArrayBuilder poly,
                                  double cx, double cy,
                                  double leftX, double leftY,
                                  double rightX, double rightY,
                                  double halfWidth, Cap cap) {
        switch (cap) {
            case BUTT:
                // 不添加
                break;
            case SQUARE:
                double tx = leftX - rightX;
                double ty = leftY - rightY;
                double tlen = Math.hypot(tx, ty);
                if (tlen > 1e-12) {
                    tx /= tlen;
                    ty /= tlen;
                    // 从左右边缘向外延伸
                    double extLeftX  = leftX + tx * halfWidth;
                    double extLeftY  = leftY + ty * halfWidth;
                    double extRightX = rightX + tx * halfWidth;
                    double extRightY = rightY + ty * halfWidth;
                    poly.add(extLeftX, extLeftY);
                    poly.add(extRightX, extRightY);
                }
                break;
            case ROUND:
                // 绘制半圆弧，从左边缘开始，逆时针到右边缘
                double startAngle = Math.atan2(leftY - cy, leftX - cx);
                double endAngle   = Math.atan2(rightY - cy, rightX - cx);
                addArc(poly, cx, cy, halfWidth, startAngle, endAngle, false);
                break;
        }
    }

    // ──────────────────────────────────────────────
    // Join 生成（左侧边顶点）
    // ──────────────────────────────────────────────
    private static void addLeftJoin(DoubleArrayBuilder poly,
                                    double prevLeftX, double prevLeftY,
                                    double prevRightX, double prevRightY,
                                    double currLeftX, double currLeftY,
                                    double currRightX, double currRightY,
                                    double vertexX, double vertexY,
                                    double prevHalfWidth, double currHalfWidth,
                                    Join join, double miterLimit) {
        // 对左边缘在顶点处的连接处理
        addJoinForSide(poly, prevLeftX, prevLeftY, currLeftX, currLeftY,
                vertexX, vertexY, currHalfWidth, join, miterLimit);
        poly.add(currLeftX, currLeftY);
    }

    /**
     * 为某条边（左或右）在顶点处生成连接几何。
     * @param poly     输出多边形
     * @param prevX, prevY  上一段边缘点
     * @param currX, currY  当前段边缘点
     * @param vx, vy        中心顶点坐标
     * @param halfWidth     当前段的半宽（用于计算圆角半径等）
     */
    private static void addJoinForSide(DoubleArrayBuilder poly,
                                       double prevX, double prevY,
                                       double currX, double currY,
                                       double vx, double vy,
                                       double halfWidth,
                                       Join join, double miterLimit) {
        // 计算两个边缘方向向量（从顶点指向边缘点）
        double a1 = Math.atan2(prevY - vy, prevX - vx);
        double a2 = Math.atan2(currY - vy, currX - vx);
        double angleDiff = a2 - a1;
        // 归一化到 (-π, π]
        if (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        if (angleDiff <= -Math.PI) angleDiff += 2 * Math.PI;

        if (join == Join.BEVEL || (join == Join.MITER && isTooSharp(angleDiff, miterLimit))) {
            // BEVEL 或超限 MITER：不添加额外点，直接由 currLeft 点完成过渡
            return;
        } else if (join == Join.MITER) {
            // MITER：计算两条边缘线的交点
            double perp1x = -Math.sin(a1);
            double perp1y = Math.cos(a1);
            double perp2x = -Math.sin(a2);
            double perp2y = Math.cos(a2);
            double[] intersect = lineIntersection(
                    prevX, prevY, prevX + perp1x, prevY + perp1y,
                    currX, currY, currX + perp2x, currY + perp2y);
            if (intersect != null) {
                double dist = Math.hypot(intersect[0] - vx, intersect[1] - vy);
                if (dist <= halfWidth * miterLimit) {
                    poly.add(intersect[0], intersect[1]);
                }
            }
        } else if (join == Join.ROUND) {
            // ROUND：添加圆弧
            double startAngle = a1;
            double endAngle = a2;
            if (angleDiff > 0) {
                addArc(poly, vx, vy, halfWidth, startAngle, endAngle, false);
            } else {
                addArc(poly, vx, vy, halfWidth, startAngle, endAngle, true);
            }
        }
    }

    // ──────────────────────────────────────────────
    // 辅助几何函数
    // ──────────────────────────────────────────────
    private static double[] lineIntersection(double x1, double y1, double x2, double y2,
                                             double x3, double y3, double x4, double y4) {
        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(d) < 1e-12) {
            return null;
        }
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
        return new double[]{x1 + t * (x2 - x1), y1 + t * (y2 - y1)};
    }

    private static boolean isTooSharp(double angleDiff, double miterLimit) {
        double absDiff = Math.abs(angleDiff);
        if (absDiff < 0.001) return false;
        double miterLen = 1.0 / Math.sin(absDiff / 2.0);
        return miterLen > miterLimit;
    }

    /**
     * 添加一段圆弧（用折线逼近）。
     * @param poly        输出多边形
     * @param cx, cy      圆心
     * @param r           半径
     * @param startAngle  起始角度（弧度）
     * @param endAngle    终止角度（弧度）
     * @param clockwise   是否顺时针方向
     */
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