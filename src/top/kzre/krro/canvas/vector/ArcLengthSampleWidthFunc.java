package top.kzre.krro.canvas.vector;

/**
 * 基于弧长采样点的可变宽度函数。
 *
 * <p>{@code sParams[i]} 和 {@code widths[i]} 一一对应，
 * {@code sParams} 严格递增、位于 [0, 1]。{@link #map(double)} 线性插值。</p>
 */
public final class ArcLengthSampleWidthFunc implements WidthFunction {

    private final double[] sParams;
    private final double[] widths;
    private final double maxWidth;
    private final int n;
    private final double[] knots;

    public ArcLengthSampleWidthFunc(double[] sParams, double[] widths) {
        if (sParams == null || widths == null) {
            throw new IllegalArgumentException("arrays must not be null");
        }
        if (sParams.length != widths.length) {
            throw new IllegalArgumentException("arrays must have the same length");
        }
        if (sParams.length == 0) {
            throw new IllegalArgumentException("at least one sample required");
        }
        for (int i = 1; i < sParams.length; i++) {
            if (!(sParams[i] > sParams[i - 1])) {
                throw new IllegalArgumentException(
                        "sParams must be strictly increasing at index " + i);
            }
        }
        this.sParams = sParams.clone();
        this.widths = widths.clone();
        this.n = sParams.length;

        double m = widths[0];
        for (int i = 1; i < n; i++) {
            if (widths[i] > m) m = widths[i];
        }
        this.maxWidth = m;

        // 中间采样点即斜率突变处
        if (n <= 2) {
            this.knots = new double[0];
        } else {
            this.knots = new double[n - 2];
            System.arraycopy(this.sParams, 1, this.knots, 0, n - 2);
        }
    }

    @Override
    public double map(double s) {
        if (s <= sParams[0]) return widths[0];
        if (s >= sParams[n - 1]) return widths[n - 1];

        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (sParams[mid] <= s) lo = mid;
            else hi = mid;
        }
        double s0 = sParams[lo];
        double s1 = sParams[hi];
        if (s1 == s0) return widths[lo];
        double ratio = (s - s0) / (s1 - s0);
        return widths[lo] + ratio * (widths[hi] - widths[lo]);
    }

    @Override
    public double maxWidth() {
        return maxWidth;
    }

    @Override
    public double[] knots() {
        return knots;
    }
}