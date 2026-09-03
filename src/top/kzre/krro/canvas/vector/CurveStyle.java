package top.kzre.krro.canvas.vector;

public final class CurveStyle {
    private final PathRenderer renderer;
    private final PolygonFiller filler;

    public CurveStyle(PathRenderer renderer, PolygonFiller filler) {
        this.renderer = renderer;
        this.filler = filler;
    }

    public PathRenderer getRenderer() {
        return renderer;
    }

    public PolygonFiller getFiller() {
        return filler;
    }
}
