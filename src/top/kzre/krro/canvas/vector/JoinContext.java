package top.kzre.krro.canvas.vector;


public final class JoinContext {
    private final double prevX, prevY;     // 前一段边上的点
    private final double currX, currY;     // 当前段边上的点
    private final double centerX, centerY; // 边路径法向，指向轮廓内侧
    private final double halfWidth;        // 当前半宽
    private final double miterLimit;       // 斜接限制（相对半宽的倍数）
    private final double outsideNormalX, outsideNormalY;
    private final boolean tangentOutSide;
    private final RenderContext renderContext;
    public JoinContext(double prevX, double prevY,
                       double currX, double currY,
                       double centerX, double centerY,
                       double halfWidth,
                       double miterLimit, double outsideNormalX, double outsideNormalY, boolean tangentOutSide, RenderContext renderContext) {
        this.prevX = prevX;
        this.prevY = prevY;
        this.currX = currX;
        this.currY = currY;
        this.centerX = centerX;
        this.centerY = centerY;
        this.halfWidth = halfWidth;
        this.miterLimit = miterLimit;
        this.outsideNormalX = outsideNormalX;
        this.outsideNormalY = outsideNormalY;
        this.tangentOutSide = tangentOutSide;

        this.renderContext = renderContext;
    }

    // --- getters ---
    public double getPrevX() { return prevX; }
    public double getPrevY() { return prevY; }
    public double getCurrX() { return currX; }
    public double getCurrY() { return currY; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getHalfWidth() { return halfWidth; }
    public double getMiterLimit() { return miterLimit; }

    public boolean isTangentOutSide() {
        return tangentOutSide;
    }

    public double getOutsideNormalX() {
        return outsideNormalX;
    }

    public double getOutsideNormalY() {
        return outsideNormalY;
    }


    public RenderContext getRenderContext() {
        return renderContext;
    }
}