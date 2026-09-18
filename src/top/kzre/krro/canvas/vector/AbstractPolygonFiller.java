package top.kzre.krro.canvas.vector;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPolygonFiller implements PolygonFiller {

    /**
     * 裁剪边缘拓展，避免裁剪误差导致的错误渲染
     */
    public static final int CLIP_EDGE_EXPAND = 1;

    /**
     * 构建边缘表（bucket），每个桶对应瓦片内的一条扫描线。
     * <p>
     * 边本身使用世界坐标存储，桶下标 = {@code edge.ymin - y0}。
     *
     * @param polygon 已裁剪到瓦片附近的多边形（世界坐标）
     * @param y0      瓦片在世界坐标下的左上角 y
     * @param lines   瓦片高度（局部像素数）
     */
    @SuppressWarnings("unchecked")
    protected static List<Edge>[] buildEdgeBuckets(Polygon polygon, int y0, int lines) {
        List<Edge>[] buckets = new List[lines];
        for (int i = 0; i < lines; i++) {
            buckets[i] = new ArrayList<Edge>();
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

            // 世界坐标 → 瓦片局部行号
            int localY = edge.ymin - y0;
            if (localY < 0) localY = 0;
            if (localY < lines) {
                buckets[localY].add(edge);
            }
        }
        return buckets;
    }

    protected static class Edge {
        final int ymin, ymax; // 世界坐标下边的有效 y 范围（半开区间）
        final double dx;      // 单位 y 变化对应的 x 增量
        double x;             // 当前扫描线处的 x 值（世界坐标）
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

    protected static class ActiveEdgeTable {
        private static final int INITIAL_CAPACITY = 16;
        private static final int INSERTION_SORT_THRESHOLD = 8;

        private Edge[] edges = new Edge[INITIAL_CAPACITY];
        private int size = 0;

        public int size() { return size; }

        public Edge get(int i) { return edges[i]; }

        public void clear() { size = 0; }

        public void add(Edge e) {
            if (size == edges.length) grow(1);
            edges[size++] = e;
        }

        public void addAll(List<Edge> list) {
            int n = list.size();
            if (n == 0) return;
            if (size + n > edges.length) grow(n);
            for (int i = 0; i < n; i++) {
                edges[size++] = list.get(i);
            }
        }

        /** 移除所有已失效的边（ymax <= y），y 为世界坐标扫描线 */
        public void removeExpired(int y) {
            int w = 0;
            for (int i = 0; i < size; i++) {
                Edge e = edges[i];
                if (e.ymax > y) {
                    edges[w++] = e;
                }
            }
            size = w;
        }

        public void sortByX() {
            if (size > 1) {
                quickSort(edges, 0, size - 1);
            }
        }

        public void advanceX() {
            for (int i = 0; i < size; i++) {
                edges[i].x += edges[i].dx;
            }
        }

        private void grow(int needed) {
            int newCap = Math.max(edges.length << 1, size + needed);
            Edge[] newEdges = new Edge[newCap];
            System.arraycopy(edges, 0, newEdges, 0, size);
            edges = newEdges;
        }

        private static void quickSort(Edge[] arr, int lo, int hi) {
            while (lo < hi) {
                if (hi - lo < INSERTION_SORT_THRESHOLD) {
                    insertionSort(arr, lo, hi);
                    return;
                }

                int mid = (lo + hi) >>> 1;
                if (arr[mid].x < arr[lo].x) swap(arr, lo, mid);
                if (arr[hi].x  < arr[lo].x) swap(arr, lo, hi);
                if (arr[hi].x  < arr[mid].x) swap(arr, mid, hi);
                swap(arr, mid, hi - 1);
                double pivot = arr[hi - 1].x;

                int i = lo, j = hi - 1;
                while (true) {
                    while (arr[++i].x < pivot) {}
                    while (arr[--j].x > pivot) {}
                    if (i >= j) break;
                    swap(arr, i, j);
                }
                swap(arr, i, hi - 1);

                if (i - lo < hi - i) {
                    quickSort(arr, lo, i - 1);
                    lo = i + 1;
                } else {
                    quickSort(arr, i + 1, hi);
                    hi = i - 1;
                }
            }
        }

        private static void insertionSort(Edge[] arr, int lo, int hi) {
            for (int i = lo + 1; i <= hi; i++) {
                Edge key = arr[i];
                double keyX = key.x;
                int j = i - 1;
                while (j >= lo && arr[j].x > keyX) {
                    arr[j + 1] = arr[j];
                    j--;
                }
                arr[j + 1] = key;
            }
        }

        private static void swap(Edge[] arr, int i, int j) {
            Edge t = arr[i];
            arr[i] = arr[j];
            arr[j] = t;
        }
    }
}