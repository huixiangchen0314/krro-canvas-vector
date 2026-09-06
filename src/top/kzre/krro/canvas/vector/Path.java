package top.kzre.krro.canvas.vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class Path {
    private final List<Vertex> vertices;
    private final boolean closed;

    public Path(List<Vertex> vertices, boolean closed) {
        this.vertices = new ArrayList<>(vertices);
        this.closed = closed;
    }

    public List<Vertex> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    public boolean isClosed() {
        return closed;
    }

    public int getVertexCount() {
        return vertices.size();
    }

    public Vertex getVertex(int index) {
        return vertices.get(index);
    }

    public double getX(int index) {
        return vertices.get(index).getX();
    }

    public double getY(int index) {
        return vertices.get(index).getY();
    }

    public double getWidth(int index) {
        return vertices.get(index).getWidth();
    }

    public boolean isEmpty() {
        return vertices.size() < 2;
    }

    /**
     * 原地简化路径，移除距离小于给定阈值的顶点（保留宽度信息）。
     * 对于闭合路径，还会检查首尾是否过近。
     * @param minDistance 最小顶点间距（像素）
     */
    public Path simplify(double minDistance) {

        if (this.getVertexCount() < 3 || minDistance <= 0) {
            return this;
        }
        List<Vertex> vertices = this.getVertices();
        List<Vertex> simplified = new ArrayList<>();
        Vertex prev = vertices.get(0);
        simplified.add(prev);
        for (int i = 1; i < vertices.size(); i++) {
            Vertex curr = vertices.get(i);
            double dist = Math.hypot(curr.getX() - prev.getX(), curr.getY() - prev.getY());
            if (dist >= minDistance) {
                simplified.add(curr);
                prev = curr;
            }
        }
        // 闭合路径首尾去重
        if (this.isClosed() && simplified.size() > 2) {
            Vertex first = simplified.get(0);
            Vertex last = simplified.get(simplified.size() - 1);
            double dist = Math.hypot(last.getX() - first.getX(), last.getY() - first.getY());
            if (dist < minDistance) {
                simplified.remove(simplified.size() - 1);
            }
        }
        return new Path(simplified, this.isClosed());
    }

    @Override
    public String toString() {
        return "Path{vertices=" + vertices + ", closed=" + closed + "}";
    }
}