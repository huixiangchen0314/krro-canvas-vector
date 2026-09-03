package top.kzre.krro.canvas.vector;


class Edge {
    final int ymin, ymax;
    final double dx;
    double x;
    final int winding;

    Edge(double x1, double y1, double x2, double y2) {
        if (y1 < y2) {
            ymin = (int) Math.ceil(y1);
            ymax = (int) Math.ceil(y2);
            dx = (x2 - x1) / (y2 - y1);
            x = x1 + dx * (ymin - y1);
            winding = 1;
        } else {
            ymin = (int) Math.ceil(y2);
            ymax = (int) Math.ceil(y1);
            dx = (x2 - x1) / (y2 - y1);
            x = x2 + dx * (ymin - y2);
            winding = -1;
        }
    }
}
