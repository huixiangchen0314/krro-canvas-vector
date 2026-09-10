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
        int canvasW  = context.getWidth();
        int canvasH  = context.getHeight();
        AntiAliasStrategy aa = context.getAntiAlias();

        int tx = TiledCanvas.unpackTx(key);
        int ty = TiledCanvas.unpackTy(key);
        int x0 = tx * tileSize;
        int y0 = ty * tileSize;
        int tw = Math.min(tileSize, canvasW - x0);
        int th = Math.min(tileSize, canvasH - y0);
        if (tw <= 0 || th <= 0) return;

        // ── 裁剪到瓦片区域（世界坐标 + 局部坐标） ──
        Polygon clipped = polygon.clipToRect(
                x0 - CLIP_EDGE_EXPAND,
                y0 - CLIP_EDGE_EXPAND,
                tw + CLIP_EDGE_EXPAND,
                th + CLIP_EDGE_EXPAND);
        if (clipped == null || clipped.getVertexCount() < 3) return;

        double[] localCoords = clipped.getCoords().clone();
        for (int i = 0; i < localCoords.length; i += 2) {
            localCoords[i]     -= x0;
            localCoords[i + 1] -= y0;
        }
        clipped = new Polygon(localCoords);

        // ── 构建边缘表 ──────────────────────────────
        List<Edge>[] buckets = buildEdgeBuckets(clipped, th);
        ActiveEdgeTable aet = new ActiveEdgeTable();

        // 局部变量提升，减少循环内字段访问
        final int localTw = tw;
        final float[] col = color;
        final TiledCanvas cv = canvas;
        final AntiAliasStrategy aaLocal = aa;
        final int localX0 = x0;
        final int localY0 = y0;

        // ── 扫描线主循环 ────────────────────────────
        for (int localY = 0; localY < th; localY++) {
            if (buckets[localY] != null) {
                aet.addAll(buckets[localY]);
            }
            aet.removeExpired(localY);
            aet.sortByX();

            // Even-Odd：两两配对
            int n = aet.size();
            double worldY = localY0 + localY + 0.5;
            for (int i = 0; i + 1 < n; i += 2) {
                double x1 = aet.get(i).x;
                double x2 = aet.get(i + 1).x;
                if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }

                int startX = (int) Math.floor(x1);
                int endX   = (int) Math.floor(x2);
                if (startX < 0) startX = 0;
                if (endX >= localTw) endX = localTw - 1;
                if (startX > endX) continue;

                double worldXBase = localX0 + 0.5;
                for (int localX = startX; localX <= endX; localX++) {
                    aaLocal.fillPixel(worldXBase + localX, worldY, col, cv);
                }
            }

            aet.advanceX();
        }
    }
}