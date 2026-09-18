package top.kzre.krro.canvas.vector;

import java.util.Arrays;

/**
 * 三角化后的多边形——GPU 光栅化的直接输入。
 * <p>
 * 顶点为视口空间坐标（x, y），索引描述三角形。
 * 采用 fan 三角化——不要求三角形互不重叠、不要求在轮廓内部。
 * 精确的填充规则（non-zero / even-odd）由 stencil buffer 处理，
 * 因此这里只需要保证三角形的覆盖关系正确即可。
 * </p>
 * <p>
 * 内部存储：
 * <ul>
 *   <li>{@code vertices}：[x0, y0, x1, y1, ...]，float 交错</li>
 *   <li>{@code indices}：[i0, i1, i2, i3, i4, i5, ...]，每 3 个构成一个三角形</li>
 * </ul>
 * 不可变——构造后内部数组不被修改。
 * </p>
 *
 * @see Polygon
 */
public final class TessellatedPolygon {

    private static final TessellatedPolygon EMPTY =
            new TessellatedPolygon(new float[0], new int[0]);

    private final float[] vertices;
    private final int[]   indices;

    public TessellatedPolygon(float[] vertices, int[] indices) {
        if (vertices == null || indices == null) {
            throw new IllegalArgumentException("vertices and indices must not be null");
        }
        if (vertices.length % 2 != 0) {
            throw new IllegalArgumentException("vertices length must be even");
        }
        if (indices.length % 3 != 0) {
            throw new IllegalArgumentException("indices length must be multiple of 3");
        }
        this.vertices = vertices;
        this.indices  = indices;
    }

    public static TessellatedPolygon empty() {
        return EMPTY;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public int getVertexCount() {
        return vertices.length / 2;
    }

    public int getTriangleCount() {
        return indices.length / 3;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }

    // ---------- Object ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TessellatedPolygon)) return false;
        TessellatedPolygon other = (TessellatedPolygon) o;
        return Arrays.equals(vertices, other.vertices)
                && Arrays.equals(indices, other.indices);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(vertices) + Arrays.hashCode(indices);
    }

    @Override
    public String toString() {
        return "TessellatedPolygon{vertices=" + getVertexCount()
                + ", triangles=" + getTriangleCount() + '}';
    }
}