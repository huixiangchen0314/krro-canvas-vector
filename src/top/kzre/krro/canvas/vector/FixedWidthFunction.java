package top.kzre.krro.canvas.vector;

/**
 * 固定宽度函数
 */
public final class FixedWidthFunction implements WidthFunction {
    private final double width;

    public FixedWidthFunction(double width) {
        this.width = width;
    }

    @Override
    public double map(double x) {
        return width;
    }

    @Override
    public double drive(double x) {
        return 0.0;
    }

    @Override
    public double drive2(double x) {
        return 0.0;
    }
}