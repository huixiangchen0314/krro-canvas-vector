package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayList;
import java.util.List;

public class TileClipper {

    /**
     * 对曲线进行画布裁剪，将结果输出到 out 列表。
     * 闭合曲线不裁剪，直接添加原曲线（不拷贝）；
     * 开放曲线逐段裁剪，丢弃完全在画布外的段，余下连续的可见段合并为若干子曲线。
     * 注意：返回的曲线直接引用了原始曲线的控制点，不要再修改它们。
     */
    public static void clip(List<Curve> out, Curve curve, int width, int height) {
        if (curve == null || curve.getPoints().isEmpty()) return;

        // 闭合曲线直接添加，无需裁剪
        if (curve.isClosed()) {
            out.add(curve);
            return;
        }

        int segCount = curve.getSegmentCount();
        List<ControlPoint> currentPoints = new ArrayList<>();

        for (int i = 0; i < segCount; i++) {
            ControlPoint cpStart = curve.getPoints().get(i);
            ControlPoint cpEnd   = curve.getPoints().get((i + 1) % curve.getPoints().size());

            boolean visible;
            if (Segments.isStraightLine(cpStart, cpEnd)) {
                // 快速路径：退化（直线）段，仅用端点判断包围盒
                double minX = Math.min(cpStart.getX(), cpEnd.getX());
                double maxX = Math.max(cpStart.getX(), cpEnd.getX());
                double minY = Math.min(cpStart.getY(), cpEnd.getY());
                double maxY = Math.max(cpStart.getY(), cpEnd.getY());
                visible = !(maxX < 0 || minX > width || maxY < 0 || minY > height);
            } else {
                // 曲线段：计算精确的贝塞尔包围盒
                Segment seg = curve.getSegment(i);
                AABB aabb = Segments.aabb(seg);
                visible = !(aabb.getMaxX() < 0 || aabb.getMinX() > width ||
                        aabb.getMaxY() < 0 || aabb.getMinY() > height);
            }

            if (visible) {
                if (currentPoints.isEmpty()) {
                    currentPoints.add(cpStart);
                }
                currentPoints.add(cpEnd);
            } else {
                if (!currentPoints.isEmpty()) {
                    out.add(new Curve(currentPoints, false));
                    currentPoints = new ArrayList<>();
                }
            }
        }
        if (!currentPoints.isEmpty()) {
            out.add(new Curve(currentPoints, false));
        }
    }

    /** 将多边形裁剪到矩形区域，返回新多边形顶点，若完全在外则返回 null */
    public static double[] clip(double[] poly,
                                int rx, int ry,
                                int rw, int rh) {
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

    private static double[] clipEdge(double[] poly, double limit, boolean keepGreater, boolean isX) {
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