package top.kzre.krro.canvas.vector;

/**
 * 可扩容的原始 double 数组构建器，无装箱开销。
 */
public final class DoubleArrayBuilder {
    private double[] data;
    private int size;

    DoubleArrayBuilder(int initialCapacity) {
        data = new double[initialCapacity];
        size = 0;
    }

    void add(double x, double y) {
        ensureCapacity(size + 2);
        data[size++] = x;
        data[size++] = y;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(data.length * 2, minCapacity);
            double[] newData = new double[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }

    double[] toArray() {
        double[] result = new double[size];
        System.arraycopy(data, 0, result, 0, size);
        return result;
    }
}