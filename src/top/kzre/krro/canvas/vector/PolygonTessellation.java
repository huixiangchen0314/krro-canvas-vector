package top.kzre.krro.canvas.vector;

/**
 * 多边形三角化——fan 三角化，产出 GPU 可绘制的三角形集合。
 * <p>
 * 不要求三角形互不重叠、不要求在轮廓内部。自交、孔洞、填充规则
 * 全部由 GPU 的 stencil buffer 处理。
 * </p>
 */
public final class PolygonTessellation {
    private PolygonTessellation() {}

    /**
     * 以多边形第 0 个顶点为中心，fan 三角化。
     *
     * <pre>
     *   顶点: v0, v1, v2, v3, v4
     *   三角形: (v0,v1,v2), (v0,v2,v3), (v0,v3,v4)
     * </pre>
     *
     * @param polygon 源多边形，顶点坐标视为视口空间
     * @return 三角化结果；顶点数 &lt; 3 时返回 {@link TessellatedPolygon#empty()}
     */
    public static TessellatedPolygon fanTessellate(Polygon polygon) {
        int n = polygon.getVertexCount();
        if (n < 3) return TessellatedPolygon.empty();

        float[] verts = new float[n * 2];
        for (int i = 0; i < n; i++) {
            verts[2 * i]     = (float) polygon.getX(i);
            verts[2 * i + 1] = (float) polygon.getY(i);
        }

        int[] indices = new int[(n - 2) * 3];
        int idx = 0;
        for (int i = 1; i < n - 1; i++) {
            indices[idx++] = 0;
            indices[idx++] = i;
            indices[idx++] = i + 1;
        }

        return new TessellatedPolygon(verts, indices);
    }
}