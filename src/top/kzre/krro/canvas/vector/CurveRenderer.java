package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayList;
import java.util.List;

public final class CurveRenderer {
    private CurveRenderer() {}

    public static void render(List<RenderableCurve> curves, RenderContext context){

        int canvasW = context.getWidth();
        int canvasH = context.getHeight();
        double scaleX = context.getScaleX();
        double scaleY = context.getScaleY();
        List<RenderablePolygon> polys = new ArrayList<>();
        for (RenderableCurve curve : curves) {
            Curve c = curve.getCurve();
            CurveFlattener flattener = curve.getFlattener();
            List<CurveStyle> styles = curve.getStyles();
            List<Curve> visibles = new ArrayList<>();
            clipCurve(visibles, c, canvasW, canvasH);

            List<Path> paths = new ArrayList<>();
            for (Curve visible : visibles) {
                Path path = flattener.flatten(visible);
                paths.add(path);
            }
            for (CurveStyle style : styles) {
                PathRenderer renderer = style.getRenderer();
                PolygonFiller filler = style.getFiller();
                for (Path path : paths) {
                    Path simplified = path.simplify(0.5f * scaleX * scaleY);
                    Polygon polygon = renderer.render(simplified, context);
                    if (polygon != null && polygon.isValid())
                    {
                        polys.add(new RenderablePolygon(polygon, filler));
                    }
                }
            }
        }

        context.getDirtyTiles()
                .stream().parallel()
                .forEach(tile->{
                    for (RenderablePolygon poly : polys) {
                        Polygon polygon = poly.getPolygon();
                        PolygonFiller filler = poly.getFiller();
                        filler.fill(polygon,tile, context);
                    }
                });
    }


    private static void clipCurve(List<Curve> out, Curve curve, int width, int height) {
        if (curve == null || curve.getPoints().isEmpty()) return;

        // 闭合曲线直接添加，无需裁剪
        if (curve.isClosed()) {
            out.add(curve);
            return;
        }

        int segCount = curve.getSegmentCount();
        List<ControlPoint> currentPoints = new ArrayList<>();

        for (int i = 0; i < segCount; i++) {
            ControlPoint cpStart = curve.getPoints().get(i);
            ControlPoint cpEnd   = curve.getPoints().get((i + 1) % curve.getPoints().size());

            boolean visible;
            if (Segments.isStraightLine(cpStart, cpEnd)) {
                // 快速路径：退化（直线）段，仅用端点判断包围盒
                double minX = Math.min(cpStart.getX(), cpEnd.getX());
                double maxX = Math.max(cpStart.getX(), cpEnd.getX());
                double minY = Math.min(cpStart.getY(), cpEnd.getY());
                double maxY = Math.max(cpStart.getY(), cpEnd.getY());
                visible = !(maxX < 0 || minX > width || maxY < 0 || minY > height);
            } else {
                // 曲线段：计算精确的贝塞尔包围盒
                Segment seg = curve.getSegment(i);
                AABB aabb = Segments.aabb(seg);
                visible = !(aabb.getMaxX() < 0 || aabb.getMinX() > width ||
                        aabb.getMaxY() < 0 || aabb.getMinY() > height);
            }

            if (visible) {
                if (currentPoints.isEmpty()) {
                    currentPoints.add(cpStart);
                }
                currentPoints.add(cpEnd);
            } else {
                if (!currentPoints.isEmpty()) {
                    out.add(new Curve(currentPoints, false));
                    currentPoints = new ArrayList<>();
                }
            }
        }
        if (!currentPoints.isEmpty()) {
            out.add(new Curve(currentPoints, false));
        }
    }



}
