package top.kzre.krro.canvas.vector;

/**
 * 归一化 t 参数数组的重映射工具。
 *
 * <p><b>语义：</b>t-params 是曲线上每个采样点的归一化参数位置，
 * 编码方式为 {@code t = (segIndex + local) / segCount}——即把「第几段、段内位置」
 * 压缩到 [0, 1] 的单一浮点值。数组单调不减，首元素 ≥ 0，末元素 ≤ 1。
 *
 * <p><b>用途：</b>拓扑变化（插段 / 删段 / 切断）后，采样点在旧曲线上的物理位置
 * 需要重新映射到新曲线的参数空间。本工具按操作类型提供对应的重映射公式。
 *
 * <p><b>约定：</b>
 * <ul>
 *   <li>输入数组不被修改——所有方法返回新数组</li>
 *   <li>输入 t 值超出 [0, 1] 视为调用方错误，方法不保证结果合理</li>
 *   <li>t = 1.0 视作「末段末尾」，不产生越界段索引</li>
 *   <li>采样点落在被删段内时，本工具不做归属决策——保留数值，
 *       由调用方过滤或合并</li>
 * </ul>
 *
 * <p><b>分层：</b>本类属 canvas.vector 层——服务于矢量图层的采样点表示。
 * 与 bezier2d 的归一化参数表示一致，但不依赖具体曲线类型：
 * 任意以「归一化 t」索引采样点的曲线（Bezier / Catmull-Rom）均可使用。
 *
 * <p>纯静态方法，无副作用，无内部状态。
 */
public final class TParamsUtils {

    private TParamsUtils() {}

    /**
     * 头插一段后的 t-params 重映射。
     * <p>旧 t=0 对应新 t=1/(n+1)，旧 t=1 对应新 t=1。
     * <pre>
     *   new_t[0] = 0
     *   new_t[i+1] = (old_t[i] * n + 1) / (n + 1)
     * </pre>
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @return 新 t 参数数组，长度 = oldTParams.length + 1
     */
    public static double[] extrudeHead(double[] oldTParams, int oldSegCount) {
        int n = oldSegCount;
        double inv = 1.0 / (n + 1);
        double[] out = new double[oldTParams.length + 1];
        out[0] = 0.0;
        for (int i = 0; i < oldTParams.length; i++) {
            out[i + 1] = (oldTParams[i] * n + 1.0) * inv;
        }
        return out;
    }

    /**
     * 尾插一段后的 t-params 重映射。
     * <p>旧 t=0 对应新 t=0，旧 t=1 对应新 t=n/(n+1)。
     * <pre>
     *   new_t[i] = old_t[i] * n / (n + 1)
     *   new_t[last] = 1
     * </pre>
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @return 新 t 参数数组，长度 = oldTParams.length + 1
     */
    public static double[] extrudeTail(double[] oldTParams, int oldSegCount) {
        int n = oldSegCount;
        double inv = 1.0 / (n + 1);
        double[] out = new double[oldTParams.length + 1];
        for (int i = 0; i < oldTParams.length; i++) {
            out[i] = oldTParams[i] * n * inv;
        }
        out[oldTParams.length] = 1.0;
        return out;
    }

    /**
     * 在段 insertIdx 处插入一段后的 t-params 重映射。
     * <p>段索引 >= insertIdx 的采样段索引 +1；其余不变。
     * 新采样点位置由调用方决定，本方法不插入新采样。
     *
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @param insertIdx   插入位置（新段插在旧段 insertIdx 之前），范围 [0, n]
     * @return 新 t 参数数组，长度 = oldTParams.length
     */
    public static double[] insertSegmentAt(double[] oldTParams, int oldSegCount, int insertIdx) {
        if (insertIdx < 0 || insertIdx > oldSegCount) {
            throw new IndexOutOfBoundsException(
                    "insertIdx " + insertIdx + " out of [0, " + oldSegCount + "]");
        }
        int n = oldSegCount;
        int newN = n + 1;
        double inv = 1.0 / newN;
        double[] out = new double[oldTParams.length];
        for (int i = 0; i < oldTParams.length; i++) {
            double t = oldTParams[i];
            int oldSeg = (t >= 1.0) ? n - 1 : (int) Math.floor(t * n);
            double local = t * n - oldSeg;
            int newSeg = (oldSeg >= insertIdx) ? oldSeg + 1 : oldSeg;
            out[i] = (newSeg + local) * inv;
        }
        return out;
    }

    /**
     * 删除段 deleteIdx 后的 t-params 重映射。
     * <p>段索引 > deleteIdx 的采样段索引 -1。
     * <strong>落在被删段内的采样（段索引 == deleteIdx）由调用方过滤</strong>——
     * 本方法保留它们的数值（会产生越界段索引），调用方负责丢弃或合并。
     *
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @param deleteIdx   被删除的段索引，范围 [0, n)
     * @return 新 t 参数数组，长度 = oldTParams.length（未过滤）
     */
    public static double[] deleteSegmentAt(double[] oldTParams, int oldSegCount, int deleteIdx) {
        if (deleteIdx < 0 || deleteIdx >= oldSegCount) {
            throw new IndexOutOfBoundsException(
                    "deleteIdx " + deleteIdx + " out of [0, " + oldSegCount + ")");
        }
        int n = oldSegCount;
        int newN = n - 1;
        double inv = 1.0 / newN;
        double[] out = new double[oldTParams.length];
        for (int i = 0; i < oldTParams.length; i++) {
            double t = oldTParams[i];
            int oldSeg = (t >= 1.0) ? n - 1 : (int) Math.floor(t * n);
            double local = t * n - oldSeg;
            int newSeg = (oldSeg > deleteIdx) ? oldSeg - 1 : oldSeg;
            out[i] = (newSeg + local) * inv;
        }
        return out;
    }

    /**
     * 在锚点 idx 处切断曲线后的 t-params 重映射。
     * <p>切点位于锚点 idx 处——即段 idx 和段 idx-1 的边界。
     * 左曲线保留段 [0, idx-1]，右曲线保留段 [idx, n-1]。
     * 两边的采样点按段归属分配到左右两侧。
     *
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @param cutIdx      切点对应的锚点索引（= 左曲线段数），范围 [1, n-1]
     * @return [leftTParams, rightTParams]，未过滤切断边界的采样（由调用方决定归属）
     */
    public static double[][] cutAt(double[] oldTParams, int oldSegCount, int cutIdx) {
        if (cutIdx <= 0 || cutIdx >= oldSegCount) {
            throw new IndexOutOfBoundsException(
                    "cutIdx " + cutIdx + " out of [1, " + oldSegCount + ")");
        }
        int n = oldSegCount;
        int leftSegCount = cutIdx;
        int rightSegCount = n - cutIdx;

        // 先按段划分采样
        double cutT = (double) cutIdx / n;
        int leftCount = 0;
        for (double t : oldTParams) {
            if (t < cutT) leftCount++;
        }

        double[] left = new double[leftCount];
        double[] right = new double[oldTParams.length - leftCount];
        int li = 0, ri = 0;
        for (double t : oldTParams) {
            if (t < cutT) {
                left[li++] = t * n / leftSegCount;   // 映射到左曲线归一化参数
            } else {
                right[ri++] = (t * n - cutIdx) / rightSegCount;
            }
        }
        return new double[][]{left, right};
    }
}