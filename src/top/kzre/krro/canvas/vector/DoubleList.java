package top.kzre.krro.canvas.vector;

public class DoubleList {
    private double[] data;
    private int size;

    public DoubleList(int initialCapacity) {
        data = new double[Math.max(initialCapacity, 16)];
        size = 0;
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

    public int size() { return size; }
    public double get(int index) { return data[index]; }
}