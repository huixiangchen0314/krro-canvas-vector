package top.kzre.krro.canvas.vector;

import java.util.function.DoubleUnaryOperator;

public class StrokeOutliner {
    private final double miterLimit;
    private final int roundSteps;

    public StrokeOutliner(double miterLimit, int roundSteps) {
        this.miterLimit = miterLimit;
        this.roundSteps = roundSteps;
    }

    public double[] outline(double[] coords,
                            DoubleUnaryOperator widthFunc,
                            Cap cap, Join join) {
        int n = coords.length / 2;
        if (n < 2) return new double[0];

        double[] halfW = new double[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            halfW[i] = widthFunc.applyAsDouble(t) / 2.0;
        }

        // 法线（指向左侧）
        double[] nx = new double[n], ny = new double[n];
        for (int i = 0; i < n; i++) {
            double dx, dy;
            if (i == 0) {
                dx = coords[2] - coords[0];
                dy = coords[3] - coords[1];
            } else if (i == n - 1) {
                dx = coords[2*i] - coords[2*i-2];
                dy = coords[2*i+1] - coords[2*i-1];
            } else {
                dx = coords[2*i+2] - coords[2*i-2];
                dy = coords[2*i+3] - coords[2*i-1];
            }
            double len = Math.hypot(dx, dy);
            if (len < 1e-12) {
                nx[i] = 0; ny[i] = 1;
            } else {
                nx[i] = -dy / len; ny[i] = dx / len;
            }
        }

        double[] leftX = new double[n], leftY = new double[n];
        double[] rightX = new double[n], rightY = new double[n];
        for (int i = 0; i < n; i++) {
            leftX[i]  = coords[2*i]   + nx[i] * halfW[i];
            leftY[i]  = coords[2*i+1] + ny[i] * halfW[i];
            rightX[i] = coords[2*i]   - nx[i] * halfW[i];
            rightY[i] = coords[2*i+1] - ny[i] * halfW[i];
        }

        DoubleList poly = new DoubleList(n * 8 + 16);

        // --- 起点 cap ---
        double startDirX = coords[2] - coords[0];
        double startDirY = coords[3] - coords[1];
        double startLen = Math.hypot(startDirX, startDirY);
        if (startLen > 1e-12) { startDirX /= startLen; startDirY /= startLen; }
        double startNormX = -startDirY, startNormY = startDirX;

        if (cap == Cap.ROUND) {
            double startAngle = Math.atan2(-startNormX, -startNormY);
            double endAngle   = Math.atan2(startNormX, startNormY);
            addArc(poly, coords[0], coords[1], halfW[0], startAngle, endAngle, false);
        } else if (cap == Cap.SQUARE) {
            double ex = startDirX * halfW[0], ey = startDirY * halfW[0];
            poly.add(rightX[0] + ex, rightY[0] + ey);
            poly.add(leftX[0]  + ex, leftY[0]  + ey);
        } else { // BUTT
            poly.add(leftX[0], leftY[0]);
        }

        // --- 左边缘（起点→终点）---
        for (int i = 1; i < n; i++) {
            addJoin(poly, leftX[i-1], leftY[i-1], leftX[i], leftY[i],
                    coords[2*i], coords[2*i+1], halfW[i], join);
            poly.add(leftX[i], leftY[i]);
        }

        // --- 终点 cap ---
        int last = n - 1;
        double endDirX = coords[2*last] - coords[2*last-2];
        double endDirY = coords[2*last+1] - coords[2*last-1];
        double endLen = Math.hypot(endDirX, endDirY);
        if (endLen > 1e-12) { endDirX /= endLen; endDirY /= endLen; }
        double endNormX = -endDirY, endNormY = endDirX;

        if (cap == Cap.ROUND) {
            double startAngle = Math.atan2(endNormX, endNormY);
            double endAngle   = Math.atan2(-endNormX, -endNormY);
            addArc(poly, coords[2*last], coords[2*last+1], halfW[last], startAngle, endAngle, false);
        } else if (cap == Cap.SQUARE) {
            double ex = endDirX * halfW[last], ey = endDirY * halfW[last];
            poly.add(leftX[last]  + ex, leftY[last]  + ey);
            poly.add(rightX[last] + ex, rightY[last] + ey);
        } else { // BUTT
            poly.add(rightX[last], rightY[last]);
        }

        // --- 右边缘（终点→起点）---
        for (int i = last - 1; i >= 0; i--) {
            addJoin(poly, rightX[i+1], rightY[i+1], rightX[i], rightY[i],
                    coords[2*i], coords[2*i+1], halfW[i], join);
            poly.add(rightX[i], rightY[i]);
        }

        // 闭合（BUTT时回到left[0]）
        if (cap == Cap.BUTT) {
            poly.add(leftX[0], leftY[0]);
        }

        return poly.toArray();
    }

    // ---------- 内部辅助方法 ----------
    private void addJoin(DoubleList poly,
                         double prevX, double prevY,
                         double currX, double currY,
                         double vertexX, double vertexY,
                         double halfWidth, Join join) {
        double prevDX = prevX - vertexX;
        double prevDY = prevY - vertexY;
        double currDX = currX - vertexX;
        double currDY = currY - vertexY;
        double cross = prevDX * currDY - prevDY * currDX;
        if (cross <= 0) return; // 内侧转角

        double angle1 = Math.atan2(prevDY, prevDX);
        double angle2 = Math.atan2(currDY, currDX);
        double diff = angle2 - angle1;
        if (diff > Math.PI) diff -= 2 * Math.PI;
        else if (diff <= -Math.PI) diff += 2 * Math.PI;

        if (join == Join.BEVEL || (join == Join.MITER && isTooSharp(diff))) {
            return; // 直接连接 prev 和 curr
        }

        if (join == Join.MITER) {
            double perp1x = -Math.sin(angle1), perp1y = Math.cos(angle1);
            double perp2x = -Math.sin(angle2), perp2y = Math.cos(angle2);
            double[] inter = lineIntersection(
                    prevX, prevY, prevX + perp1x, prevY + perp1y,
                    currX, currY, currX + perp2x, currY + perp2y);
            if (inter != null) {
                double dist = Math.hypot(inter[0] - vertexX, inter[1] - vertexY);
                if (dist <= halfWidth * miterLimit) {
                    poly.add(inter[0], inter[1]);
                }
            }
        } else if (join == Join.ROUND) {
            addArc(poly, vertexX, vertexY, halfWidth, angle1, angle2, false);
        }
    }

    private boolean isTooSharp(double angleDiff) {
        double absDiff = Math.abs(angleDiff);
        if (absDiff < 0.001) return false;
        double miterLen = 1.0 / Math.sin(absDiff / 2.0);
        return miterLen > miterLimit;
    }

    private double[] lineIntersection(double x1, double y1, double x2, double y2,
                                      double x3, double y3, double x4, double y4) {
        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(d) < 1e-12) return null;
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
        return new double[]{x1 + t * (x2 - x1), y1 + t * (y2 - y1)};
    }

    private void addArc(DoubleList poly, double cx, double cy,
                        double r, double startAngle, double endAngle, boolean clockwise) {
        double delta = endAngle - startAngle;
        if (clockwise) { if (delta > 0) delta -= 2 * Math.PI; }
        else { if (delta < 0) delta += 2 * Math.PI; }
        for (int i = 0; i <= roundSteps; i++) {
            double a = startAngle + delta * i / roundSteps;
            poly.add(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
    }
}