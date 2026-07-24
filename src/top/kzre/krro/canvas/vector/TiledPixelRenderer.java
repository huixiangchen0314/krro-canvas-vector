package top.kzre.krro.canvas.vector;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.Canvas;
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
     * 将源浮点数组通过仿射变换混合到目标 Canvas 上（瓦片粒度）。
     * 当矩阵为单位矩阵时自动走快速路径，不进行逆映射。
     *
     * @param dest      目标画布
     * @param w         画布宽度（像素）
     * @param h         画布高度（像素）
     * @param src       源像素数组 (w * h * 4)
     * @param tileSize  瓦片尺寸（仅用于划分脏区域，通常取画布的 tileSize）
     * @param matrix2d  2D 仿射矩阵 [a,b,c,d,平移x,平移y]
     * @param blendMode 混合模式
     * @param opacity   不透明度
     * @param dirtyTiles 脏瓦片集合，null 表示全图，空集合直接返回
     */
    public static void blendTransformedTiled(Canvas dest, int w, int h,
                                             float[] src, int tileSize,
                                             float[] matrix2d, String blendMode, float opacity,
                                             Set<Long> dirtyTiles) {
        if (dest == null || src == null) throw new IllegalArgumentException();
        if (opacity < 0 || opacity > 1) return;
        if (tileSize <= 0) return;
        if (dirtyTiles != null && dirtyTiles.isEmpty()) return;

        int channels = dest.getChannels();   // 固定为 4

        // 判断是否为单位矩阵
        float m00 = matrix2d[0], m01 = matrix2d[1], m10 = matrix2d[2], m11 = matrix2d[3],
                mTx = matrix2d[4], mTy = matrix2d[5];
        boolean identity = Math.abs(m00 - 1f) < 1e-5f && Math.abs(m01) < 1e-5f &&
                Math.abs(m10) < 1e-5f && Math.abs(m11 - 1f) < 1e-5f &&
                Math.abs(mTx) < 1e-5f && Math.abs(mTy) < 1e-5f;

        // 收集目标瓦片
        Set<Long> tiles = dirtyTiles;
        if (tiles == null) {
            tiles = new HashSet<>();
            int tilesX = (w + tileSize - 1) / tileSize;
            int tilesY = (h + tileSize - 1) / tileSize;
            for (int tileY = 0; tileY < tilesY; tileY++) {
                for (int tileX = 0; tileX < tilesX; tileX++) {
                    tiles.add(TiledCanvas.pack(tileX, tileY));
                }
            }
        }

        // 池化瓦片缓冲区
        int tileBufSize = tileSize * tileSize * channels;
        FloatsPool tilePool = FloatsPools.getPool(tileBufSize);

        // 复用背景和前景像素数组
        float[] bgPixel  = new float[channels];
        float[] srcPixel = new float[channels];

        if (identity) {
            // 单位矩阵快速路径：直接逐像素混合，无需逆变换
            float[] dstTile = tilePool.acquire();
            try {
                for (long key : tiles) {
                    int tileX = TiledCanvas.unpackTx(key);
                    int tileY = TiledCanvas.unpackTy(key);
                    int x0 = tileX * tileSize, y0 = tileY * tileSize;
                    int x1 = Math.min(x0 + tileSize, w);
                    int y1 = Math.min(y0 + tileSize, h);
                    int bw = x1 - x0, bh = y1 - y0;
                    if (bw <= 0 || bh <= 0) continue;

                    dest.readBytes(dstTile, 0, x0, y0, bw, bh, bw);

                    for (int y = 0; y < bh; y++) {
                        int srcY = y0 + y;
                        for (int x = 0; x < bw; x++) {
                            int srcX = x0 + x;
                            int srcIdx = (srcY * w + srcX) * channels;
                            int dstIdx = (y * bw + x) * channels;

                            bgPixel[0] = dstTile[dstIdx]; bgPixel[1] = dstTile[dstIdx+1];
                            bgPixel[2] = dstTile[dstIdx+2]; bgPixel[3] = dstTile[dstIdx+3];

                            srcPixel[0] = src[srcIdx]; srcPixel[1] = src[srcIdx+1];
                            srcPixel[2] = src[srcIdx+2]; srcPixel[3] = src[srcIdx+3] * opacity;
                            if (srcPixel[3] == 0f) continue;

                            float[] blended = Blends.blendWithAlpha(blendMode, bgPixel, srcPixel);
                            dstTile[dstIdx]   = blended[0];
                            dstTile[dstIdx+1] = blended[1];
                            dstTile[dstIdx+2] = blended[2];
                            dstTile[dstIdx+3] = blended[3];
                        }
                    }
                    dest.writeBytes(dstTile, 0, x0, y0, bw, bh, bw);
                }
            } finally {
                tilePool.release(dstTile);
            }
        } else {
            // 一般变换路径：需要逆矩阵和双线性采样
            float[] inv = invertMatrix(matrix2d);
            if (inv == null) return;

            float[] dstTile = tilePool.acquire();
            try {
                for (long key : tiles) {
                    int tileX = TiledCanvas.unpackTx(key);
                    int tileY = TiledCanvas.unpackTy(key);
                    int x0 = tileX * tileSize, y0 = tileY * tileSize;
                    int x1 = Math.min(x0 + tileSize, w);
                    int y1 = Math.min(y0 + tileSize, h);
                    int bw = x1 - x0, bh = y1 - y0;
                    if (bw <= 0 || bh <= 0) continue;

                    dest.readBytes(dstTile, 0, x0, y0, bw, bh, bw);

                    for (int y = 0; y < bh; y++) {
                        int worldY = y0 + y;
                        for (int x = 0; x < bw; x++) {
                            int worldX = x0 + x;
                            float sx = inv[0] * worldX + inv[2] * worldY + inv[4];
                            float sy = inv[1] * worldX + inv[3] * worldY + inv[5];
                            sampleFromArray(src, w, h, sx, sy, srcPixel);
                            if (srcPixel[3] == 0f) continue;
                            srcPixel[3] *= opacity;

                            int dstIdx = (y * bw + x) * channels;
                            bgPixel[0] = dstTile[dstIdx]; bgPixel[1] = dstTile[dstIdx+1];
                            bgPixel[2] = dstTile[dstIdx+2]; bgPixel[3] = dstTile[dstIdx+3];

                            float[] blended = Blends.blendWithAlpha(blendMode, bgPixel, srcPixel);
                            dstTile[dstIdx]   = blended[0];
                            dstTile[dstIdx+1] = blended[1];
                            dstTile[dstIdx+2] = blended[2];
                            dstTile[dstIdx+3] = blended[3];
                        }
                    }
                    dest.writeBytes(dstTile, 0, x0, y0, bw, bh, bw);
                }
            } finally {
                tilePool.release(dstTile);
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