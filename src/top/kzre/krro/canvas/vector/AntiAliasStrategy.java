package top.kzre.krro.canvas.vector;

public interface AntiAliasStrategy {
    /**
     * 对多边形进行抗锯齿填充，结果写入 dst
     * @param dst 目标RGBA数组 (w*h*4)
     * @param w 目标宽度
     * @param h 目标高度
     * @param polygon 闭合多边形顶点 [x0,y0, x1,y1, ...]
     * @param color 填充颜色 [R,G,B,A]
     * @param rule 填充规则
     */
    void fill(float[] dst, int w, int h,
              double[] polygon, float[] color,
              FillRule rule);
}