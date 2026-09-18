package top.kzre.krro.canvas.vector;

import top.kzre.curve.bezier2d.*;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CurveClipper {
    private CurveClipper() {}

    /** 递归细分深度上限——防止极端曲线的无限细分 */
    private static final int MAX_SUBDIVIDE_DEPTH = 10;

    /**
     * 计算曲线实际经过的瓦片集合（含描边宽度扩展）。
     *
     * @param halfWidth 视口空间半宽（曲线已预乘视口变换，宽度需调用方乘 scale 换算）
     */
    public static Set<Long> curveTiles(Curve curve, int tileSize, double halfWidth) {
        Set<Long> tiles = new HashSet<>();
        if (curve == null || curve.getPoints().isEmpty()) return tiles;

        int segCount = curve.getSegmentCount();
        for (int i = 0; i < segCount; i++) {
            Segment seg = curve.getSegment(i);
            collectSegmentTiles(seg, tileSize, halfWidth, tiles, 0);
        }
        return tiles;
    }

    /**
     * 计算单个线段经过的瓦片集合（含描边宽度扩展）。
     */
    public static Set<Long> segTiles(Segment segment, int tileSize, double halfWidth) {
        Set<Long> tiles = new HashSet<>();
        if (segment == null) return tiles;
        collectSegmentTiles(segment, tileSize, halfWidth, tiles, 0);
        return tiles;
    }

    /**
     * 递归细分线段，直到其 AABB（按半宽扩展后）落在单个瓦片内或达到深度上限。
     */
    private static void collectSegmentTiles(Segment seg, int tileSize, double halfWidth,
                                            Set<Long> tiles, int depth) {
        AABB aabb = Segments.aabb(seg);

        // 按半宽扩展 AABB——包含描边覆盖范围
        double minX = aabb.getMinX() - halfWidth;
        double maxX = aabb.getMaxX() + halfWidth;
        double minY = aabb.getMinY() - halfWidth;
        double maxY = aabb.getMaxY() + halfWidth;

        int minTx = Math.floorDiv((int) Math.floor(minX), tileSize);
        int maxTx = Math.floorDiv((int) Math.floor(maxX), tileSize);
        int minTy = Math.floorDiv((int) Math.floor(minY), tileSize);
        int maxTy = Math.floorDiv((int) Math.floor(maxY), tileSize);

        // 终止：扩展后 AABB 落在单瓦片内，或达到深度上限
        if ((minTx == maxTx && minTy == maxTy) || depth >= MAX_SUBDIVIDE_DEPTH) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                for (int ty = minTy; ty <= maxTy; ty++) {
                    tiles.add(TiledCanvas.pack(tx, ty));
                }
            }
            return;
        }

        // 细分
        Segment left  = new Segment();
        Segment right = new Segment();
        Segments.split(seg, 0.5, left, right);
        collectSegmentTiles(left,  tileSize, halfWidth, tiles, depth + 1);
        collectSegmentTiles(right, tileSize, halfWidth, tiles, depth + 1);
    }

    /**
     * 按 AABB 裁剪曲线——把可见段收集到 out。
     * 半宽用于可见性判断，避免「中心线在视口外但描边可见」被误剔除。
     */
    public static void clipAABB(Curve curve, double halfWidth,
                                int minimumX, int minimumY,
                                int maximumX, int maximumY,
                                List<Curve> out) {
        if (curve == null || curve.getPoints().isEmpty()) return;

        if (curve.isClosed()) {
            out.add(curve);
            return;
        }

        int segCount = curve.getSegmentCount();
        List<ControlPoint> currentPoints = new ArrayList<>();

        for (int i = 0; i < segCount; i++) {
            ControlPoint cpStart = curve.getPoints().get(i);
            ControlPoint cpEnd   = curve.getPoints().get((i + 1) % curve.getPoints().size());

            boolean visible;
            if (Segments.isStraightLine(cpStart, cpEnd)) {
                double minX = Math.min(cpStart.getX(), cpEnd.getX()) - halfWidth;
                double maxX = Math.max(cpStart.getX(), cpEnd.getX()) + halfWidth;
                double minY = Math.min(cpStart.getY(), cpEnd.getY()) - halfWidth;
                double maxY = Math.max(cpStart.getY(), cpEnd.getY()) + halfWidth;
                visible = !(maxX < minimumX || minX > maximumX
                        || maxY < minimumY || minY > maximumY);
            } else {
                Segment seg = curve.getSegment(i);
                AABB aabb = Segments.aabb(seg);
                visible = !(aabb.getMaxX() + halfWidth < minimumX
                        || aabb.getMinX() - halfWidth > maximumX
                        || aabb.getMaxY() + halfWidth < minimumY
                        || aabb.getMinY() - halfWidth > maximumY);
            }

            if (visible) {
                if (currentPoints.isEmpty()) {
                    currentPoints.add(cpStart);
                }
                currentPoints.add(cpEnd);
            } else {
                if (!currentPoints.isEmpty()) {
                    out.add(new Curve(currentPoints, false));
                    currentPoints = new ArrayList<>();
                }
            }
        }
        if (!currentPoints.isEmpty()) {
            out.add(new Curve(currentPoints, false));
        }
    }
}