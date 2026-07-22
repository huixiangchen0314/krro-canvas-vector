package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class CurveUtils {

    public static double[] flattenCurveAdaptive(Curve curve, double flatness) {
        if (curve.getPoints().size() < 2) {
            return new double[0];
        }
        double flatnessSq = flatness * flatness;
        List<Segment> segments = curve.getSegments();
        if (segments.isEmpty()) {
            return new double[0];
        }
        DoubleArrayBuilder builder = new DoubleArrayBuilder(segments.size() * 16);
        for (Segment seg : segments) {
            flattenSegmentIterative(seg, flatnessSq, builder);
        }
        Segment last = segments.get(segments.size() - 1);
        builder.add(last.getD().getX(), last.getD().getY());
        return builder.toArray();
    }

    /** 只扁平化单个 Segment，用于分段渲染 */
    public static double[] flattenSegment(Segment seg, double flatness) {
        double flatnessSq = flatness * flatness;
        DoubleArrayBuilder builder = new DoubleArrayBuilder(32);
        flattenSegmentIterative(seg, flatnessSq, builder);
        // 需要添加段的终点
        builder.add(seg.getD().getX(), seg.getD().getY());
        return builder.toArray();
    }

    private static void flattenSegmentIterative(Segment seg, double flatnessSq,
                                                DoubleArrayBuilder builder) {
        Deque<Segment> stack = new ArrayDeque<>();
        stack.push(seg);
        while (!stack.isEmpty()) {
            Segment s = stack.pop();
            if (Segments.isFlat(s, flatnessSq)) {
                builder.add(s.getA().getX(), s.getA().getY());
            } else {
                Segment left = new Segment(null, null, null, null);
                Segment right = new Segment(null, null, null, null);
                Segments.split(s, 0.5, left, right);
                stack.push(right);
                stack.push(left);
            }
        }
    }
}