package top.kzre.krro.canvas.vector;

public final class CapContext {
    private final Vertex vertex;
    private final double prevX, prevY;
    private final double currX, currY;
    private final double dirX, dirY;

    private final RenderContext renderContext;
    public CapContext(
            Vertex vertex,
            double prevX, double prevY,
            double currX, double currY,
            double dirX, double dirY,
            RenderContext renderContext) {

        this.currX = currX;
        this.currY = currY;
        this.vertex = vertex;
        this.prevX = prevX;
        this.prevY = prevY;
        this.dirX = dirX;
        this.dirY = dirY;
        this.renderContext = renderContext;
    }

    public double getCurrX() { return currX; }
    public double getCurrY() { return currY; }
    public double getPrevX() { return prevX; }
    public double getPrevY() { return prevY; }

    public RenderContext getRenderContext() {
        return renderContext;
    }

    public Vertex getVertex() {
        return vertex;
    }

    public double getDirX() {
        return dirX;
    }

    public double getDirY() {
        return dirY;
    }
}