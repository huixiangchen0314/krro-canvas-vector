package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EvenOddPolygonFiller extends AbstractPolygonFiller {


    private final float[] color;

    public EvenOddPolygonFiller(float[] color) {
        this.color = color.clone();

    }

    @Override
    public void fill(Polygon polygon, Long key,  RenderContext context) {
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

        // TODO 池化
        // 裁剪多边形到瓦片区域（局部坐标）

        Polygon clipped = polygon.clipToRect(x0 - CLIP_EDGE_EXPAND , y0 - CLIP_EDGE_EXPAND, tw + CLIP_EDGE_EXPAND, th + CLIP_EDGE_EXPAND);
        if (clipped == null || clipped.getVertexCount() < 3) return;
        double[] localCoords = clipped.getCoords().clone();
        for (int i = 0; i < localCoords.length; i += 2) {
            localCoords[i] -= x0;
            localCoords[i + 1] -= y0;
        }
        clipped = new Polygon(localCoords);
        // 构建边缘表（相对于瓦片局部坐标）
        List<Edge>[] buckets = buildEdgeBuckets(clipped, th);
        List<Edge> active = new ArrayList<>();

        for (int localY = 0; localY < th; localY++) {
            if (localY < buckets.length && buckets[localY] != null) {
                active.addAll(buckets[localY]);
            }
            // 移除已结束的边
            final int y = localY;
            active.removeIf(e -> e.ymax <= y);
            active.sort(Comparator.comparingDouble(e -> e.x));

            // Even‑Odd 规则：两两配对
            for (int i = 0; i + 1 < active.size(); i += 2) {
                double x1 = active.get(i).x;
                double x2 = active.get(i + 1).x;
                if (x1 > x2) { double tmp = x1; x1 = x2; x2 = tmp; }
                // 对区间内的每个像素调用抗锯齿策略
                int startX = (int) Math.floor(x1);
                int endX   = (int) Math.floor(x2);
                for (int localX = Math.max(0, startX); localX <= Math.min(tw - 1, endX); localX++) {
                    // 计算实际浮点坐标（世界坐标）
                    double worldX = x0 + localX;
                    double worldY = y0 + localY;
                    // 若需要更精细的覆盖率，可以传递浮点坐标 (x0 + x, y0 + y) 给 aa
                    // 此处我们传递像素中心的浮点坐标，策略内部决定覆盖率
                    aa.fillPixel(worldX + 0.5, worldY + 0.5, color, canvas);
                }
            }

            // 更新活动边表中的 x 值
            for (Edge e : active) {
                e.x += e.dx;
            }
        }
    }
}