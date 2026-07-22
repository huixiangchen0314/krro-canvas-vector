package top.kzre.krro.canvas.vector;

public final class CapGenerator {

    private static final int ROUND_STEPS = 16;

    public static void addCap(Cap cap,
                              double cx, double cy,
                              double tangentX, double tangentY,
                              double halfWidth,
                              double rightX, double rightY,
                              double leftX, double leftY,
                              DoubleArrayBuilder builder,
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
                double sa, ea;
                if (isStart) {
                    // 起点：从右边缘顺时针到左边缘
                    sa = Math.atan2(rightY - cy, rightX - cx);
                    ea = Math.atan2(leftY  - cy, leftX  - cx);
                } else {
                    // 终点：从左边缘顺时针到右边缘
                    sa = Math.atan2(leftY  - cy, leftX  - cx);
                    ea = Math.atan2(rightY - cy, rightX - cx);
                }
                double delta = ea - sa;
                if (delta > 0) delta -= 2 * Math.PI;  // 确保顺时针旋转
                for (int i = 0; i <= ROUND_STEPS; i++) {
                    double a = sa + delta * i / ROUND_STEPS;
                    builder.add(cx + Math.cos(a) * halfWidth, cy + Math.sin(a) * halfWidth);
                }
                break;
        }
    }
}