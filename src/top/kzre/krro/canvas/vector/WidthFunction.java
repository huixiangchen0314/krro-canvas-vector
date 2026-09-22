package top.kzre.krro.canvas.vector;

/**
 * 宽度映射函数。
 *
 * <p>输入 s 是归一化弧长，范围 [0, 1]。之所以不用标准参数 t，
 * 是因为 t 在弯曲处的物理间距不均，按 t 采样会在曲率大的位置
 * 丢失几何细节。弧长在所有位置都均匀。</p>
 */
public interface WidthFunction {

    /** 按归一化弧长 s ∈ [0, 1] 查宽度（像素）。 */
    double map(double s);

    /** [0, 1] 上宽度的上界，用于预分配。 */
    double maxWidth();

    /**
     * 弧长 s 上的斜率突变点，严格递增，位于 (0, 1) 内。
     * 返回数组视为只读。默认空数组。
     */
    default double[] knots() {
        return new double[0];
    }

    /** 差分步长，供 drive / drive2 使用。 */
    default double derivativeStep() {
        return 1e-6;
    }

    default double drive(double x) {
        return drive(x, derivativeStep());
    }

    default double drive(double x, double h) {
        if (x < h) {
            return (map(x + h) - map(x)) / h;
        } else if (x > 1.0 - h) {
            return (map(x) - map(x - h)) / h;
        } else {
            return (map(x + h) - map(x - h)) / (2 * h);
        }
    }

    default double drive2(double x) {
        return drive2(x, derivativeStep());
    }

    default double drive2(double x, double h) {
        if (x < h) {
            double f0 = map(x);
            double f1 = map(x + h);
            double f2 = map(x + 2 * h);
            return (f2 - 2 * f1 + f0) / (h * h);
        } else if (x > 1.0 - h) {
            double f0 = map(x - 2 * h);
            double f1 = map(x - h);
            double f2 = map(x);
            return (f2 - 2 * f1 + f0) / (h * h);
        } else {
            double f0 = map(x - h);
            double f1 = map(x);
            double f2 = map(x + h);
            return (f2 - 2 * f1 + f0) / (h * h);
        }
    }
}