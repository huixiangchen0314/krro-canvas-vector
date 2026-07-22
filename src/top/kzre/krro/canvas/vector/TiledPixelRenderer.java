package top.kzre.krro.canvas.vector;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.HashSet;
import java.util.Set;

/**
 * 将连续浮点数组（源）经仿射变换混合到目标浮点数组（目标）中。
 * 源图像尺寸假设与目标相同（即 dstW x dstH），但变换允许任意旋转/缩放/平移，
 * 超出源图像边界的像素视为透明。
 */
public final class TiledPixelRenderer {

    private TiledPixelRenderer() {}

    /**
     * 将源连续数组按仿射变换混合到目标数组。
     *
     * @param dst         目标 RGBA 数组 (dstW * dstH * 4)
     * @param dstW        目标宽度
     * @param dstH        目标高度
     * @param src         源 RGBA 数组 (dstW * dstH * 4) — 尺寸必须与目标相同
     * @param srcTileSize 虚拟瓦片尺寸，仅用于划分脏区域（与源数组尺寸无关）
     * @param matrix2d      仿射变换矩阵 [a, b, c, d, tx, ty]（列向量约定）
     * @param blendMode   混合模式（来自 Blends 常量）
     * @param opacity     透明度 [0, 1]
     * @param dirtyTiles  需要更新的目标瓦片键集（基于 srcTileSize 划分），
     *                    若为 null 则更新所有目标瓦片；若为空集合则忽略。
     */
    public static void blendTransformedTiled(float[] dst, int dstW, int dstH,
                                             float[] src, int srcTileSize,
                                             float[] matrix2d, String blendMode, float opacity,
                                             Set<Long> dirtyTiles) {
        if (dst == null || src == null)
            throw new IllegalArgumentException("dst and src cannot be null");
        if (dstW <= 0 || dstH <= 0)
            throw new IllegalArgumentException("dimensions must be positive");
        if (src.length < dstW * dstH * 4)
            throw new IllegalArgumentException("src array too small");
        if (opacity < 0 || opacity > 1)
            throw new IllegalArgumentException("opacity out of range");
        if (matrix2d == null || matrix2d.length < 6)
            throw new IllegalArgumentException("matrix2d must be a 6-element float array");
        if (srcTileSize <= 0) return;

        // 空脏集合直接返回
        if (dirtyTiles != null && dirtyTiles.isEmpty()) return;

        // 计算逆矩阵（目标 → 源）
        float[] invMatrix = invertMatrix(matrix2d);
        if (invMatrix == null) return; // 奇异矩阵

        // 如果 dirtyTiles 为 null，生成所有目标瓦片
        Set<Long> targetTiles = dirtyTiles;
        if (targetTiles == null) {
            int tilesX = (dstW + srcTileSize - 1) / srcTileSize;
            int tilesY = (dstH + srcTileSize - 1) / srcTileSize;
            targetTiles = new HashSet<>();
            for (int ty = 0; ty < tilesY; ty++) {
                for (int tx = 0; tx < tilesX; tx++) {
                    targetTiles.add(TiledCanvas.pack(tx, ty));
                }
            }
        }

        float[] bg = new float[4];
        float[] fg = new float[4];

        // 遍历目标脏瓦片
        for (long key : targetTiles) {
            int tx = TiledCanvas.unpackTx(key);
            int ty = TiledCanvas.unpackTy(key);

            int startX = Math.max(0, tx * srcTileSize);
            int startY = Math.max(0, ty * srcTileSize);
            int endX = Math.min(startX + srcTileSize, dstW);
            int endY = Math.min(startY + srcTileSize, dstH);
            if (startX >= endX || startY >= endY) continue;

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    // 逆映射到源坐标
                    float sx = invMatrix[0] * x + invMatrix[2] * y + invMatrix[4];
                    float sy = invMatrix[1] * x + invMatrix[3] * y + invMatrix[5];

                    // 采样源像素（双线性插值）
                    sampleFromArray(src, dstW, dstH, sx, sy, fg);
                    if (fg[3] == 0f) continue;

                    fg[3] *= opacity;

                    int idx = (y * dstW + x) * 4;
                    bg[0] = dst[idx];
                    bg[1] = dst[idx + 1];
                    bg[2] = dst[idx + 2];
                    bg[3] = dst[idx + 3];

                    float[] blended = Blends.blendWithAlpha(blendMode, bg, fg);
                    dst[idx]     = blended[0];
                    dst[idx + 1] = blended[1];
                    dst[idx + 2] = blended[2];
                    dst[idx + 3] = blended[3];
                }
            }
        }
    }

    // ---------- 辅助方法 ----------

    /** 计算 2D 仿射矩阵的逆矩阵，若奇异返回 null */
    private static float[] invertMatrix(float[] m) {
        float a = m[0], b = m[1], c = m[2], d = m[3], tx = m[4], ty = m[5];
        float det = a * d - b * c;
        if (Math.abs(det) < 1e-12f) return null;
        float invDet = 1.0f / det;
        return new float[]{
                d * invDet,
                -b * invDet,
                -c * invDet,
                a * invDet,
                (c * ty - d * tx) * invDet,
                (b * tx - a * ty) * invDet
        };
    }

    /** 从连续数组中采样一个像素（双线性插值），超出边界则透明 */
    private static void sampleFromArray(float[] src, int srcW, int srcH,
                                        float sx, float sy, float[] out) {
        // 检查是否超出边界（留出 1 像素边距用于插值）
        if (sx < 0 || sx >= srcW - 1 || sy < 0 || sy >= srcH - 1) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }

        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        float fx = sx - x0;
        float fy = sy - y0;
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        int idx00 = (y0 * srcW + x0) * 4;
        int idx10 = (y0 * srcW + x1) * 4;
        int idx01 = (y1 * srcW + x0) * 4;
        int idx11 = (y1 * srcW + x1) * 4;

        // 分别对每个通道进行双线性插值
        for (int i = 0; i < 4; i++) {
            float top = src[idx00 + i] + (src[idx10 + i] - src[idx00 + i]) * fx;
            float bottom = src[idx01 + i] + (src[idx11 + i] - src[idx01 + i]) * fx;
            out[i] = top + (bottom - top) * fy;
        }
    }
}