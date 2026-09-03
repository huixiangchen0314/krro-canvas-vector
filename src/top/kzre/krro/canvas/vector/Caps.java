package top.kzre.krro.canvas.vector;

/**
 * 内置端点（Cap）策略工厂。
 * <p>
 * 提供对标准端点样式的统一访问，包括：
 * <ul>
 *   <li>{@link #butt()} – 平头端点（无扩展）</li>
 *   <li>{@link #square()} – 方形端点（延伸半个线宽）</li>
 *   <li>{@link #round()} – 圆形端点（半圆弧，默认 16 步）</li>
 *   <li>{@link #round(int)} – 圆形端点（可指定圆弧步数）</li>
 * </ul>
 * </p>
 *
 * <p>所有工厂方法均返回单例实例（除按步数创建的 round 外），
 * 确保无状态策略的零开销复用。</p>
 *
 * @see CapStrategy
 * @see ButtCap
 * @see SquareCap
 * @see RoundCap
 */
public final class Caps {

    // 内置单例
    private static final ButtCap BUTT_INSTANCE = ButtCap.INSTANCE;
    private static final SquareCap SQUARE_INSTANCE = SquareCap.INSTANCE;
    private static final RoundCap ROUND_DEFAULT = new RoundCap(16);

    private Caps() {
        // 私有构造，禁止实例化
    }

    /**
     * 返回平头端点（Butt Cap）策略。
     * <p>
     * 该策略不添加任何额外顶点，路径以当前端点直接结束。
     * 常用于描边端点的默认样式。
     * </p>
     *
     * @return 平头端点策略（单例）
     */
    public static CapStrategy butt() {
        return BUTT_INSTANCE;
    }

    /**
     * 返回方形端点（Square Cap）策略。
     * <p>
     * 该策略在路径端点处添加一个矩形端头，向外延伸半个线宽。
     * 端头边与路径切线垂直，形成方头效果。
     * </p>
     *
     * @return 方形端点策略（单例）
     */
    public static CapStrategy square() {
        return SQUARE_INSTANCE;
    }

    /**
     * 返回圆形端点（Round Cap）策略，使用默认 16 步圆弧插值。
     * <p>
     * 该策略在路径端点处生成半圆弧，使端点呈圆形。
     * 默认步数（16）在大多数情况下可提供平滑的视觉效果。
     * </p>
     *
     * @return 圆形端点策略（共享实例，步数为 16）
     */
    public static CapStrategy round() {
        return ROUND_DEFAULT;
    }

    /**
     * 返回具有指定精度的圆形端点（Round Cap）策略。
     * <p>
     * 步数越多，圆弧越平滑，但顶点数也相应增加。
     * 建议步数范围 8 ~ 32，可根据画布尺寸和性能要求调整。
     * </p>
     *
     * @param steps 圆弧插值步数，必须大于 0
     * @return 新的圆形端点策略实例
     * @throws IllegalArgumentException 如果 steps <= 0
     */
    public static CapStrategy round(int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }
        // 如果步数与默认一致，直接返回共享实例
        if (steps == 16) {
            return ROUND_DEFAULT;
        }
        return new RoundCap(steps);
    }
}