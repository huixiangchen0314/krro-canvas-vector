package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;

import java.util.List;

public final class RenderableCurve {
    private final Curve curve;
    private final CurveFlattener flattener;
    private final List<CurveStyle> styles;
    private final double maxWidth;
    private final double maxHalfWidth;

    public RenderableCurve(Curve curve, CurveFlattener flattener, List<CurveStyle> styles, double maxWidth) {
        this.curve = curve;
        this.flattener = flattener;
        this.styles = styles;
        this.maxWidth = maxWidth;
        this.maxHalfWidth = maxWidth * 0.5;
    }

    public Curve getCurve() {
        return curve;
    }


    public List<CurveStyle> getStyles() {
        return styles;
    }

    public CurveFlattener getFlattener() {
        return flattener;
    }

    public double getMaxWidth() {
        return maxWidth;
    }


    public double getMaxHalfWidth() {
        return maxHalfWidth;
    }
}
