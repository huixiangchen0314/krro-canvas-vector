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
}