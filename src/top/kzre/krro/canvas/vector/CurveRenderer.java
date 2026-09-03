package top.kzre.krro.canvas.vector;

/**
 * 曲线渲染器
 */

public abstract class CurveRenderer {
    public abstract void render(Path path, RenderContext context);

    public static CurveRenderer stroke(CapStrategy cap, JoinStrategy join,
                                       PolygonFiller filler,
                                       double miterLimit) {
        return new CurveStroke(cap, join, filler,miterLimit);
    }

    public static CurveRenderer fill( PolygonFiller filler){
        return new CurveFill(filler);
    }

    public  CurveRenderer and(CurveRenderer renderer){
        final CurveRenderer outer = this;
        return new CurveRenderer(){
            @Override
            public void render(Path path, RenderContext context) {
                outer.render(path,context);
                renderer.render(path,context);
            }
        };
    }
}
