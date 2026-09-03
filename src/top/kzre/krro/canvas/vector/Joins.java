package top.kzre.krro.canvas.vector;

/**
 * 内置描边连接（Join）策略的工厂类。
 * <p>
 * 提供创建标准 Join 策略的静态方法，并缓存常用实例以提高性能。
 * </p>
 */
public final class Joins {

    private static final JoinStrategy MITER_INSTANCE = MiterJoin.INSTANCE;
    private static final JoinStrategy BEVEL_INSTANCE = BevelJoin.INSTANCE;
    private static final JoinStrategy ROUND_INSTANCE = new RoundJoin(); // 默认步数

    private Joins() {
        // 私有构造，防止实例化
    }

    /**
     * 返回斜接连接策略（单例）。
     *
     * @return 斜接连接策略
     */
    public static JoinStrategy miter() {
        return MITER_INSTANCE;
    }

    /**
     * 返回斜角连接策略（单例）。
     *
     * @return 斜角连接策略
     */
    public static JoinStrategy bevel() {
        return BEVEL_INSTANCE;
    }

    /**
     * 返回圆形连接策略（默认步数 16）。
     *
     * @return 圆形连接策略
     */
    public static JoinStrategy round() {
        return ROUND_INSTANCE;
    }

    /**
     * 返回具有指定步数的圆形连接策略。
     *
     * @param steps 圆弧步数（必须 > 0）
     * @return 圆形连接策略实例
     * @throws IllegalArgumentException 如果 steps <= 0
     */
    public static JoinStrategy round(int steps) {
        if (steps == 16) {
            return ROUND_INSTANCE; // 复用默认实例
        }
        return new RoundJoin(steps);
    }
}