package top.kzre.krro.canvas.vector;

public final class RenderablePolygon {
    private final Polygon polygon;
    private final PolygonFiller filler;
    public RenderablePolygon(Polygon polygon, PolygonFiller filler) {
        this.polygon = polygon;

        this.filler = filler;
    }


    public PolygonFiller getFiller() {
        return filler;
    }

    public Polygon getPolygon() {
        return polygon;
    }
}
