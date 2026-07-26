package top.kzre.krro.canvas.vector;

import java.util.function.DoubleUnaryOperator;

public class StrokeOutliner {
    private final double miterLimit;
    private final int roundSteps;

    public StrokeOutliner(double miterLimit, int roundSteps) {
        this.miterLimit = miterLimit;
        this.roundSteps = roundSteps;
    }

    /**
     * 生成笔触轮廓，自动处理首尾 Cap。
     */
    public double[] outline(double[] coords,
                            DoubleUnaryOperator widthFunc,
                            Cap cap, Join join) {
        int n = coords.length / 2;
        if (n < 2) return new double[0];

        double[] halfW = new double[n];
        for (int i = 0; i < n; i++) {
            halfW[i] = widthFunc.applyAsDouble((double) i / (n - 1)) / 2.0;
        }

        double[] nx = new double[n], ny = new double[n];
        for (int i = 0; i < n; i++) {
            double dx, dy;
            if (i == 0) {
                dx = coords[2] - coords[0]; dy = coords[3] - coords[1];
            } else if (i == n - 1) {
                dx = coords[2*i] - coords[2*i-2]; dy = coords[2*i+1] - coords[2*i-1];
            } else {
                dx = coords[2*i+2] - coords[2*i-2]; dy = coords[2*i+3] - coords[2*i-1];
            }
            double len = Math.hypot(dx, dy);
            if (len < 1e-12) { nx[i] = 0; ny[i] = 1; }
            else { nx[i] = -dy / len; ny[i] = dx / len; }
        }

        double[] leftX = new double[n], leftY = new double[n];
        double[] rightX = new double[n], rightY = new double[n];
        for (int i = 0; i < n; i++) {
            leftX[i] = coords[2*i] + nx[i] * halfW[i]; leftY[i] = coords[2*i+1] + ny[i] * halfW[i];
            rightX[i] = coords[2*i] - nx[i] * halfW[i]; rightY[i] = coords[2*i+1] - ny[i] * halfW[i];
        }

        DoubleList poly = new DoubleList(n * 8 + 16);

        // 起点 Cap（第一个点，isStart = true）
        double tx = coords[2] - coords[0], ty = coords[3] - coords[1];
        double tlen = Math.hypot(tx, ty);
        if (tlen > 1e-12) { tx /= tlen; ty /= tlen; }
        addCap(cap, coords[0], coords[1], tx, ty, halfW[0],
                rightX[0], rightY[0], leftX[0], leftY[0], poly, true);

        // 左侧边 + Join
        poly.add(leftX[0], leftY[0]);
        for (int i = 1; i < n; i++) {
            addJoin(join,
                    leftX[i-1], leftY[i-1], leftX[i], leftY[i],
                    coords[2*i], coords[2*i+1], halfW[i], miterLimit, poly);
            poly.add(leftX[i], leftY[i]);
        }

        // 终点 Cap（最后一个点，isStart = false）
        int last = n - 1;
        double lx = coords[2*last] - coords[2*last-2], ly = coords[2*last+1] - coords[2*last-1];
        double ll = Math.hypot(lx, ly);
        if (ll > 1e-12) { lx /= ll; ly /= ll; }
        addCap(cap, coords[2*last], coords[2*last+1], -lx, -ly, halfW[last],
                rightX[last], rightY[last], leftX[last], leftY[last], poly, false);

        // 右侧边反向 + Join
        poly.add(rightX[n-1], rightY[n-1]);
        for (int i = n - 2; i >= 0; i--) {
            addJoin(join,
                    rightX[i+1], rightY[i+1],
                    rightX[i], rightY[i],
                    coords[2*i], coords[2*i+1], halfW[i], miterLimit, poly);
            poly.add(rightX[i], rightY[i]);
        }

        // 闭合轮廓
        poly.add(leftX[0], leftY[0]);
        return poly.toArray();
    }


    public  void addCap(Cap cap,
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
                for (int i = 0; i <= roundSteps; i++) {
                    double a = startAngle + delta * i / roundSteps;
                    builder.add(cx + Math.cos(a) * halfWidth, cy + Math.sin(a) * halfWidth);
                }
                break;
        }
    }

    /**
     * 在顶点处生成左边缘的连接顶点（外侧转角）。
     *
     * @param join            连接样式
     * @param prevX, prevY    前一段的左边缘点
     * @param currX, currY    当前段的左边缘点
     * @param vertexX, vertexY 中心顶点坐标
     * @param halfWidth       当前半宽
     * @param miterLimit      斜接限制（与半宽的比值）
     * @param builder         顶点输出
     */
    public void addJoin(Join join,
                               double prevX, double prevY,
                               double currX, double currY,
                               double vertexX, double vertexY,
                               double halfWidth,
                               double miterLimit,
                               DoubleList builder) {
        double prevDX = prevX - vertexX;
        double prevDY = prevY - vertexY;
        double currDX = currX - vertexX;
        double currDY = currY - vertexY;

        double cross = prevDX * currDY - prevDY * currDX;
        if (cross <= 0) return;  // 内侧转角

        double angle1 = Math.atan2(prevDY, prevDX);
        double angle2 = Math.atan2(currDY, currDX);
        double angleDiff = angle2 - angle1;
        if (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        else if (angleDiff <= -Math.PI) angleDiff += 2 * Math.PI;

        if (join == Join.BEVEL ||
                (join == Join.MITER && isTooSharp(angleDiff, miterLimit))) {
            return;
        }

        if (join == Join.MITER) {
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

    private  void addArc(DoubleList poly, double cx, double cy,
                               double r, double startAngle, double endAngle,
                               boolean clockwise) {
        double delta = endAngle - startAngle;
        if (clockwise) {
            if (delta > 0) delta -= 2 * Math.PI;
        } else {
            if (delta < 0) delta += 2 * Math.PI;
        }
        for (int i = 0; i <= roundSteps; i++) {
            double a = startAngle + delta * i / roundSteps;
            poly.add(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
    }
}