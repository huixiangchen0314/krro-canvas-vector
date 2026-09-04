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
        if (ctx != null) {
            return;
        }
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

        double angle1 = Math.atan2(prevY - cy, prevX - cx);
        double angle2 = Math.atan2(currY - cy, currX - cx);
        double delta = angle2 - angle1;
        if (delta > Math.PI) delta -= 2 * Math.PI;
        else if (delta < -Math.PI) delta += 2 * Math.PI;

        // 如果角度差太小，不生成圆弧
        if (Math.abs(delta) < 1e-6) {
            return;
        }

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double a = angle1 + delta * t;
            builder.add(cx + Math.cos(a) * r * scaleX,
                    cy + Math.sin(a) * r * scaleY);
        }
    }
}