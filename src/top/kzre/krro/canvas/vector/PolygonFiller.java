package top.kzre.krro.canvas.vector;

import java.util.Set;

@FunctionalInterface
public interface PolygonFiller {
    default void fill(Polygon polygon, RenderContext context){
        Set<Long> dirtyTiles = context.getDirtyTiles();
        for (Long dirtyTile : dirtyTiles) {
            fill (polygon, dirtyTile, context);
        }
    }

    void fill(Polygon polygon, Long tile, RenderContext context);
}
