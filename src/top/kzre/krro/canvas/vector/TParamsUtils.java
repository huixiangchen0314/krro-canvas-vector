package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.ArcLengthUtils;
import top.kzre.curve.bezier2d.Curve;
import top.kzre.curve.bezier2d.TableMapping;

import java.util.Arrays;

/**
 * 归一化 t 参数数组的重映射工具。
 *
 * <p><b>语义：</b>t-params 是曲线上每个采样点的归一化参数位置，
 * 编码方式为 {@code t = (segIndex + local) / segCount}——即把「第几段、段内位置」
 * 压缩到 [0, 1] 的单一浮点值。数组单调不减，首元素 ≥ 0，末元素 ≤ 1。
 *
 * <p><b>用途：</b>拓扑变化（插段 / 删段 / 切断 / 合并 / 重采样）后，
 * 采样点在旧曲线上的物理位置需要重新映射到新曲线的参数空间。
 * 本工具按操作类型提供对应的重映射公式。
 *
 * <p><b>约定：</b>
 * <ul>
 *   <li>输入数组不被修改——所有方法返回新数组</li>
 *   <li>输入 t 值超出 [0, 1] 视为调用方错误，方法不保证结果合理</li>
 *   <li>t = 1.0 视作「末段末尾」，不产生越界段索引</li>
 *   <li>采样点落在被删段内时，本工具不做归属决策——保留数值，
 *       由调用方过滤或合并</li>
 *   <li>切分 / 合并时，连接点采样两侧各保留一份（调用方自行去重）</li>
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

    // ═══════════════════════════════════════════════════════════
    // 段 / 全局参数互转
    // ═══════════════════════════════════════════════════════════

    /**
     * 全局归一化参数 → 段索引。
     * <p>{@code segIdx = floor(t * segCount)}；t = 1.0 归入末段。
     */
    public static int segmentOf(double t, int segCount) {
        if (t >= 1.0) return segCount - 1;
        int idx = (int) Math.floor(t * segCount);
        return Math.max(0, Math.min(idx, segCount - 1));
    }

    /**
     * 全局归一化参数 → 段内局部参数 [0, 1]。
     */
    public static double localOf(double t, int segCount) {
        int seg = segmentOf(t, segCount);
        return t * segCount - seg;
    }

    /**
     * 段索引 + 段内局部参数 → 全局归一化参数。
     */
    public static double globalT(int segIdx, double localT, int segCount) {
        return (segIdx + localT) / segCount;
    }

    // ═══════════════════════════════════════════════════════════
    // 插段
    // ═══════════════════════════════════════════════════════════

    /**
     * 头插一段后的 t-params 重映射。
     * <p>旧 t=0 对应新 t=1/(n+1)，旧 t=1 对应新 t=1。
     * <pre>
     *   new_t[0]    = 0
     *   new_t[i+1]  = (old_t[i] * n + 1) / (n + 1)
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
     *   new_t[i]    = old_t[i] * n / (n + 1)
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
            int oldSeg = segmentOf(t, n);
            double local = t * n - oldSeg;
            int newSeg = (oldSeg >= insertIdx) ? oldSeg + 1 : oldSeg;
            out[i] = (newSeg + local) * inv;
        }
        return out;
    }

    /**
     * 删除段结果。包含重映射后的 t 参数以及原采样索引的保留情况。
     */
    public static final class DeleteResult {
        /** 重映射后的归一化 t 参数（已过滤被删段内的采样） */
        public final double[] tParams;
        /** 保留的原始采样索引（升序） */
        public final int[]    keptIdx;

        DeleteResult(double[] tParams, int[] keptIdx) {
            this.tParams = tParams;
            this.keptIdx = keptIdx;
        }
    }

    /**
     * 删除段 deleteIdx 后的 t-params 重映射。
     *
     * <p>落在被删段 {@code [deleteIdx/n, (deleteIdx+1)/n)} 内的采样被丢弃；
     * 其余采样的段索引 {@code > deleteIdx} 则 -1，再除以新段数归一化。
     *
     * <p>返回的 {@code keptIdx} 是原始数组索引——调用方按相同归属切分
     * 其他平行数组（如 :t-width 的 width-samples）。
     *
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @param deleteIdx   被删除的段索引，范围 [0, n)
     * @return 删除结果（t 参数 + 保留索引）
     */
    public static DeleteResult deleteSegmentAt(double[] oldTParams,
                                               int oldSegCount,
                                               int deleteIdx) {
        if (deleteIdx < 0 || deleteIdx >= oldSegCount) {
            throw new IndexOutOfBoundsException(
                    "deleteIdx " + deleteIdx + " out of [0, " + oldSegCount + ")");
        }
        int n = oldSegCount;
        int newN = n - 1;
        double inv = 1.0 / newN;

        double segTStart = (double) deleteIdx / n;
        double segTEnd   = (double) (deleteIdx + 1) / n;

        // 第一遍：统计保留数量
        int keepCount = 0;
        for (double t : oldTParams) {
            if (t < segTStart || t >= segTEnd) {
                keepCount++;
            }
        }

        // 第二遍：填充
        double[] out     = new double[keepCount];
        int[]    keptIdx = new int[keepCount];
        int k = 0;
        for (int i = 0; i < oldTParams.length; i++) {
            double t = oldTParams[i];
            if (t >= segTStart && t < segTEnd) continue;

            int oldSeg = segmentOf(t, n);
            double local = t * n - oldSeg;
            int newSeg = (oldSeg > deleteIdx) ? oldSeg - 1 : oldSeg;
            out[k] = (newSeg + local) * inv;
            keptIdx[k] = i;
            k++;
        }
        return new DeleteResult(out, keptIdx);
    }

    // ═══════════════════════════════════════════════════════════
    // 切分
    // ═══════════════════════════════════════════════════════════

    /**
     * 切分结果。包含重映射后的 t 参数以及原始采样索引的分组。
     * <p>原始索引让调用方可以按相同的归属切分其他与采样一一对应的数组
     * （如 :t-width 的 width-samples）——保证索引不错位。
     */
    public static final class SplitResult {
        /** 左曲线的归一化 t 参数 */
        public final double[] leftTParams;
        /** 右曲线的归一化 t 参数 */
        public final double[] rightTParams;
        /** 归入左侧的原采样索引（升序） */
        public final int[] leftIdx;
        /** 归入右侧的原采样索引（升序） */
        public final int[] rightIdx;

        SplitResult(double[] leftTParams, double[] rightTParams,
                    int[] leftIdx, int[] rightIdx) {
            this.leftTParams  = leftTParams;
            this.rightTParams = rightTParams;
            this.leftIdx      = leftIdx;
            this.rightIdx     = rightIdx;
        }
    }

    /**
     * 在锚点 splitIdx 处切分曲线后的 t-params 重映射。
     * <p>切点位于锚点 splitIdx 处——即段 splitIdx 和段 splitIdx-1 的边界。
     * 左曲线保留段 [0, splitIdx-1]，右曲线保留段 [splitIdx, n-1]。
     * <p>切点上的采样同时出现在两侧——调用方按需去重。
     * <p>返回的 {@code leftIdx} / {@code rightIdx} 是原始数组索引，
     * 调用方用它们切分同源的其他数组（保证归属一致）。
     *
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数 n
     * @param splitIdx    切点对应的锚点索引（= 左曲线段数），范围 [1, n-1]
     * @return 切分结果（t 参数 + 索引分组）
     */
    public static SplitResult splitAt(double[] oldTParams, int oldSegCount, int splitIdx) {
        if (splitIdx <= 0 || splitIdx >= oldSegCount) {
            throw new IndexOutOfBoundsException(
                    "splitIdx " + splitIdx + " out of [1, " + oldSegCount + ")");
        }
        int n = oldSegCount;
        int leftSegCount  = splitIdx;
        int rightSegCount = n - splitIdx;
        double tSplit = (double) splitIdx / n;
        double eps = 1e-12;

        // 第一遍：统计两侧数量
        int lc = 0, rc = 0;
        for (double t : oldTParams) {
            if (t <= tSplit + eps) lc++;
            if (t >= tSplit - eps) rc++;
        }

        // 第二遍：填充 t 参数和索引
        double[] leftTParams  = new double[lc];
        double[] rightTParams = new double[rc];
        int[]    leftIdx      = new int[lc];
        int[]    rightIdx     = new int[rc];
        int li = 0, ri = 0;
        for (int i = 0; i < oldTParams.length; i++) {
            double t = oldTParams[i];
            if (t <= tSplit + eps) {
                leftTParams[li] = t * n / leftSegCount;
                leftIdx[li] = i;
                li++;
            }
            if (t >= tSplit - eps) {
                rightTParams[ri] = (t * n - splitIdx) / rightSegCount;
                rightIdx[ri] = i;
                ri++;
            }
        }
        return new SplitResult(leftTParams, rightTParams, leftIdx, rightIdx);
    }

    // ═══════════════════════════════════════════════════════════
    // 合并
    // ═══════════════════════════════════════════════════════════

    /**
     * 合并结果。包含合并后的 t 参数以及原始采样索引的分组。
     * <p>原始索引让调用方可以按相同的归属合并其他与采样一一对应的数组
     * （如 :t-width 的 width-samples）。
     */
    public static final class JoinResult {
        /** 合并后的归一化 t 参数 */
        public final double[] tParams;
        /** 左曲线采样在合并结果中的索引（升序）——连接点采样包含在内 */
        public final int[] leftKeepIdx;
        /** 右曲线采样在合并结果中的索引（升序）——连接点采样不在内 */
        public final int[] rightKeepIdx;
        /** 连接点采样来自 leftTParams 的哪个索引 */
        public final int leftConnectorIdx;
        /** 连接点采样来自 rightTParams 的哪个索引 */
        public final int rightConnectorIdx;

        JoinResult(double[] tParams, int[] leftKeepIdx, int[] rightKeepIdx,
                   int leftConnectorIdx, int rightConnectorIdx) {
            this.tParams           = tParams;
            this.leftKeepIdx       = leftKeepIdx;
            this.rightKeepIdx      = rightKeepIdx;
            this.leftConnectorIdx  = leftConnectorIdx;
            this.rightConnectorIdx = rightConnectorIdx;
        }
    }

    /**
     * 合并两条曲线的 t-params。
     * <p>左曲线的段范围 [0, nL] 映射到新曲线的 [0, nL/(nL+nR)]；
     * 右曲线的段范围 [0, nR] 映射到 [nL/(nL+nR), 1]。
     * <p>连接点采样只保留一份——左曲线的末采样保留在新曲线上，
     * 右曲线的首采样被丢弃（与左末采样重合）。
     * <p>返回的索引让调用方按相同归属合并其他平行数组（如 width-samples）。
     * 连接点的宽度由调用方决定——可以取左值、右值或平均。
     *
     * @param leftTParams   左曲线归一化 t 参数
     * @param leftSegCount  左曲线段数 nL
     * @param rightTParams  右曲线归一化 t 参数
     * @param rightSegCount 右曲线段数 nR
     * @return 合并结果（t 参数 + 索引分组）
     */
    public static JoinResult join(double[] leftTParams, int leftSegCount,
                                  double[] rightTParams, int rightSegCount) {
        if (leftSegCount < 1 || rightSegCount < 1) {
            throw new IllegalArgumentException(
                    "seg counts must be >= 1: left=" + leftSegCount + ", right=" + rightSegCount);
        }
        if (leftTParams.length == 0 || rightTParams.length == 0) {
            throw new IllegalArgumentException("t-params must be non-empty");
        }
        int totalSeg = leftSegCount + rightSegCount;
        double scaleL = (double) leftSegCount / totalSeg;
        double scaleR = (double) rightSegCount / totalSeg;
        double splitT = (double) leftSegCount / totalSeg;

        int len = leftTParams.length + rightTParams.length - 1;
        double[] out      = new double[len];
        int[] leftKeep    = new int[leftTParams.length];       // 左全部保留（含连接点）
        int[] rightKeep   = new int[rightTParams.length - 1];  // 右去掉首点

        int k = 0;
        // 左曲线采样：全部保留
        for (int i = 0; i < leftTParams.length - 1; i++) {
            out[k] = leftTParams[i] * scaleL;
            leftKeep[i] = k;
            k++;
        }
        // 连接点：新曲线上的中间位置（保留左曲线的末采样位置）
        out[k] = splitT;
        leftKeep[leftTParams.length - 1] = k;
        k++;
        // 右曲线采样：去掉首点，其余偏移
        for (int i = 1; i < rightTParams.length; i++) {
            out[k] = splitT + rightTParams[i] * scaleR;
            rightKeep[i - 1] = k;
            k++;
        }
        return new JoinResult(out, leftKeep, rightKeep,
                leftTParams.length - 1, 0);
    }

    /**
     * 曲线反向后的 t-params 重映射。
     *
     * <p>每个采样点的位置从 {@code t} 变为 {@code 1-t}；同时数组顺序反转，
     * 结果仍保持升序。
     *
     * <p>公式：{@code new_t[i] = 1 - old_t[n-1-i]}。
     *
     * <p><b>特例</b>：均匀分布的 t-params（{@code [0, 1/(n-1), ..., 1]}）
     * 反向后不变——因为值对称。
     *
     * @param oldTParams 旧归一化 t 参数（升序）
     * @return 新 t 参数数组，长度 = oldTParams.length，升序
     */
    public static double[] reverse(double[] oldTParams) {
        int n = oldTParams.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = 1.0 - oldTParams[n - 1 - i];
        }
        return out;
    }

    /**
     * 按弧长比例重映射 t-params。
     *
     * <p>对每个旧 t：找它在旧曲线上的累积弧长比例，然后在新曲线上
     * 找相同弧长比例对应的 t。
     *
     * <p>用于 reform / fit-segment 等改变曲线形状、希望采样点物理位置
     * （弧长比例）保持不变的场景。
     *
     * <p>内部用 {@link ArcLengthUtils#sample} 构建两端的 {@link TableMapping}，
     * 插值由 {@code getS} / {@code getT} 完成。
     *
     * @param oldTParams 旧 t 参数（升序 [0, 1]）
     * @param oldCurve   旧曲线
     * @param newCurve   新曲线
     * @return 重映射后的 t 参数，长度 = oldTParams.length，升序
     */
    public static double[] remapByArcLength(double[] oldTParams,
                                            Curve oldCurve, Curve newCurve) {
        int n = oldTParams.length;
        if (n == 0) return new double[0];

        TableMapping oldMap = ArcLengthUtils.sample(oldCurve, 200);
        TableMapping newMap = ArcLengthUtils.sample(newCurve, 200);

        double oldMax = oldMap.getMaxS();
        double newMax = newMap.getMaxS();

        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double sOld   = oldMap.getS(oldTParams[i]);           // 旧 t → 旧累计弧长
            double sNorm  = (oldMax > 1e-12) ? sOld / oldMax : oldTParams[i];
            out[i]        = (newMax > 1e-12)
                    ? newMap.getT(sNorm * newMax)       // 归一化弧长 → 新 t
                    : sNorm;
        }
        return out;
    }

    // ═══════════════════════════════════════════════════════════
    // 重采样
    // ═══════════════════════════════════════════════════════════

    /**
     * 将 t-params 从 oldSegCount 重采样到 newSegCount。
     * <p>按参数位置线性映射：{@code new_t = old_t * oldN / newN}。
     * 采样数量不变，仅缩放归一化位置。
     *
     * <p>注意：本方法不改变采样点的物理位置——只是把「旧段上的位置」
     * 重新映射到「新段上的对应位置」。适用于「段数变化但采样点数量不变」
     * 的场景（如 reform 中 :t-width 的迁移）。
     *
     * @param oldTParams  旧归一化 t 参数
     * @param oldSegCount 旧段数
     * @param newSegCount 新段数
     * @return 重映射后的 t 参数数组，长度 = oldTParams.length
     */
    public static double[] rescale(double[] oldTParams, int oldSegCount, int newSegCount) {
        if (oldSegCount < 1 || newSegCount < 1) {
            throw new IllegalArgumentException(
                    "seg counts must be >= 1: old=" + oldSegCount + ", new=" + newSegCount);
        }
        double scale = (double) oldSegCount / newSegCount;
        double[] out = new double[oldTParams.length];
        for (int i = 0; i < oldTParams.length; i++) {
            out[i] = oldTParams[i] * scale;
        }
        return out;
    }

    /**
     * 将 t-params 重采样为 targetCount 个（均匀分布）。
     * <p>用于「采样点数量也需要变化」的场景——按归一化位置均匀采样。
     * <p>丢弃原采样值，只保留其位置分布（假设原采样已经近似均匀）。
     *
     * @param targetCount 目标采样数
     * @return 均匀分布的 t 参数数组，长度 = targetCount
     */
    public static double[] uniformResample(int targetCount) {
        if (targetCount < 2) {
            throw new IllegalArgumentException("targetCount must be >= 2");
        }
        double[] out = new double[targetCount];
        for (int i = 0; i < targetCount; i++) {
            out[i] = (double) i / (targetCount - 1);
        }
        return out;
    }



    // ═══════════════════════════════════════════════════════════
    // 校验
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验 t-params 数组是否合法：非空、单调不减、均在 [0, 1] 内。
     *
     * @param tParams t 参数数组
     * @return 合法返回 true
     */
    public static boolean isValid(double[] tParams) {
        if (tParams == null || tParams.length == 0) return false;
        double prev = -1e-12;
        for (double t : tParams) {
            if (t < 0.0 || t > 1.0) return false;
            if (t < prev) return false;
            prev = t;
        }
        return true;
    }

    /**
     * 断言 t-params 合法，否则抛 IllegalArgumentException。
     */
    public static void requireValid(double[] tParams) {
        if (!isValid(tParams)) {
            throw new IllegalArgumentException(
                    "invalid t-params: " + (tParams == null ? "null" : Arrays.toString(tParams)));
        }
    }
}