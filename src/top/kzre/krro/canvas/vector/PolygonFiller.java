package top.kzre.krro.canvas.vector;

@FunctionalInterface
public interface PolygonFiller {
    void fill(Polygon polygon, RenderContext context);
}