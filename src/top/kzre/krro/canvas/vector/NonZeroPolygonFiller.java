package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

import java.util.List;

public final class NonZeroPolygonFiller extends AbstractPolygonFiller {

    private final float[] color;

    public NonZeroPolygonFiller(float[] color) {
        this.color = color.clone();
    }

    @Override
    public void fill(Polygon polygon, Long key, RenderContext context) {
        TiledCanvas canvas = context.getDestCanvas();
        int tileSize = canvas.getTileSize();
        int canvasW = context.getWidth();
        int canvasH = context.getHeight();
        AntiAliasStrategy aa = context.getAntiAlias();

        int tx = TiledCanvas.unpackTx(key);
        int ty = TiledCanvas.unpackTy(key);
        int x0 = tx * tileSize;
        int y0 = ty * tileSize;
        int tw = Math.min(tileSize, canvasW - x0);
        int th = Math.min(tileSize, canvasH - y0);
        if (tw <= 0 || th <= 0) return;

        // 裁剪到瓦片附近（世界坐标，不偏移）
        Polygon clipped = polygon.clipToRect(
                x0 - CLIP_EDGE_EXPAND,
                y0 - CLIP_EDGE_EXPAND,
                tw + CLIP_EDGE_EXPAND * 2,
                th + CLIP_EDGE_EXPAND * 2);
        if (clipped == null || clipped.getVertexCount() < 3) return;

        // 直接用世界坐标建边表
        List<Edge>[] buckets = buildEdgeBuckets(clipped, y0, th);
        ActiveEdgeTable aet = new ActiveEdgeTable();

        final int worldLeft  = x0;
        final int worldRight = x0 + tw;

        for (int localY = 0; localY < th; localY++) {
            List<Edge> bucket = buckets[localY];
            if (bucket != null && !bucket.isEmpty()) {
                aet.addAll(bucket);
            }

            int worldY = y0 + localY;
            aet.removeExpired(worldY);
            aet.sortByX();

            int winding = 0;
            double spanStart = 0;
            boolean inside = false;
            for (int i = 0, n = aet.size(); i < n; i++) {
                Edge edge = aet.get(i);
                winding += edge.winding;

                if (winding != 0 && !inside) {
                    spanStart = edge.x;
                    inside = true;
                } else if (winding == 0 && inside) {
                    double x1 = spanStart;
                    double x2 = edge.x;
                    if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }
                    fillSpan(worldLeft, worldRight, worldY, x1, x2, canvas, aa);
                    inside = false;
                }
            }
            if (inside) {
                double x1 = spanStart;
                double x2 = worldRight;
                if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }
                fillSpan(worldLeft, worldRight, worldY, x1, x2, canvas, aa);
            }

            aet.advanceX();
        }
    }

    private void fillSpan(int worldLeft, int worldRight, int worldY,
                          double x1, double x2,
                          TiledCanvas canvas, AntiAliasStrategy aa) {
        int startX = (int) Math.floor(x1);
        int endX   = (int) Math.floor(x2);
        if (startX < worldLeft)   startX = worldLeft;
        if (endX > worldRight - 1) endX = worldRight - 1;
        if (startX > endX) return;

        double sampleY = worldY + 0.5;
        float[] col = color;
        for (int px = startX; px <= endX; px++) {
            aa.fillPixel(px + 0.5, sampleY, col, canvas);
        }
    }
}