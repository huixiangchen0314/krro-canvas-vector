package top.kzre.krro.canvas.vector;

import java.util.Arrays;

/**
 * 可扩容的原始 double 数组构建器，无装箱开销。
 */
public final class DoubleArrayBuilder {
    private double[] data;
    private int size;

    public DoubleArrayBuilder(int initialCapacity) {
        if (initialCapacity < 2) initialCapacity = 2;
        this.data = new double[initialCapacity];
    }

    /**
     * 添加一对坐标 (x, y)。
     */
    public void add(double x, double y) {
        ensureCapacity(2);
        data[size++] = x;
        data[size++] = y;
    }

    /**
     * 添加一个点的坐标数组 [x, y]。
     */
    public void add(double[] point) {
        if (point.length < 2) return;
        add(point[0], point[1]);
    }

    /**
     * 返回当前已存储的元素数（即 x, y 对数的两倍）。
     */
    public int size() {
        return size;
    }

    /**
     * 提取已存储数据的一个副本。
     */
    public double[] toArray() {
        return Arrays.copyOf(data, size);
    }

    private void ensureCapacity(int extra) {
        if (size + extra > data.length) {
            int newLen = data.length * 2;
            if (newLen < size + extra) newLen = size + extra;
            data = Arrays.copyOf(data, newLen);
        }
    }
}