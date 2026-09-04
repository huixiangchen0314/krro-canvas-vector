package top.kzre.krro.canvas.vector;

public final class RoundCap implements CapStrategy {
    private final int steps;

    public RoundCap() { this(16); }
    public RoundCap(int steps) {
        if (steps <= 0) throw new IllegalArgumentException("steps must be positive");
        this.steps = steps;
    }

    @Override
    public void addCap(CapContext ctx, DoubleList builder) {
        double cx = ctx.getCenterX(), cy = ctx.getCenterY();
        double r = ctx.getHalfWidth();
        RenderContext renderContext = ctx.getRenderContext();
        double scaleX = renderContext.getScaleX();
        double scaleY = renderContext.getScaleY();

        double startX, startY, endX, endY;
        if (ctx.isStart()) {
            // 起点：从右边缘到左边缘
            startX = ctx.getRightX(); startY = ctx.getRightY();
            endX = ctx.getLeftX(); endY = ctx.getLeftY();
        } else {
            // 终点：从左边缘到右边缘
            startX = ctx.getLeftX(); startY = ctx.getLeftY();
            endX = ctx.getRightX(); endY = ctx.getRightY();
        }

        // 计算起始角度和总角度差
        double startAngle = Math.atan2(startY - cy, startX - cx);
        double endAngle = Math.atan2(endY - cy, endX - cx);
        double delta = endAngle - startAngle;
        if (delta > 0) delta -= 2 * Math.PI; // 顺时针


        // 添加中间点（不包括起点和终点）
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double a = startAngle + delta * t;
            builder.add(cx + Math.cos(a) * r * scaleX, cy + Math.sin(a) * r * scaleY);
        }

    }
}