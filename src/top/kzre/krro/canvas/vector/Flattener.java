package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.Curve;
import top.kzre.curve.bezier2d.Segment;
import top.kzre.curve.bezier2d.Segments;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Flattener {
    private final double flatnessSq;

    public Flattener(double flatness) {
        this.flatnessSq = flatness * flatness;
    }

    public double[] flatten(Curve curve) {
        List<Segment> segments = curve.getSegments();
        if (segments == null || segments.isEmpty()) return new double[0];
        DoubleList list = new DoubleList(segments.size() * 16);
        for (Segment seg : segments) {
            flattenSegment(seg, list);
        }
        Segment last = segments.get(segments.size() - 1);
        list.add(last.getD().getX(), last.getD().getY());
        return list.toArray();
    }

    private void flattenSegment(Segment seg, DoubleList list) {
        Deque<Segment> stack = new ArrayDeque<>();
        stack.push(seg);
        while (!stack.isEmpty()) {
            Segment s = stack.pop();
            if (Segments.isFlat(s, flatnessSq)) {
                list.add(s.getA().getX(), s.getA().getY());
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