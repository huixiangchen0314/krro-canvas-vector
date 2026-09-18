package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayList;
import java.util.List;

public final class CurveRenderer {
    private CurveRenderer() {}

    public static void render(List<RenderableCurve> curves, RenderContext context) {
        long t0 = System.nanoTime();

        int viewWidth = context.getViewWidth();
        int viewHeight = context.getViewHeight();
        double effectiveScale = context.getEffectiveScale();
        // 各阶段累计耗时（纳秒）
        long tClip = 0, tFlatten = 0, tSimplify = 0, tRender = 0, tFill = 0;
        int curveCount = 0;
        int visibleCount = 0;
        int pathCount = 0;
        int polyCount = 0;

        List<RenderablePolygon> polys = new ArrayList<>();

        // ── 阶段 1：裁剪 → 展平 → 简化 → 生成多边形 ──
        for (RenderableCurve curve : curves) {
            curveCount++;
            Curve c = curve.getCurve();
            double maxHalfWidth = curve.getMaxHalfWidth() * effectiveScale;
            CurveFlattener flattener = curve.getFlattener();
            List<CurveStyle> styles = curve.getStyles();

            long s1 = System.nanoTime();
            List<Curve> visibles = new ArrayList<>();
            CurveClipper.clipAABB(c, maxHalfWidth,0, 0, viewWidth, viewHeight, visibles);
            tClip += System.nanoTime() - s1;

            if (visibles.isEmpty()) continue;
            visibleCount++;

            long s2 = System.nanoTime();
            List<Path> paths = new ArrayList<>();
            for (Curve visible : visibles) {
                Path path = flattener.flatten(visible, context);
                paths.add(path);
            }
            tFlatten += System.nanoTime() - s2;

            pathCount += paths.size();

            for (CurveStyle style : styles) {
                PathRenderer renderer = style.getRenderer();
                PolygonFiller filler = style.getFiller();
                for (Path path : paths) {
                    long s3 = System.nanoTime();
                    Path simplified = path.simplify(0.5f);
                    tSimplify += System.nanoTime() - s3;

                    long s4 = System.nanoTime();
                    Polygon polygon = renderer.render(simplified, context);
                    tRender += System.nanoTime() - s4;

                    if (polygon != null && polygon.isValid()) {
                        polys.add(new RenderablePolygon(polygon, filler));
                        polyCount++;
                    }
                }
            }
        }

        // ── 阶段 2：并行填充脏块 ──
        long s5 = System.nanoTime();
        context.getDirtyTiles()
                .stream().parallel()
                .forEach(tile -> {
                    for (RenderablePolygon poly : polys) {
                        Polygon polygon = poly.getPolygon();
                        PolygonFiller filler = poly.getFiller();
                        filler.fill(polygon, tile, context);
                    }
                });
        tFill += System.nanoTime() - s5;

        long total = System.nanoTime() - t0;

        // ── 统计输出 ──
        double totalMs = total / 1_000_000.0;
        System.out.printf(
                "[render] total=%.2fms | clip=%.2fms(%.1f%%) flatten=%.2fms(%.1f%%) " +
                        "simplify=%.2fms(%.1f%%) render=%.2fms(%.1f%%) fill=%.2fms(%.1f%%)%n",
                totalMs,
                tClip / 1e6,        pct(tClip, total),
                tFlatten / 1e6,     pct(tFlatten, total),
                tSimplify / 1e6,    pct(tSimplify, total),
                tRender / 1e6,      pct(tRender, total),
                tFill / 1e6,        pct(tFill, total)
        );
        System.out.printf(
                "[render] curves=%d visible=%d paths=%d polys=%d dirtyTiles=%d%n",
                curveCount, visibleCount, pathCount, polyCount,
                context.getDirtyTiles().size()
        );
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : (part * 100.0 / total);
    }


}
