package top.kzre.krro.canvas.vector;

@FunctionalInterface
public interface WidthFunction {
    /**
     * 应用进行函数映射 x 属于 (0, 1) 范围
     */
    double map(double x);

    /**
     * 获取映射函数在 x 位置的一阶导数（前向/后向/中心差分）
     */
    default double drive(double x) {
        double h = 1e-6;
        if (x < h) {
            // 前向差分
            return (map(x + h) - map(x)) / h;
        } else if (x > 1.0 - h) {
            // 后向差分
            return (map(x) - map(x - h)) / h;
        } else {
            // 中心差分
            return (map(x + h) - map(x - h)) / (2 * h);
        }
    }

    /**
     * 获取映射函数在 x 位置的二阶导数
     */
    default double drive2(double x) {
        double h = 1e-6;
        if (x < h) {
            // 前向二阶差分
            double f0 = map(x);
            double f1 = map(x + h);
            double f2 = map(x + 2 * h);
            return (f2 - 2 * f1 + f0) / (h * h);
        } else if (x > 1.0 - h) {
            // 后向二阶差分
            double f0 = map(x - 2 * h);
            double f1 = map(x - h);
            double f2 = map(x);
            return (f2 - 2 * f1 + f0) / (h * h);
        } else {
            // 中心二阶差分
            double f0 = map(x - h);
            double f1 = map(x);
            double f2 = map(x + h);
            return (f2 - 2 * f1 + f0) / (h * h);
        }
    }
}