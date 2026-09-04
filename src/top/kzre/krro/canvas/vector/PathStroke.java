package top.kzre.krro.canvas.vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 描边渲染器，将路径（折线）转换为描边轮廓多边形。
 * <p>
 * 采用滑动窗口算法，一次遍历计算出所有顶点的左、右边缘，并分别生成正向（左侧）和反向（右侧）的 Join，
 * 最后组合成完整的轮廓多边形。该实现避免了大量数组分配，仅使用一个 {@link List} 存储反向 JoinContext。
 * </p>
 *
 * <h3>算法流程</h3>
 * <ol>
 *   <li>若路径仅有两个顶点，直接生成矩形多边形。</li>
 *   <li>预先计算第一个顶点的法线、左右边缘和半宽。</li>
 *   <li>添加起点 Cap。</li>
 *   <li>滑动窗口迭代：对于每个中间顶点（索引 1 到 n-2），使用其前后顶点计算法线、左右边缘。</li>
 *   <li>正向 Join（左侧）立即添加到轮廓多边形。</li>
 *   <li>反向 Join（右侧）存储到列表中，顺序为 (当前右边缘, 前一个右边缘)。</li>
 *   <li>处理最后一个顶点，添加终点 Cap 和最后一个反向 Join。</li>
 *   <li>反向遍历存储的 Join 列表，依次添加到轮廓多边形，完成右侧边。</li>
 *   <li>闭合轮廓。</li>
 * </ol>
 *
 * @see PathRenderer
 * @see CapStrategy
 * @see JoinStrategy
 * @see PolygonFiller
 */
public final class PathStroke extends PathRenderer {
    private final CapStrategy cap;
    private final JoinStrategy join;
    private final double miterLimit;

    /**
     * 构造描边渲染器。
     *
     * @param cap        端点样式策略
     * @param join       连接样式策略
     * @param miterLimit 斜接限制（相对于半宽的倍数），仅对 MITER 连接生效
     */
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

        // 处理两个顶点的情况：直接生成矩形
        if (n == 2) {
            Vertex v0 = vertices.get(0);
            Vertex v1 = vertices.get(1);
            return genRectPolygon(v0, v1, context);
        }

        double scaleX = context.getScaleX();
        double scaleY = context.getScaleY();
        DoubleList poly = new DoubleList(n * 8 + 16);
        List<JoinContext> reverseJoins = new ArrayList<>(n);

        // ---- 第一个顶点 ----
        Vertex v0 = vertices.get(0);
        Vertex v1 = vertices.get(1);

        // 线段方向
        double dirX0 = v1.getX() - v0.getX();
        double dirY0 = v1.getY() - v0.getY();
        double len0 = Math.hypot(dirX0, dirY0);

        // 线段法向
        double normalX0 = -dirY0 / len0;
        double normalY0 = dirX0 / len0;
        double halfWidth0 = v0.getWidth() * 0.5;
        // 沿着法向扩张 线段两点
        double leftX0 = v0.getX() + normalX0 * halfWidth0 * scaleX;
        double leftY0 = v0.getY() + normalY0 * halfWidth0 * scaleY;
        double rightX0 = v0.getX() - normalX0 * halfWidth0 * scaleX;
        double rightY0 = v0.getY() - normalY0 * halfWidth0 * scaleY;

        // 起点 Cap
        cap.addCap(new CapContext(
                v0,
                rightX0, rightY0,
                leftX0, leftY0,
                -dirX0, -dirY0,
                context
        ), poly);

        // 点1
        poly.add(leftX0, leftY0);


        double prevNormalX = normalX0;
        double prevNormalY = normalY0;

        // ---- 滑动窗口：(prev, curr, next) ----
        // 每个循环计算一条两条线段并补充.
        // 其中 prev 结束点 和 next 的起始点 一共 4个扩张点有上次循环计算
        // 滑动窗口复用了 (prev, curr) 这条线段法向.本次需要计算 (curr, next) 的法向
        for (int i = 1; i < n - 1; i++) {
            Vertex vCurr = vertices.get(i);
            Vertex vNext = vertices.get(i + 1);

            // 指向外侧的法向
            // (curr, next) 线段法向的计算
            double dirX = vNext.getX() - vCurr.getX();
            double dirY = vNext.getY() - vCurr.getY();
            double len = Math.hypot(dirX, dirY);
            double normalX = -dirY / len;
            double normalY = dirX / len;



            double currX = vCurr.getX();
            double currY = vCurr.getY();
            double currHalfWidth = vCurr.getWidth() * 0.5;
            // 计算 vPrev-end 的扩张点
            double prevEndLeftX = currX + prevNormalX * currHalfWidth * scaleX;
            double prevEndLeftY = currY + prevNormalY * currHalfWidth * scaleY;
            double prevEndRightX = currX - prevNormalX * currHalfWidth * scaleX;
            double prevEndRightY = currY - prevNormalY * currHalfWidth * scaleY;
            // 计算 vCurr-start 的扩张点
            double currStartLeftX = currX + normalX * currHalfWidth * scaleX;
            double currStartLeftY = currY + normalY * currHalfWidth * scaleY;
            double currStartRightX = currX - normalX * currHalfWidth * scaleX;
            double currStartRightY = currY - normalY * currHalfWidth * scaleY;


            // 连接前一个左边缘 (prevLeft) 和当前左边缘 (lx, ly)，中心为当前顶点
            JoinContext leftJoin = new JoinContext(
                    vCurr,
                    prevEndLeftX, prevEndLeftY,
                    currStartLeftX, currStartLeftY,
                    miterLimit,
                    context
            );

            poly.add(prevEndLeftX, prevEndLeftY);
            join.addJoin(leftJoin, poly);
            poly.add(currStartLeftX, currStartLeftY);

            // ---- 反向 Join（右侧） ----

            // 存储顺序：当前右边缘 -> 前一个右边缘，便于反向遍历
            JoinContext rightJoin = new JoinContext(
                    vCurr,
                    currStartRightX, currStartRightY,
                    prevEndRightX, prevEndRightY,
                    miterLimit,
                    context);
            reverseJoins.add(rightJoin);

            // 更新前一个线段的法向
            prevNormalX = normalX;
            prevNormalY = normalY;
        }

