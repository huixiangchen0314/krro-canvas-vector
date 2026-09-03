package top.kzre.krro.canvas.vector;


public final class JoinContext {
    private final double prevX, prevY;     // 前一段边缘上的点（例如左边缘上的点）
    private final double currX, currY;     // 当前段边缘上的点
    private final double vertexX, vertexY; // 路径中心顶点坐标
    private final double halfWidth;        // 当前半宽
    private final double miterLimit;       // 斜接限制（相对半宽的倍数）

    public JoinContext(double prevX, double prevY,
                       double currX, double currY,
                       double vertexX, double vertexY,
                       double halfWidth,
                       double miterLimit) {
        this.prevX = prevX;
        this.prevY = prevY;
        this.currX = currX;
        this.currY = currY;
        this.vertexX = vertexX;
        this.vertexY = vertexY;
        this.halfWidth = halfWidth;
        this.miterLimit = miterLimit;
    }

    // --- getters ---
    public double getPrevX() { return prevX; }
    public double getPrevY() { return prevY; }
    public double getCurrX() { return currX; }
    public double getCurrY() { return currY; }
    public double getVertexX() { return vertexX; }
    public double getVertexY() { return vertexY; }
    public double getHalfWidth() { return halfWidth; }
    public double getMiterLimit() { return miterLimit; }
}