package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

import java.util.List;

public final class EvenOddPolygonFiller extends AbstractPolygonFiller {

    private final float[] color;

    public EvenOddPolygonFiller(float[] color) {
        this.color = color.clone();
    }

    @Override
    public void fill(Polygon polygon, Long key, RenderContext context) {
        TiledCanvas canvas = context.getDestCanvas();
        int tileSize = canvas.getTileSize();
        int canvasW  = context.getViewWidth();
        int canvasH  = context.getViewHeight();
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
        final float[] col = color;
        final AntiAliasStrategy aaLocal = aa;
        final TiledCanvas cv = canvas;

        for (int localY = 0; localY < th; localY++) {
            List<Edge> bucket = buckets[localY];
            if (bucket != null && !bucket.isEmpty()) {
                aet.addAll(bucket);
            }

            int worldY = y0 + localY;
            aet.removeExpired(worldY);
            aet.sortByX();

            int n = aet.size();
            double sampleY = worldY + 0.5;
            // Even-Odd：两两配对
            for (int i = 0; i + 1 < n; i += 2) {
                double x1 = aet.get(i).x;
                double x2 = aet.get(i + 1).x;
                if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }

                int startX = (int) Math.floor(x1);
                int endX   = (int) Math.floor(x2);
                if (startX < worldLeft)    startX = worldLeft;
                if (endX > worldRight - 1) endX   = worldRight - 1;
                if (startX > endX) continue;

                for (int px = startX; px <= endX; px++) {
                    aaLocal.fillPixel(px + 0.5, sampleY, col, cv);
                }
            }

            aet.advanceX();
        }
    }
}