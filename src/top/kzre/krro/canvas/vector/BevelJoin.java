package top.kzre.krro.canvas.vector;

/**
 * 斜角连接（Bevel Join）策略。
 * <p>
 * 两条边缘直接以斜角（倒角）方式连接，不添加额外顶点。
 * 这是最简单的连接方式，性能最高，但视觉效果较硬。
 * </p>
 *
 * @see JoinStrategy
 * @see JoinContext
 */
public final class BevelJoin extends AbstractJoinStrategy {

    public static final BevelJoin INSTANCE = new BevelJoin();

    private BevelJoin() {
        // 私有构造，确保单例
    }

    @Override
    public void addJoin(JoinContext context, DoubleList builder) {
        // 斜角连接无需添加额外顶点
        // 两条边缘直接相交，不进行圆角或斜接处理
    }
}