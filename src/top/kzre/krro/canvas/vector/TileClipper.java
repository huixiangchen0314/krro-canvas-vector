package top.kzre.krro.canvas.vector;

public class TileClipper {

    /** 将多边形裁剪到矩形区域，返回新多边形顶点，若完全在外则返回 null */
    public double[] clip(double[] poly, int rx, int ry, int rw, int rh) {
        if (poly == null || poly.length < 6) return null;
        double xmin = rx;
        double xmax = rx + rw;
        double ymin = ry;
        double ymax = ry + rh;

        double[] current = poly;
        current = clipEdge(current, xmin, true, true);
        if (current == null) return null;
        current = clipEdge(current, xmax, false, true);
        if (current == null) return null;
        current = clipEdge(current, ymin, true, false);
        if (current == null) return null;
        current = clipEdge(current, ymax, false, false);
        return current;
    }

    private double[] clipEdge(double[] poly, double limit, boolean keepGreater, boolean isX) {
        if (poly.length < 6) return null;
        DoubleList out = new DoubleList(poly.length / 2 + 4);
        int n = poly.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x1 = poly[2*i], y1 = poly[2*i+1];
            double x2 = poly[2*j], y2 = poly[2*j+1];

            double v1 = isX ? x1 : y1;
            double v2 = isX ? x2 : y2;

            boolean inside1 = keepGreater ? (v1 >= limit) : (v1 <= limit);
            boolean inside2 = keepGreater ? (v2 >= limit) : (v2 <= limit);

            if (inside1) {
                if (inside2) {
                    out.add(x2, y2);
                } else {
                    double t = (limit - v1) / (v2 - v1);
                    out.add(x1 + t*(x2-x1), y1 + t*(y2-y1));
                }
            } else {
                if (inside2) {
                    double t = (limit - v1) / (v2 - v1);
                    out.add(x1 + t*(x2-x1), y1 + t*(y2-y1));
                    out.add(x2, y2);
                }
            }
        }
        double[] result = out.toArray();
        return result.length >= 6 ? result : null;
    }
}