        // ---- 最后一个顶点 ----

        Vertex last = vertices.get(n - 1);
        Vertex secondLast = vertices.get(n - 2);

        double lastX = last.getX();
        double lastY = last.getY();
        double dirX = lastX - secondLast.getX();
        double dirY = lastY - secondLast.getY();
        double len = Math.hypot(dirX, dirY);
        double normalX = -dirY / len;
        double normalY = dirX / len;
        double secondLastX = secondLast.getX();
        double secondLastY = secondLast.getY();
        double secondLastHalfWidth = secondLast.getWidth() * 0.5;

        // 计算 vPrev-end 的扩张点
        double prevEndLeftX = secondLastX + prevNormalX * secondLastHalfWidth * scaleX;
        double prevEndLeftY = secondLastY + prevNormalY * secondLastHalfWidth * scaleY;
        double prevEndRightX = secondLastX - prevNormalX * secondLastHalfWidth * scaleX;
        double prevEndRightY = secondLastY - prevNormalY * secondLastHalfWidth * scaleY;
        // 计算 vCurr-start 的扩张点
        double currStartLeftX = secondLastX + normalX + secondLastHalfWidth * scaleX;
        double currStartLeftY = secondLastY + normalY + secondLastHalfWidth * scaleY;
        double currStartRightX = secondLastX - normalX * secondLastHalfWidth * scaleX;
        double currStartRightY = secondLastY - normalY * secondLastHalfWidth * scaleY;

        poly.add(prevEndLeftX, prevEndLeftY);
        join.addJoin(new JoinContext(
                secondLast,
                prevEndLeftX, prevEndLeftY,
                currStartLeftX, currStartLeftY,
                miterLimit,
                context
        ), poly);
        poly.add(currStartLeftX, currStartLeftY);

        JoinContext lastRightJoin = new JoinContext(
                secondLast,
                currStartRightX, currStartRightY,
                prevEndRightX, prevEndRightY,
                miterLimit,
                context
        );
        reverseJoins.add(lastRightJoin);

        double lastHalfWidth = last.getWidth() * 0.5;
        double lastLeftX = lastX + normalX * lastHalfWidth * scaleX;
        double lastLeftY = lastY + normalY * lastHalfWidth * scaleY;
        double lastRightX = lastX - normalX * lastHalfWidth * scaleX;
        double lastRightY = lastY - normalY * lastHalfWidth * scaleY;



        // ---- 终点 Cap ----
        poly.add(lastLeftX, lastLeftY);
        cap.addCap(new CapContext(
                last,
                lastLeftX, lastLeftY,
                lastRightX, lastRightY,
                dirX, dirY,
                context
        ), poly);
        poly.add(lastRightX, lastRightY);


        // ---- 反向输出右侧边 ----
        for (int i = reverseJoins.size() - 1; i >= 0; i--) {
            JoinContext jc = reverseJoins.get(i);
            // jc 的顺序是 (currRight, prevRight)，当前轮廓的最后一个点是 currRight，
            // 直接调用 join.addJoin 并添加 prevRight 即可完成连接
            poly.add(jc.getPrevX(), jc.getPrevY());
            join.addJoin(jc, poly);
            poly.add(jc.getCurrX(), jc.getCurrY());
        }

        poly.add(rightX0, rightY0);

        double[] outline = poly.toArray();
        if (outline.length < 6) return new Polygon(new double[0]);
        return new Polygon(outline);

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

        double leftX0 = v0.getX() + normalX * halfWidth0 * scaleX;
        double leftY0 = v0.getY() + normalY * halfWidth0 * scaleY;
        double rightX0 = v0.getX() - normalX * halfWidth0 * scaleX;
        double rightY0 = v0.getY() - normalY * halfWidth0 * scaleY;
        double leftX1 = v1.getX() + normalX * halfWidth1 * scaleX;
        double leftY1 = v1.getY() + normalY * halfWidth1 * scaleY;
        double rightX1 = v1.getX() - normalX * halfWidth1 * scaleX;
        double rightY1 = v1.getY() - normalY * halfWidth1 * scaleY;

        cap.addCap(new CapContext(
                v0,
                rightX0, leftY0,
                leftX0, leftY0,
                - dirX, - dirY,
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