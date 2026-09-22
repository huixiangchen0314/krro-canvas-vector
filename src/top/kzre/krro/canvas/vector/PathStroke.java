package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.math.KMath;

import java.util.ArrayList;
import java.util.List;

/**
 * 描边渲染器，将路径（折线）转换为描边轮廓多边形。
 * <p>
 * 内侧拐角按偏移直线求交输出单点，外侧拐角按 join 策略填充缺口。
 * </p>
 */
public final class PathStroke extends PathRenderer {
    private final CapStrategy cap;
    private final JoinStrategy join;
    private final double miterLimit;

    /** 直线求交的临时结果，避免每次分配数组。 */
    private double tmpX, tmpY;

    public PathStroke(CapStrategy cap, JoinStrategy join, double miterLimit) {
        this.cap = cap;
        this.join = join;
        this.miterLimit = miterLimit;
    }

    @Override
    public Polygon render(Path path, RenderContext context) {
        List<Vertex> vertices = path.getVertices();
        int n = vertices.size();
        if (n < 2) return null;
        if (n == 2) {
            return genRectPolygon(vertices.get(0), vertices.get(1), context);
        }

        double scaleX = context.getScaleX();
        double scaleY = context.getScaleY();
        DoubleList poly = new DoubleList(n * 8 + 16);
        List<JoinContext> reverseJoins = new ArrayList<>(n);

        // ---- 第一个顶点 ----
        Vertex v0 = vertices.get(0);
        Vertex v1 = vertices.get(1);

        double dirX0 = v1.getX() - v0.getX();
        double dirY0 = v1.getY() - v0.getY();
        double len0 = Math.hypot(dirX0, dirY0);

        double prevDirX = dirX0 / len0;
        double prevDirY = dirY0 / len0;
        double prevNormalX = -prevDirY;
        double prevNormalY = prevDirX;

        double halfWidth0 = v0.getWidth() * 0.5;
        double leftX0  = v0.getX() + prevNormalX * halfWidth0 * scaleX;
        double leftY0  = v0.getY() + prevNormalY * halfWidth0 * scaleY;
        double rightX0 = v0.getX() - prevNormalX * halfWidth0 * scaleX;
        double rightY0 = v0.getY() - prevNormalY * halfWidth0 * scaleY;

        cap.addCap(new CapContext(
                v0,
                rightX0, rightY0,
                leftX0, leftY0,
                -dirX0, -dirY0,
                context
        ), poly);

        poly.add(leftX0, leftY0);

        // ---- 滑动窗口 ----
        for (int i = 1; i < n - 1; i++) {
            Vertex vCurr = vertices.get(i);
            Vertex vNext = vertices.get(i + 1);

            double currX = vCurr.getX();
            double currY = vCurr.getY();

            double dirX = vNext.getX() - currX;
            double dirY = vNext.getY() - currY;
            double len = Math.hypot(dirX, dirY);
            double currDirX = dirX / len;
            double currDirY = dirY / len;
            double currNormalX = -currDirY;
            double currNormalY = currDirX;

            double hw  = vCurr.getWidth() * 0.5;
            double hwX = hw * scaleX;
            double hwY = hw * scaleY;

            double prevEndLeftX   = currX + prevNormalX * hwX;
            double prevEndLeftY   = currY + prevNormalY * hwY;
            double prevEndRightX  = currX - prevNormalX * hwX;
            double prevEndRightY  = currY - prevNormalY * hwY;
            double currStartLeftX = currX + currNormalX * hwX;
            double currStartLeftY = currY + currNormalY * hwY;
            double currStartRightX = currX - currNormalX * hwX;
            double currStartRightY = currY - currNormalY * hwY;

            // 方向叉积：> 0 左转，左侧为内侧；< 0 右转，右侧为内侧
            double cross = prevDirX * currDirY - prevDirY * currDirX;
            boolean leftIsInner = cross > 0;

            // ---- 左侧 ----
            if (leftIsInner) {
                // 内侧：两条偏移直线求交
                if (intersectLines(
                        prevEndLeftX, prevEndLeftY, prevDirX, prevDirY,
                        currStartLeftX, currStartLeftY, currDirX, currDirY)) {
                    poly.add(tmpX, tmpY);
                } else {
                    // 退化（折返）：取中点
                    poly.add(
                            0.5 * (prevEndLeftX + currStartLeftX),
                            0.5 * (prevEndLeftY + currStartLeftY));
                }
            } else {
                // 外侧：走 join
                JoinContext leftJoin = new JoinContext(
                        vCurr,
                        prevEndLeftX, prevEndLeftY,
                        currStartLeftX, currStartLeftY,
                        miterLimit,
                        context);
                poly.add(prevEndLeftX, prevEndLeftY);
                join.addJoin(leftJoin, poly);
                poly.add(currStartLeftX, currStartLeftY);
            }

            // ---- 右侧 ----
            if (!leftIsInner) {
                // 内侧：求交点，存退化 JoinContext（prev == curr）

                if (intersectLines(
                        prevEndRightX, prevEndRightY, prevDirX, prevDirY,
                        currStartRightX, currStartRightY, currDirX, currDirY)) {
                    reverseJoins.add(new JoinContext(
                            vCurr, tmpX, tmpY, tmpX, tmpY, miterLimit, context));
                } else {
                    double mx = 0.5 * (prevEndRightX + currStartRightX);
                    double my = 0.5 * (prevEndRightY + currStartRightY);
                    reverseJoins.add(new JoinContext(
                            vCurr, mx, my, mx, my, miterLimit, context));
                }
            } else {
                // 外侧：走 join
                JoinContext rightJoin = new JoinContext(
                        vCurr,
                        currStartRightX, currStartRightY,
                        prevEndRightX, prevEndRightY,
                        miterLimit,
                        context);
                reverseJoins.add(rightJoin);
            }

            prevDirX = currDirX;
            prevDirY = currDirY;
            prevNormalX = currNormalX;
            prevNormalY = currNormalY;
        }

        // ---- 最后一个顶点 ----
        Vertex last = vertices.get(n - 1);
        Vertex secondLast = vertices.get(n - 2);

        double lastX = last.getX();
        double lastY = last.getY();
        double lastDirX = lastX - secondLast.getX();
        double lastDirY = lastY - secondLast.getY();

        double lastHalfWidth = last.getWidth() * 0.5;
        double lastLeftX  = lastX + prevNormalX * lastHalfWidth * scaleX;
        double lastLeftY  = lastY + prevNormalY * lastHalfWidth * scaleY;
        double lastRightX = lastX - prevNormalX * lastHalfWidth * scaleX;
        double lastRightY = lastY - prevNormalY * lastHalfWidth * scaleY;

        poly.add(lastLeftX, lastLeftY);
        cap.addCap(new CapContext(
                last,
                lastLeftX, lastLeftY,
                lastRightX, lastRightY,
                lastDirX, lastDirY,
                context
        ), poly);
        poly.add(lastRightX, lastRightY);

        // ---- 反向输出右侧边 ----
        for (int i = reverseJoins.size() - 1; i >= 0; i--) {
            JoinContext jc = reverseJoins.get(i);
            double px = jc.getPrevX(), py = jc.getPrevY();
            double cx = jc.getCurrX(), cy = jc.getCurrY();
            poly.add(px, py);
            if (px != cx || py != cy) {
                join.addJoin(jc, poly);
                poly.add(cx, cy);
            }
        }

        poly.add(rightX0, rightY0);

        double[] outline = poly.toArray();
        if (outline.length < 6) return new Polygon(new double[0]);
        return new Polygon(outline);
    }

