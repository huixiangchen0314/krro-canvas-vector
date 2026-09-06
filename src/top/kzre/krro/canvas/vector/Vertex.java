package top.kzre.krro.canvas.vector;

public final class Vertex {
    /**
     * x 坐标
     */
    private final double x;
    /**
     * y 坐标
     */
    private final double y;
    /**
     * 曲线参数
     */
    private final double t;
    /**
     * 宽度采样
     */
    private final double width;

    public Vertex(double x, double y, double t, double width) {
        this.x = x;
        this.y = y;
        this.t = t;
        this.width = width;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getT() {
        return t;
    }

    public double getWidth() {
        return width;
    }

    @Override
    public String toString() {
        return "Vertex{x=" + x + ", y=" + y + ", t=" + t + ", width=" + width + "}";
    }
}
