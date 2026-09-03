package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;

import java.util.List;

public final class RenderableCurve {
    private final Curve curve;
    private final CurveFlattener flattener;
    private final List<CurveStyle> styles;

    public RenderableCurve(Curve curve, CurveFlattener flattener, List<CurveStyle> styles) {
        this.curve = curve;
        this.flattener = flattener;
        this.styles = styles;

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
}
