package top.kzre.krro.canvas.vector;

import java.util.function.DoubleUnaryOperator;

/**
 * 基于弧长参数采样点的可变宽度函数。
 * 提供两个等长的数组：arc-params（归一化弧长 0..1）和对应的宽度值。
 * 使用线性插值计算任意 t 处的宽度，超出范围时钳位到边界值。
 */
public final class ArcLengthSampleWidthFunc implements DoubleUnaryOperator {

    private final double[] arcParams; // 归一化弧长，严格递增
    private final double[] widths;
    private final int n;

    /**
     * @param arcParams 弧长参数数组，单调递增，范围 [0,1]
     * @param widths    对应的宽度数组，长度与 arcParams 相同
     * @throws IllegalArgumentException 如果数组为 null，长度不等，或长度小于1
     */
    public ArcLengthSampleWidthFunc(double[] arcParams, double[] widths) {
        if (arcParams == null || widths == null) {
            throw new IllegalArgumentException("arcParams and widths must not be null");
        }
        if (arcParams.length != widths.length) {
            throw new IllegalArgumentException("arcParams and widths must have the same length");
        }
        if (arcParams.length == 0) {
            throw new IllegalArgumentException("at least one sample required");
        }
        this.arcParams = arcParams.clone(); // 防御性拷贝
        this.widths = widths.clone();
        this.n = arcParams.length;
    }

    @Override
    public double applyAsDouble(double t) {
        // 钳位到 [0,1]
        if (t <= 0.0) return widths[0];
        if (t >= 1.0) return widths[n - 1];

        // 二分查找 t 所在的区间 [arcParams[i], arcParams[i+1]]
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (arcParams[mid] <= t) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        // 此时 t 在 arcParams[lo] 和 arcParams[hi] 之间
        double t0 = arcParams[lo];
        double t1 = arcParams[hi];
        if (t1 == t0) {
            return widths[lo]; // 重合点，不应该发生
        }
        double ratio = (t - t0) / (t1 - t0);
        return widths[lo] + ratio * (widths[hi] - widths[lo]);
    }
}