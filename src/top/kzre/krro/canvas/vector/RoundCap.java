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
        double prevX = ctx.getPrevX();
        double prevY = ctx.getPrevY();
        double currX = ctx.getCurrX();
        double currY = ctx.getCurrY();
        Vertex v = ctx.getVertex();
        double cx = v.getX(), cy = v.getY();
        double r = v.getWidth() * 0.5;
        RenderContext rc = ctx.getRenderContext();
        double scaleX = rc.getScaleX();
        double scaleY = rc.getScaleY();

        double prevAngle = Math.atan2(prevY - cy, prevX - cx);

        double dirAngle = Math.atan2(ctx.getDirY(), ctx.getDirX());

        // 计算从 prev 到 dir 的短路径差
        double diff1 = dirAngle - prevAngle;
        if (diff1 > Math.PI) diff1 -= 2 * Math.PI;
        else if (diff1 < -Math.PI) diff1 += 2 * Math.PI;

        int halfSteps = steps / 2;

        // 生成中间顶点（不包含起点和终点）
        for (int i = 1; i < halfSteps - 1; i++) {
            double t = (double) i / halfSteps;
            double a = prevAngle + diff1 * t;
            builder.add(cx + Math.cos(a) * r * scaleX,
                    cy + Math.sin(a) * r * scaleY);
        }

        builder.add(cx + Math.cos(dirAngle) * r * scaleX,
                cy + Math.sin(dirAngle) * r * scaleY);

        double currAngle = Math.atan2(currY - cy, currX - cx);

        // 计算从 prev 到 dir 的短路径差
        double diff2 = currAngle - dirAngle;
        if (diff2 > Math.PI) diff2 -= 2 * Math.PI;
        else if (diff2 < -Math.PI) diff2 += 2 * Math.PI;


        // 生成中间顶点（不包含起点和终点）
        for (int i = 1; i < halfSteps - 1; i++) {
            double t = (double) i / halfSteps;
            double a = dirAngle + diff2 * t;
            builder.add(cx + Math.cos(a) * r * scaleX,
                    cy + Math.sin(a) * r * scaleY);
        }
    }
}