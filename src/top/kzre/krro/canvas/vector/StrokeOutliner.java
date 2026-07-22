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
        CapGenerator.addCap(cap, coords[0], coords[1], tx, ty, halfW[0],
                rightX[0], rightY[0], leftX[0], leftY[0], poly, true);

        // 左侧边 + Join
        poly.add(leftX[0], leftY[0]);
        for (int i = 1; i < n; i++) {
            JoinGenerator.addJoin(join,
                    leftX[i-1], leftY[i-1], leftX[i], leftY[i],
                    coords[2*i], coords[2*i+1], halfW[i], miterLimit, poly);
            poly.add(leftX[i], leftY[i]);
        }

        // 终点 Cap（最后一个点，isStart = false）
        int last = n - 1;
        double lx = coords[2*last] - coords[2*last-2], ly = coords[2*last+1] - coords[2*last-1];
        double ll = Math.hypot(lx, ly);
        if (ll > 1e-12) { lx /= ll; ly /= ll; }
        CapGenerator.addCap(cap, coords[2*last], coords[2*last+1], -lx, -ly, halfW[last],
                rightX[last], rightY[last], leftX[last], leftY[last], poly, false);

        // 右侧边反向 + Join
        poly.add(rightX[n-1], rightY[n-1]);
        for (int i = n - 2; i >= 0; i--) {
            JoinGenerator.addJoin(join,
                    rightX[i+1], rightY[i+1],
                    rightX[i], rightY[i],
                    coords[2*i], coords[2*i+1], halfW[i], miterLimit, poly);
            poly.add(rightX[i], rightY[i]);
        }

        // 闭合轮廓
        poly.add(leftX[0], leftY[0]);
        return poly.toArray();
    }
}