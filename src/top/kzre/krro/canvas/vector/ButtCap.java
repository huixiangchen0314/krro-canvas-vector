package top.kzre.krro.canvas.vector;

/**
 * 平头端点（Butt Cap）策略。
 * <p>
 * 不添加任何额外顶点，路径边缘直接以当前点作为端点结束。
 * 这是最常用、性能最高的端点样式。
 * </p>
 *
 * @see CapStrategy
 * @see CapContext
 */
public final class ButtCap implements CapStrategy {

    public static final ButtCap INSTANCE = new ButtCap();

    private ButtCap() {
        // 私有构造，确保单例
    }

    @Override
    public void addCap(CapContext context, DoubleList builder) {
        // 平头端点无需添加任何顶点
        // 路径直接以左右边缘点结束，不进行扩展或圆角处理
    }
}