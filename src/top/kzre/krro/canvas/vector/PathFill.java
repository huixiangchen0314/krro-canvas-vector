package top.kzre.krro.canvas.vector;

import java.util.List;

public final class PathFill extends PathRenderer {

    @Override
    public Polygon render(Path path, RenderContext context) {
        // 只处理闭合路径
        if (!path.isClosed()) {
            return null;
        }

        List<Vertex> vertices = path.getVertices();
        int n = vertices.size();
        if (n < 3) {
            return null; // 至少需要三个顶点才能构成有效多边形
        }

        // 提取顶点坐标
        double[] coords = new double[n * 2];
        for (int i = 0; i < n; i++) {
            Vertex v = vertices.get(i);
            coords[2 * i] = v.getX();
            coords[2 * i + 1] = v.getY();
        }

       return new Polygon(coords);

    }
}