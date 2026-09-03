package top.kzre.krro.canvas.vector;

public final class DoubleList {
    private double[] data;
    private int size;

    public DoubleList(int initialCapacity) {
        data = new double[Math.max(initialCapacity, 16)];
        size = 0;
    }

    //零拷贝
    public DoubleList(double[] data) {
        this.data = data;
        size = data.length;
    }

    public void add(double value) {
        if (size >= data.length) {
            grow();
        }
        data[size++] = value;
    }

    public void add(double x, double y) { add(x); add(y); }

    private void grow() {
        int newLen = Math.max(data.length * 2, 16);
        double[] newData = new double[newLen];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }

    public double[] toArray() {
        double[] result = new double[size];
        System.arraycopy(data, 0, result, 0, size);
        return result;
    }
    // 新增：移除最后一个元素
    public void removeLast() {
        if (size > 0) {
            size--;
        }
    }

    // 新增：设置指定索引的值
    public void set(int index, double value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        data[index] = value;
    }

    public int size() { return size; }
    public double get(int index) { return data[index]; }
}