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

            // 裁剪多边形到瓦片局部坐标
            Polygon clipped = polygon.clipToRect(x0, y0, tw, th);
            if (clipped == null || clipped.getVertexCount() < 3) continue;

            // 构建边缘表（仅包含与当前瓦片相交的边）
            List<Edge>[] buckets = buildEdgeBuckets(clipped, th);
            List<Edge> active = new ArrayList<>();

            for (int localY = 0; localY < th; localY++) {
                // 添加新边
                if (localY < buckets.length && buckets[localY] != null) {
                    active.addAll(buckets[localY]);
                }
                // 移除已结束的边
                final int y = localY;
                active.removeIf(e -> e.ymax <= y);
                // 按 x 排序
                active.sort(Comparator.comparingDouble(e -> e.x));

                // ---- 非零环绕填充 ----
                int winding = 0;
                double spanStart = 0;
                boolean inside = false;
                for (Edge edge : active) {
                    winding += edge.winding;
                    if (winding != 0 && !inside) {
                        spanStart = edge.x;
                        inside = true;
                    } else if (winding == 0 && inside) {
                        // 结束一个内部区间
                        double x1 = spanStart;
                        double x2 = edge.x;
                        if (x1 > x2) { double tmp = x1; x1 = x2; x2 = tmp; }
                        fillSpan(x0, y0, localY, x1, x2, tw, canvas, aa);
                        inside = false;
                    }
                }
                // 如果扫描线结束仍为内部，填充到瓦片右边缘
                if (inside) {
                    double x1 = spanStart;
                    double x2 = tw;
                    if (x1 > x2) { double tmp = x1; x1 = x2; x2 = tmp; }
                    fillSpan(x0, y0, localY, x1, x2, tw, canvas, aa);
                }

                // 更新活动边中的 x 值
                for (Edge e : active) {
                    e.x += e.dx;
                }
            }
        }
    }

    /**
     * 填充一个水平区间，调用抗锯齿策略处理每个像素。
     *
     * @param x0    瓦片在世界坐标中的 X 偏移
     * @param y0    瓦片在世界坐标中的 Y 偏移
     * @param localY 瓦片内的局部扫描线 Y
     * @param x1    区间左边界（浮点，世界坐标）
     * @param x2    区间右边界（浮点，世界坐标）
     * @param tw    瓦片宽度（用于限制范围）
     */
    private void fillSpan(int x0, int y0, int localY, double x1, double x2, int tw, TiledCanvas canvas, AntiAliasStrategy aa) {
        int startX = (int) Math.floor(x1);
        int endX   = (int) Math.floor(x2);
        // 限制在瓦片范围内
        startX = Math.max(0, startX);
        endX   = Math.min(tw - 1, endX);
        if (startX > endX) return;

        double worldY = y0 + localY + 0.5; // 像素中心 Y
        for (int localX = startX; localX <= endX; localX++) {
            double worldX = x0 + localX + 0.5; // 像素中心 X
            aa.fillPixel(worldX, worldY, color, canvas);
        }
    }
}