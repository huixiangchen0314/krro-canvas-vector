package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;
import java.util.*;

public final class NonZeroPolygonFiller extends AbstractPolygonFiller {
    private final float[] color;

    public NonZeroPolygonFiller(float[] color) {
        this.color = color.clone();
    }

    @Override
    public void fill(Polygon polygon, RenderContext context) {
        TiledCanvas canvas = context.getDestCanvas();
        int tileSize = canvas.getTileSize();
        int canvasW = context.getWidth();
        int canvasH = context.getHeight();
        Set<Long> dirtyTiles = context.getDirtyTiles();
        AntiAliasStrategy aa = context.getAntiAlias();

        for (long key : dirtyTiles) {
            int tx = TiledCanvas.unpackTx(key);
            int ty = TiledCanvas.unpackTy(key);
            int x0 = tx * tileSize;
            int y0 = ty * tileSize;
            int tw = Math.min(tileSize, canvasW - x0);
            int th = Math.min(tileSize, canvasH - y0);
            if (tw <= 0 || th <= 0) continue;

            // 裁剪多边形到瓦片（世界坐标）
            Polygon clipped = polygon.clipToRect(x0 - CLIP_EDGE_EXPAND , y0, tw, th);
            if (clipped == null || clipped.getVertexCount() < 3) continue;


            // 偏移到局部坐标
            double[] localCoords = clipped.getCoords().clone();
            for (int i = 0; i < localCoords.length; i += 2) {
                localCoords[i] -= x0;
                localCoords[i + 1] -= y0;
            }

            clipped = new Polygon(localCoords);

            // 构建边缘表（局部坐标）
            List<Edge>[] buckets = buildEdgeBuckets(clipped, th);
            List<Edge> active = new ArrayList<>();

            for (int localY = 0; localY < th; localY++) {
                if (localY < buckets.length && buckets[localY] != null) {
                    active.addAll(buckets[localY]);
                }
                final int y = localY;
                active.removeIf(e -> e.ymax <= y);
                active.sort(Comparator.comparingDouble(e -> e.x));

                int winding = 0;
                double spanStart = 0;
                boolean inside = false;
                for (Edge edge : active) {
                    winding += edge.winding;
                    if (winding != 0 && !inside) {
                        //winding != 0 且 inside == false，表示进入多边形内部
                        spanStart = edge.x;
                        inside = true;
                    } else if (winding == 0 && inside) {
                        // winding == 0 且 inside == true，表示离开多边形内部
                        double x1 = spanStart;
                        double x2 = edge.x;
                        if (x1 > x2) {
                            double tmp = x1;
                            x1 = x2;
                            x2 = tmp;
                        }
                        fillSpan(x0, y0, localY, x1, x2, tw, canvas, aa);
                        inside = false;
                    }
                }
                if (inside) {
                    double x1 = spanStart;
                    double x2 = tw;
                    if (x1 > x2) { double tmp = x1; x1 = x2; x2 = tmp; }
                    fillSpan(x0, y0, localY, x1, x2, tw, canvas, aa);
                }

                for (Edge e : active) {
                    e.x += e.dx;
                }
            }
        }
    }

    private void fillSpan(int x0, int y0, int localY, double x1, double x2,
                          int tw, TiledCanvas canvas, AntiAliasStrategy aa) {
        int startX = (int) Math.floor(x1);
        int endX   = (int) Math.floor(x2);
        startX = Math.max(0, startX);
        endX   = Math.min(tw - 1, endX);
        if (startX > endX) return;

        double worldY = y0 + localY + 0.5;
        for (int localX = startX; localX <= endX; localX++) {
            double worldX = x0 + localX + 0.5;
            aa.fillPixel(worldX, worldY, color, canvas);
        }
    }
}