package top.kzre.krro.canvas.vector;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class DoubleArrayPool {
    private static final int MAX_PER_SIZE = 10;

    private static final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<double[]>> pools =
            new ConcurrentHashMap<>();

    private DoubleArrayPool() {}

    /**
     * 借出一个指定长度的 double 数组，默认返回的数组全部清零。
     */
    public static double[] borrow(int length) {
        return borrow(length, true);
    }

    /**
     * 借出一个指定长度的 double 数组。
     *
     * @param length 数组长度
     * @param zero   若为 true，保证返回的数组所有元素为 0.0
     * @return 长度恰好为 length 的数组（可能复用或新建）
     */
    public static double[] borrow(int length, boolean zero) {
        ConcurrentLinkedQueue<double[]> queue = pools.get(length);
        if (queue != null) {
            double[] arr = queue.poll();
            if (arr != null) {
                if (zero) {
                    Arrays.fill(arr, 0.0);
                }
                return arr;
            }
        }
        // 新建数组自动初始化为 0.0，所以不需要额外清零
        return new double[length];
    }

    /**
     * 归还数组，将其放入池中复用。
     */
    public static void returnArray(double[] arr) {
        if (arr == null) return;
        int length = arr.length;
        ConcurrentLinkedQueue<double[]> queue = pools.computeIfAbsent(
                length, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() < MAX_PER_SIZE) {
            queue.offer(arr);
        }
        // 超出容量则丢弃，GC 回收
    }

    /**
     * 清空所有池化数组。
     */
    public static void clear() {
        pools.clear();
    }
}