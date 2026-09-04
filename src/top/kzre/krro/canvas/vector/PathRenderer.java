package top.kzre.krro.canvas.vector;


public abstract class PathRenderer {
    public abstract Polygon render(Path path, RenderContext context);

    public static PathRenderer stroke(CapStrategy cap, JoinStrategy join,
                                      double miterLimit) {
        return new PathStroke(cap, join, miterLimit);
    }

    public static PathRenderer fill(){
        return new PathFill();
    }

}
