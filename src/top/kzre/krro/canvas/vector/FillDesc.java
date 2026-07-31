// FillDescriptor.java
package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;

public class FillDesc implements CurveRenderDesc {
    public final Curve curve;
    public final float[] color;
    public final FillRule rule;

    public FillDesc(Curve curve, float[] color, FillRule rule) {
        this.curve = curve;
        this.color = color;
        this.rule = rule;
    }
}