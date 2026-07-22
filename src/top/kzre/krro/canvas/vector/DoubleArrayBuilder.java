package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.pool.DoublesPools;

public final class DoubleArrayBuilder {
    private double[] data;
    private int size;

    public DoubleArrayBuilder(int initialCapacity) {
        // 初始数组使用池化，方便回收
        data = DoublesPools.getPool(initialCapacity).acquire();
        size = 0;
    }

    public void add(double x, double y) {
        ensureCapacity(size + 2);
        data[size++] = x;
        data[size++] = y;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(data.length * 2, minCapacity);
            double[] newData = DoublesPools.getPool(newCapacity).acquire();
            System.arraycopy(data, 0, newData, 0, size);
            // 归还旧数组
            DoublesPools.getPool(data.length).release(data);
            data = newData;
        }
    }

    public double[] toArray() {
        // 返回精确大小的池化数组
        double[] result = DoublesPools.getPool(size).acquire();
        System.arraycopy(data, 0, result, 0, size);
        return result;
    }

    // 释放内部数组（通常在不再需要构建器时调用）
    public void dispose() {
        if (data != null) {
            DoublesPools.getPool(data.length).release(data);
            data = null;
        }
    }
}