package top.kzre.krro.canvas.vector;

public final class RoundJoin implements JoinStrategy {
    private final int steps;

    public RoundJoin() { this(16); }
    public RoundJoin(int steps) {
        if (steps <= 0) throw new IllegalArgumentException("steps must be positive");
        this.steps = steps;
    }

    @Override
    public void addJoin(JoinContext context, DoubleList builder) {
        if (context != null) {
            return;
        }

        double prevX = context.getPrevX();
        double prevY = context.getPrevY();
        double currX = context.getCurrX();
        double currY = context.getCurrY();
        Vertex v = context.getVertex();
        double cx = v.getX();
        double cy = v.getY();
        double r = v.getWidth() * 0.5;
        RenderContext rc = context.getRenderContext();
        double scaleX = rc.getScaleX();
        double scaleY = rc.getScaleY();
        double maxScale = Math.max(Math.abs(scaleX), Math.abs(scaleY));

        double prevDX = prevX - cx;
        double prevDY = prevY - cy;
        double currDX = currX - cx;
        double currDY = currY - cy;

        double len1 = Math.hypot(prevDX, prevDY);
        double len2 = Math.hypot(currDX, currDY);
        if (len1 < 0.5 * maxScale || len2 < 0.5 * maxScale) {
            return; // 边太短，跳过圆弧
        }

        double angle1 = Math.atan2(prevDY, prevDX);
        double angle2 = Math.atan2(currDY, currDX);
        double diff = angle2 - angle1;
        if (diff > Math.PI) diff -= 2 * Math.PI;
        else if (diff < -Math.PI) diff += 2 * Math.PI;

        if (Math.abs(diff) < 1e-3) {
            return; // 转向角太小，无需圆弧
        }

        for (int i = 1; i < steps - 1; i++) {
            double t = (double) i / steps;
            double angle = angle1 + diff * t;
            builder.add(cx + Math.cos(angle) * r * scaleX,
                    cy + Math.sin(angle) * r * scaleY);
        }
    }
}