package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.AABB;

import java.util.Arrays;

/**
 * 不可变多边形，顶点按顺序排列，不要求首尾重复（闭合隐式表示）。
 * 内部存储为 double[]，格式 [x0, y0, x1, y1, ..., xn, yn]。
 * 最小顶点数为 3（三角形）。
 */
public final class Polygon {
    private final double[] coords;   // 坐标数组，长度 >= 6 且为偶数
    private final int vertexCount;
    private transient volatile int hashCode;  // 懒加载缓存

    // ---------- 构造函数 ----------
    /**
     * 直接引用传入的数组（不复制），调用方必须保证后续不修改该数组。
     * 数组长度必须 >= 6 且为偶数。
     */
    public Polygon(double[] coords) {
        if (coords == null || coords.length < 6 || coords.length % 2 != 0) {
            throw new IllegalArgumentException("coords must be non-null, length >= 6 and even");
        }
        this.coords = coords;
        this.vertexCount = coords.length / 2;
    }

    /**
     * 安全复制版本，创建数组副本。
     */
    public static Polygon copyOf(double[] coords) {
        return new Polygon(coords.clone());
    }

    // ---------- 访问器 ----------
    public double[] getCoords() {
        return coords;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public double getX(int index) {
        return coords[2 * index];
    }

    public double getY(int index) {
        return coords[2 * index + 1];
    }

    // ---------- 几何计算 ----------
    /**
     * 计算轴对齐包围盒 (AABB)。
     */
    public AABB getBounds() {
        double minX = coords[0], maxX = coords[0];
        double minY = coords[1], maxY = coords[1];
        for (int i = 2; i < coords.length; i += 2) {
            double x = coords[i];
            double y = coords[i + 1];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        return new AABB(minX, minY, maxX, maxY);
    }

    /**
     * 计算多边形面积（带符号，负值表示顺时针方向）。
     */
    public double area() {
        double area = 0.0;
        int n = vertexCount;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double x1 = coords[2 * i], y1 = coords[2 * i + 1];
            double x2 = coords[2 * j], y2 = coords[2 * j + 1];
            area += x1 * y2 - x2 * y1;
        }
        return area * 0.5;
    }

    /**
     * 判断多边形是否有效（至少 3 个顶点且面积不为零）。
     */
    public boolean isValid() {
        return vertexCount >= 3 && Math.abs(area()) > 1e-12;
    }

    /**
     * 对每个顶点应用变换矩阵（4x4 或 3x2 矩阵），返回新 Polygon。
     * @param matrix 长度为 6 的 float[] 或 double[]，表示仿射变换 [m00, m01, m02, m10, m11, m12]。
     */
    public Polygon transform(double[] matrix) {
        if (matrix == null || matrix.length < 6) {
            throw new IllegalArgumentException("matrix must be length >= 6");
        }
        double[] newCoords = new double[coords.length];
        for (int i = 0; i < coords.length; i += 2) {
            double x = coords[i];
            double y = coords[i + 1];
            newCoords[i] = matrix[0] * x + matrix[1] * y + matrix[2];
            newCoords[i + 1] = matrix[3] * x + matrix[4] * y + matrix[5];
        }
        return new Polygon(newCoords);
    }

    /**
     * 平移多边形。
     */
    public Polygon translate(double dx, double dy) {
        return transform(new double[]{1, 0, dx, 0, 1, dy});
    }

    /**
     * 缩放多边形（相对于原点）。
     */
    public Polygon scale(double sx, double sy) {
        return transform(new double[]{sx, 0, 0, 0, sy, 0});
    }

    /**
     * 旋转多边形（绕原点，弧度）。
     */
    public Polygon rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return transform(new double[]{cos, -sin, 0, sin, cos, 0});
    }

    public Polygon clipToRect(double rectMinX, double rectMinY, double width, double height) {
        double maxX = rectMinX + width;
        double maxY = rectMinY + height;
        return clip(rectMinX,rectMinY, maxX, maxY);
    }



    // ---------- 裁剪 ----------
    /**
     * 使用 Sutherland–Hodgman 算法裁剪多边形到矩形区域。
     * @param rectMinX, rectMinY, rectMaxX, rectMaxY 裁剪矩形（包含边界）。
     * @return 裁剪后的多边形，若完全在外则返回 null。
     */
    public Polygon clip(double rectMinX, double rectMinY, double rectMaxX, double rectMaxY) {
        if (rectMinX >= rectMaxX || rectMinY >= rectMaxY) {
            return null;
        }
        // 用四个裁剪边依次裁剪
        Polygon p = this;
        p = clipEdge(p, rectMinX, true, true);   // 左
        if (p == null) return null;
        p = clipEdge(p, rectMaxX, false, true);  // 右
        if (p == null) return null;
        p = clipEdge(p, rectMinY, true, false);  // 下
        if (p == null) return null;
        p = clipEdge(p, rectMaxY, false, false); // 上
        return p;
    }

    private static Polygon clipEdge(Polygon poly, double limit, boolean keepGreater, boolean isX) {
        int n = poly.vertexCount;
        double[] src = poly.coords;
        DoubleList out = new DoubleList(n * 2 + 4);
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double x1 = src[2 * j];
            double y1 = src[2 * j + 1];
            double x2 = src[2 * i];
            double y2 = src[2 * i + 1];
            double v1 = isX ? x1 : y1;
            double v2 = isX ? x2 : y2;

            boolean inside1 = keepGreater ? (v1 > limit - 1e-9) : (v1 < limit + 1e-9);
            boolean inside2 = keepGreater ? (v2 > limit - 1e-9) : (v2 < limit + 1e-9);

            if (inside1) {
                if (inside2) {
                    // 两点都在范围内
                    out.add(x2, y2);
                } else {
                    // 点1 在范围内，输出交点
                    double t = (limit - v1) / (v2 - v1);
                    out.add(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
                }
            } else {
                if (inside2) {
                    // 点2在范围内，输出交点和点2
                    double t = (limit - v1) / (v2 - v1);
                    double ix = x1 + t * (x2 - x1);
                    double iy = y1 + t * (y2 - y1);
                    out.add(ix, iy);
                    // 如果交点与点2距离很近，则不再添加点2（避免超短边）
                    if (Math.hypot(ix - x2, iy - y2) > 1e-6) {
                        out.add(x2, y2);
                    }
                }
            }
        }
        double[] result = out.toArray();
        return (result.length >= 6) ? new Polygon(result) : null;
    }

    // ---------- 工具方法 ----------
    /**
     * 检查多边形是否为空（无顶点）。
     */
    public boolean isEmpty() {
        return vertexCount < 3;
    }

    /**
     * 判断多边形是否包含点（使用射线法，支持凸凹）。
     */
    public boolean containsPoint(double px, double py) {
        boolean inside = false;
        int n = vertexCount;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = coords[2 * i], yi = coords[2 * i + 1];
            double xj = coords[2 * j], yj = coords[2 * j + 1];
            if ((yi > py) != (yj > py) &&
                    px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    // ---------- Object 方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Polygon)) return false;
        Polygon other = (Polygon) o;
        return Arrays.equals(coords, other.coords);
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == 0) {
            h = Arrays.hashCode(coords);
            hashCode = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return "Polygon{" + "vertices=" + vertexCount + ", coords=" + Arrays.toString(coords) + '}';
    }
}