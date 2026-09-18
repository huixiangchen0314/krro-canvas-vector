package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class AdaptiveFlattener implements CurveFlattener {


    private final WidthFunction widthFunc;
    private final double widthAccelTolerance; // 宽度变化容差（像素），用于自适应细分

    /**
     * @param widthFunc       宽度函数，接收曲线参数 t（0~1），返回宽度值（像素），不能为 null
     * @param widthAccelTolerance  宽度变化容差（像素），当线段两端宽度差小于此值时不再细分
     */
    public AdaptiveFlattener(WidthFunction widthFunc, double widthAccelTolerance) {
        if (widthFunc == null) {
            throw new IllegalArgumentException("widthFunc must not be null");
        }
        if (widthAccelTolerance <= 0) {
            throw new IllegalArgumentException("widthTolerance must be positive");
        }
        this.widthFunc = widthFunc;
        this.widthAccelTolerance = widthAccelTolerance;
    }

    @Override
    public Path flatten(Curve curve, RenderContext context) {
        double flatness   = context.getFlatness();
        double flatnessSq = flatness * flatness;

        // 曲线已预乘视口变换 → 坐标在视口空间 → flatnessSq 不换算
        // 宽度未预乘 → 世界空间 → widthAccelTolerance 需换算
        double scale = context.getEffectiveScale();
        double worldWidthAccelTol = widthAccelTolerance / scale;

        List<Segment> segments = curve.getSegments();
        if (segments == null || segments.isEmpty()) {
            return new Path(new ArrayList<>(), curve.isClosed());
        }

        // ---- 1. 展平所有段 ----
        List<LocalPoint> allPoints = new ArrayList<>();
        int segCount = segments.size();
        for (Segment seg : segments) {
            allPoints.addAll(flattenSegment(seg, flatnessSq));
        }

        Segment lastSeg = segments.get(segCount - 1);
        allPoints.add(new LocalPoint(lastSeg.getD().getX(), lastSeg.getD().getY(), 1.0));

        int n = allPoints.size();
        if (n < 2) {
            return new Path(new ArrayList<>(), curve.isClosed());
        }

        // ---- 2. 全局参数 t ----
        for (int i = 0; i < n; i++) {
            double t = curve.isClosed()
                    ? ((i == n - 1) ? 1.0 : (double) i / segCount)
                    : (double) i / (n - 1);
            allPoints.get(i).globalT = t;
        }

        // ---- 3. 自适应细分（宽度二阶导） ----
        List<Vertex> vertices = new ArrayList<>();
        LocalPoint first = allPoints.get(0);
        vertices.add(new Vertex(first.x, first.y, first.globalT, widthFunc.map(first.globalT)));

        for (int i = 0; i < n - 1; i++) {
            subdivideSegment(allPoints.get(i), allPoints.get(i + 1),
                    vertices, worldWidthAccelTol);
        }

        return new Path(vertices, curve.isClosed());
    }

    /**
     * 递归细分线段，直到宽度变化足够小或线段足够短。
     * 注意：此方法会添加 p1 作为终点（可能经过中间点），并假设 p0 已经在 vertices 中。
     */
    private void subdivideSegment(LocalPoint p0, LocalPoint p1,
                                  List<Vertex> vertices,
                                  double widthAccelTolerance) {

        double dx = p1.x - p0.x;
        double dy = p1.y - p0.y;
        double len = Math.hypot(dx, dy);
        double midT = (p0.globalT + p1.globalT) * 0.5;

        // 如果线段很短（<0.5像素）或者宽度变化小于容差，直接添加终点
        if (len < 0.5 || this.widthFunc.drive2(midT) < widthAccelTolerance) {
            double width = this.widthFunc.map(midT);
            vertices.add(new Vertex(p1.x, p1.y, p1.globalT, width));
            return;
        }

        // 否则在中点分割，分别处理左右子段

        double midX = (p0.x + p1.x) * 0.5;
        double midY = (p0.y + p1.y) * 0.5;
        LocalPoint mid = new LocalPoint(midX, midY, midT);

        // 递归左半段（添加 mid 作为终点）
        subdivideSegment(p0, mid,  vertices, widthAccelTolerance);
        // 递归右半段（添加 p1 作为终点）
        subdivideSegment(mid, p1, vertices, widthAccelTolerance);
    }

    // ---- 辅助方法：展平单个段（返回起点及内部点，不包含终点） ----
    private List<LocalPoint> flattenSegment(Segment seg, double flatnessSq) {
        List<LocalPoint> points = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(seg, 0.0, 1.0));
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            if (Segments.isFlat(f.seg, flatnessSq)) {
                points.add(new LocalPoint(f.seg.getA().getX(), f.seg.getA().getY(), f.startT));
            } else {
                Segment left = new Segment();
                Segment right = new Segment();
                Segments.split(f.seg, 0.5, left, right);
                double midT = (f.startT + f.endT) / 2.0;
                stack.push(new Frame(right, midT, f.endT));
                stack.push(new Frame(left, f.startT, midT));
            }
        }
        return points;
    }

    // ---- 内部辅助类 ----
    private static class Frame {
        final Segment seg;
        final double startT, endT;
        Frame(Segment seg, double startT, double endT) {
            this.seg = seg;
            this.startT = startT;
            this.endT = endT;
        }
    }

    private static class LocalPoint {
        final double x, y;
        double globalT; // 全局曲线参数 t
        LocalPoint(double x, double y, double globalT) {
            this.x = x;
            this.y = y;
            this.globalT = globalT;
        }
    }
}