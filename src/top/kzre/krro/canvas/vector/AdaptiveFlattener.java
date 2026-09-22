package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 自适应曲线展平器。
 *
 * <p>几何展平在标准参数 t 空间进行（保证平坦度），
 * 宽度细分在归一化弧长 s 空间进行（保证宽度保真）。
 * {@link WidthFunction#map(double)} 接收 s，{@link WidthFunction#knots()}
 * 也返回 s 上的突变点。</p>
 */
public final class AdaptiveFlattener implements CurveFlattener {

    private final WidthFunction widthFunc;
    private final double widthAccelTolerance;
    private final double[] knots;

    public AdaptiveFlattener(WidthFunction widthFunc, double widthAccelTolerance) {
        if (widthFunc == null) {
            throw new IllegalArgumentException("widthFunc must not be null");
        }
        if (widthAccelTolerance <= 0) {
            throw new IllegalArgumentException("widthAccelTolerance must be positive");
        }
        this.widthFunc = widthFunc;
        this.widthAccelTolerance = widthAccelTolerance;
        this.knots = widthFunc.knots();
    }

    @Override
    public Path flatten(Curve curve, RenderContext context) {
        double flatness = context.getFlatness();
        double flatnessSq = flatness * flatness;

        double scale = context.getEffectiveScale();
        double worldWidthAccelTol = widthAccelTolerance / scale;

        List<Segment> segments = curve.getSegments();
        if (segments == null || segments.isEmpty()) {
            return new Path(new ArrayList<>(), curve.isClosed());
        }

        // ---- 1. 几何展平，累计每个点的全局 t ----
        List<LocalPoint> allPoints = new ArrayList<>();
        int segCount = segments.size();
        double segSpan = 1.0 / segCount;
        double accT = 0.0;
        for (Segment seg : segments) {
            for (LocalPoint p : flattenSegment(seg, flatnessSq)) {
                p.t = accT + p.t * segSpan;   // 段内 t → 全局 t
                allPoints.add(p);
            }
            accT += segSpan;
        }
        Segment lastSeg = segments.get(segCount - 1);
        allPoints.add(new LocalPoint(
                lastSeg.getD().getX(), lastSeg.getD().getY(), 1.0));

        int n = allPoints.size();
        if (n < 2) {
            return new Path(new ArrayList<>(), curve.isClosed());
        }

        // ---- 2. 累积弧长 → 归一化 s ----
        double[] cumArc = new double[n];
        cumArc[0] = 0.0;
        for (int i = 1; i < n; i++) {
            LocalPoint a = allPoints.get(i - 1);
            LocalPoint b = allPoints.get(i);
            cumArc[i] = cumArc[i - 1] + Math.hypot(b.x - a.x, b.y - a.y);
        }
        double totalArc = cumArc[n - 1];
        if (totalArc < 1e-12) {
            // 退化：整条曲线长度为零，退回到 t 作为参数
            for (LocalPoint p : allPoints) p.s = p.t;
        } else {
            double inv = 1.0 / totalArc;
            for (int i = 0; i < n; i++) allPoints.get(i).s = cumArc[i] * inv;
        }

        // ---- 3. 宽度按 s 自适应细分 ----
        List<Vertex> vertices = new ArrayList<>();
        LocalPoint first = allPoints.get(0);
        vertices.add(new Vertex(first.x, first.y, first.t,
                widthFunc.map(first.s)));

        for (int i = 0; i < n - 1; i++) {
            subdivideSegment(allPoints.get(i), allPoints.get(i + 1),
                    vertices, worldWidthAccelTol);
        }

        return new Path(vertices, curve.isClosed());
    }

    /**
     * 在两个展平点之间按弧长 s 自适应细分。
     * 假设 p0 已在 vertices 中，p1 作为终点加入。
     */
    private void subdivideSegment(LocalPoint p0, LocalPoint p1,
                                  List<Vertex> vertices,
                                  double widthAccelTolerance) {

        // ---- 优先：s 区间内有 knot 就切在 knot 上 ----
        int knotIdx = findKnotInRange(p0.s, p1.s);
        if (knotIdx >= 0) {
            double knotS = knots[knotIdx];
            double span = p1.s - p0.s;
            double ratio = span > 1e-12 ? (knotS - p0.s) / span : 0.5;
            if (ratio < 0) ratio = 0;
            else if (ratio > 1) ratio = 1;

            double kx = p0.x + ratio * (p1.x - p0.x);
            double ky = p0.y + ratio * (p1.y - p0.y);
            double kt = p0.t + ratio * (p1.t - p0.t);
            LocalPoint kp = new LocalPoint(kx, ky, kt, knotS);

            subdivideSegment(p0, kp, vertices, widthAccelTolerance);
            subdivideSegment(kp, p1, vertices, widthAccelTolerance);
            return;
        }

        double dx = p1.x - p0.x;
        double dy = p1.y - p0.y;
        double len = Math.hypot(dx, dy);
        double midS = (p0.s + p1.s) * 0.5;

        // 线够短，或宽度在 s 上足够平坦 → 停
        if (len < 0.5 || widthFunc.drive2(midS) < widthAccelTolerance) {
            vertices.add(new Vertex(p1.x, p1.y, p1.t, widthFunc.map(p1.s)));
            return;
        }

        double span = p1.s - p0.s;
        double ratio = span > 1e-12 ? (midS - p0.s) / span : 0.5;
        double midX = p0.x + ratio * (p1.x - p0.x);
        double midY = p0.y + ratio * (p1.y - p0.y);
        double midT = p0.t + ratio * (p1.t - p0.t);
        LocalPoint mid = new LocalPoint(midX, midY, midT, midS);

        subdivideSegment(p0, mid, vertices, widthAccelTolerance);
        subdivideSegment(mid, p1, vertices, widthAccelTolerance);
    }

    private int findKnotInRange(double s0, double s1) {
        if (knots.length == 0) return -1;
        int lo = 0, hi = knots.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (knots[mid] <= s0) lo = mid + 1;
            else hi = mid;
        }
        return (lo < knots.length && knots[lo] < s1) ? lo : -1;
    }

    /** 展平单个段，返回段内局部 t 的点（不含终点）。 */
    private List<LocalPoint> flattenSegment(Segment seg, double flatnessSq) {
        List<LocalPoint> points = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(seg, 0.0, 1.0));
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            if (Segments.isFlat(f.seg, flatnessSq)) {
                points.add(new LocalPoint(
                        f.seg.getA().getX(), f.seg.getA().getY(), f.startT));
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
        double t;   // 全局标准参数
        double s;   // 归一化弧长
        LocalPoint(double x, double y, double t) {
            this.x = x; this.y = y; this.t = t;
        }
        LocalPoint(double x, double y, double t, double s) {
            this(x, y, t);
            this.s = s;
        }
    }
}