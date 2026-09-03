package top.kzre.krro.canvas.vector;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPolygonFiller implements PolygonFiller {

    /**
     * 构建边缘表（bucket），每个桶对应于扫描线 y 坐标。
     * 边缘表存储了从当前顶点开始到下一个顶点的边信息。
     */
    @SuppressWarnings("unchecked")
    protected static List<Edge>[] buildEdgeBuckets(Polygon polygon, int h) {
        List<Edge>[] buckets = new List[h];
        for (int i = 0; i < h; i++) {
            buckets[i] = new ArrayList<>();
        }
        int n = polygon.getVertexCount();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x1 = polygon.getX(i);
            double y1 = polygon.getY(i);
            double x2 = polygon.getX(j);
            double y2 = polygon.getY(j);
            if (Math.abs(y2 - y1) < 1e-12) continue; // 忽略水平边
            Edge edge = new Edge(x1, y1, x2, y2);
            int y = Math.max(0, edge.ymin);
            if (y < h) {
                buckets[y].add(edge);
            }
        }
        return buckets;
    }

    protected static class Edge {
        final int ymin, ymax; // 边的有效 y 范围（半开区间）
        final double dx;      // 单位 y 变化对应的 x 增量
        double x;             // 当前扫描线处的 x 值
        final int winding;    // +1 或 -1，用于非零规则

        Edge(double x1, double y1, double x2, double y2) {
            if (y1 < y2) {
                this.ymin = (int) Math.ceil(y1);
                this.ymax = (int) Math.ceil(y2);
                this.dx = (x2 - x1) / (y2 - y1);
                this.x = x1 + this.dx * (this.ymin - y1);
                this.winding = 1;
            } else {
                this.ymin = (int) Math.ceil(y2);
                this.ymax = (int) Math.ceil(y1);
                this.dx = (x2 - x1) / (y2 - y1);
                this.x = x2 + this.dx * (this.ymin - y2);
                this.winding = -1;
            }
        }
    }
}