    /**
     * 两条参数直线求交：
     *   L1: P1 + t * d1
     *   L2: P2 + s * d2
     * 结果写入 tmpX / tmpY。平行或近乎平行时返回 false。
     */
    private boolean intersectLines(
            double px1, double py1, double dx1, double dy1,
            double px2, double py2, double dx2, double dy2) {
        double denom = dx1 * dy2 - dy1 * dx2;
        if (Math.abs(denom) < 1e-12) return false;
        double t = ((px2 - px1) * dy2 - (py2 - py1) * dx2) / denom;
        tmpX = px1 + t * dx1;
        tmpY = py1 + t * dy1;
        return true;
    }

    private Polygon genRectPolygon(Vertex v0, Vertex v1, RenderContext context) {
        double scaleX = context.getScaleX();
        double scaleY = context.getScaleY();
        DoubleList poly = new DoubleList(16);
        double dirX = v1.getX() - v0.getX();
        double dirY = v1.getY() - v0.getY();

        double len = Math.hypot(dirX, dirY);
        if (len < 1e-12) {
            return null;
        }
        double normalX = -dirY / len;
        double normalY = dirX / len;
        double halfWidth0 = v0.getWidth() * 0.5;
        double halfWidth1 = v1.getWidth() * 0.5;

        double leftX0  = v0.getX() + normalX * halfWidth0 * scaleX;
        double leftY0  = v0.getY() + normalY * halfWidth0 * scaleY;
        double rightX0 = v0.getX() - normalX * halfWidth0 * scaleX;
        double rightY0 = v0.getY() - normalY * halfWidth0 * scaleY;
        double leftX1  = v1.getX() + normalX * halfWidth1 * scaleX;
        double leftY1  = v1.getY() + normalY * halfWidth1 * scaleY;
        double rightX1 = v1.getX() - normalX * halfWidth1 * scaleX;
        double rightY1 = v1.getY() - normalY * halfWidth1 * scaleY;

        cap.addCap(new CapContext(
                v0,
                rightX0, rightY0,
                leftX0, leftY0,
                -dirX, -dirY,
                context
        ), poly);

        poly.add(leftX0, leftY0);
        poly.add(leftX1, leftY1);

        cap.addCap(new CapContext(
                v1,
                leftX1, leftY1,
                rightX1, rightY1,
                dirX, dirY,
                context
        ), poly);

        poly.add(rightX1, rightY1);
        poly.add(rightX0, rightY0);

        double[] outline = poly.toArray();
        if (outline.length < 6) return null;
        return new Polygon(outline);
    }
}