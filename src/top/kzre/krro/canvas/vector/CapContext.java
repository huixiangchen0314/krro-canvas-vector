package top.kzre.krro.canvas.vector;

/**
 * 封装描边端点（Cap）生成所需的所有几何参数。
 * <p>
 * 此类是不可变的数据容器，用于在 {@link CapStrategy} 和 {@link StrokeOutliner} 之间传递信息。
 * </p>
 */
public final class CapContext {
    private final double centerX, centerY;           // 端点中心点坐标
    private final double tangentX, tangentY; // 单位切线方向（指向路径方向）
    private final double halfWidth;        // 半宽（宽度的一半）
    private final double rightX, rightY;   // 右侧边缘点（沿切线方向的法线右侧）
    private final double leftX, leftY;     // 左侧边缘点（沿切线方向的法线左侧）
    private final boolean isStart;         // true 表示起点，false 表示终点

    public CapContext(double centerX, double centerY,
                      double tangentX, double tangentY,
                      double halfWidth,
                      double rightX, double rightY,
                      double leftX, double leftY,
                      boolean isStart) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.tangentX = tangentX;
        this.tangentY = tangentY;
        this.halfWidth = halfWidth;
        this.rightX = rightX;
        this.rightY = rightY;
        this.leftX = leftX;
        this.leftY = leftY;
        this.isStart = isStart;
    }

    // --- getters ---
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getTangentX() { return tangentX; }
    public double getTangentY() { return tangentY; }
    public double getHalfWidth() { return halfWidth; }
    public double getRightX() { return rightX; }
    public double getRightY() { return rightY; }
    public double getLeftX() { return leftX; }
    public double getLeftY() { return leftY; }
    public boolean isStart() { return isStart; }
}