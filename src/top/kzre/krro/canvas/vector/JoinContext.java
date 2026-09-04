package top.kzre.krro.canvas.vector;


public final class JoinContext {
    private final double prevX, prevY;     // 前一段边上的点
    private final double currX, currY;     // 当前段边上的点
    private final Vertex vertex;
    private final double miterLimit;       // 斜接限制（相对半宽的倍数）
    private final RenderContext renderContext;
    public JoinContext(Vertex vertex,
                       double prevX, double prevY,
                       double currX, double currY,
                       double miterLimit, RenderContext renderContext) {
        this.prevX = prevX;
        this.prevY = prevY;
        this.currX = currX;
        this.currY = currY;
        this.vertex = vertex;
        this.miterLimit = miterLimit;

        this.renderContext = renderContext;
    }

    // --- getters ---
    public double getPrevX() { return prevX; }
    public double getPrevY() { return prevY; }
    public double getCurrX() { return currX; }
    public double getCurrY() { return currY; }
    public double getMiterLimit() { return miterLimit; }


    public RenderContext getRenderContext() {
        return renderContext;
    }

    public Vertex getVertex() {
        return vertex;
    }
}