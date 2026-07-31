package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;
import java.util.function.DoubleUnaryOperator;

public class StrokeDesc implements CurveRenderDesc {
    public final Curve curve;
    public final boolean isVariableWidth;
    public final float fixedWidth;
    public final DoubleUnaryOperator widthFunc;
    public final float[] color;
    public final Cap cap;
    public final Join join;

    // 私有构造器
    private StrokeDesc(Curve curve, boolean isVariableWidth, float fixedWidth,
                       DoubleUnaryOperator widthFunc, float[] color, Cap cap, Join join) {
        this.curve = curve;
        this.isVariableWidth = isVariableWidth;
        this.fixedWidth = fixedWidth;
        this.widthFunc = widthFunc;
        this.color = color;
        this.cap = cap;
        this.join = join;
    }

    /** 固定宽度描边工厂 */
    public static StrokeDesc ofFixed(Curve curve, float width, float[] color, Cap cap, Join join) {
        return new StrokeDesc(curve, false, width, null, color, cap, join);
    }

    /** 可变宽度描边工厂 */
    public static StrokeDesc ofVariable(Curve curve, DoubleUnaryOperator widthFunc,
                                        float[] color, Cap cap, Join join) {
        return new StrokeDesc(curve, true, 0, widthFunc, color, cap, join);
    }
}