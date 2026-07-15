package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class CurveUtils {

    /**
     * 自适应细分，输出逼近曲线的多边形顶点序列。
     * @param curve    贝塞尔曲线
     * @param flatness 允许最大直线偏差（像素单位，推荐 0.25）
     * @return 扁平化坐标数组 [x0, y0, x1, y1, ...]
     */
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
        // 最后一个段的终点
        Segment last = segments.get(segments.size() - 1);
        builder.add(last.getD().getX());
        builder.add(last.getD().getY());

        return builder.toArray();
    }

    /**
     * 迭代细分单个曲线段，直到所有子段都足够平坦。
     * 平坦度判断委托给上游 Segments.isFlat。
     */
    private static void flattenSegmentIterative(Segment seg, double flatnessSq,
                                                DoubleArrayBuilder builder) {
        Deque<Segment> stack = new ArrayDeque<>();
        stack.push(seg);

        while (!stack.isEmpty()) {
            Segment s = stack.pop();
            if (Segments.isFlat(s, flatnessSq)) {
                builder.add(s.getA().getX());
                builder.add(s.getA().getY());
            } else {
                Segment left = new Segment(null, null, null, null);
                Segment right = new Segment(null, null, null, null);
                Segments.split(s, 0.5, left, right);
                stack.push(right);
                stack.push(left);
            }
        }
    }

    // ---------- 内部可扩容数组构建器，完全无装箱 ----------
    private static final class DoubleArrayBuilder {
        private double[] data;
        private int size;

        DoubleArrayBuilder(int initialCapacity) {
            data = new double[initialCapacity];
        }

        void add(double value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = value;
        }

        double[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}