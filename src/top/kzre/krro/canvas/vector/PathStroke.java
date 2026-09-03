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
        if (vertices.size() < 2) return null;

        return generateOutline(vertices);

    }

    /**
     * 根据顶点列表生成描边轮廓多边形。
     * <p>
     * 使用滑动窗口算法，一次遍历同时生成正向（左侧）和反向（右侧）的轮廓边。
     * 正向边直接添加到多边形，反向边以 {@link JoinContext} 形式存储，最后反向输出。
     * </p>
     *
     * @param vertices 路径顶点列表（包含坐标和宽度信息）
     * @return 闭合的描边轮廓多边形
     */
    private Polygon generateOutline(List<Vertex> vertices) {
        int n = vertices.size();
        if (n < 2) return null;

        // 处理两个顶点的情况：直接生成矩形
        if (n == 2) {
            Vertex v0 = vertices.get(0);
            Vertex v1 = vertices.get(1);
            double dx = v1.getX() - v0.getX();
            double dy = v1.getY() - v0.getY();
            double len = Math.hypot(dx, dy);
            if (len < 1e-12) return new Polygon(new double[0]);
            double nx = -dy / len;
            double ny = dx / len;
            double hw0 = v0.getWidth() * 0.5;
            double hw1 = v1.getWidth() * 0.5;
            double x1 = v0.getX() + nx * hw0, y1 = v0.getY() + ny * hw0;
            double x2 = v1.getX() + nx * hw1, y2 = v1.getY() + ny * hw1;
            double x3 = v1.getX() - nx * hw1, y3 = v1.getY() - ny * hw1;
            double x4 = v0.getX() - nx * hw0, y4 = v0.getY() - ny * hw0;
            return new Polygon(new double[]{x1, y1, x2, y2, x3, y3, x4, y4});
        }

        DoubleList poly = new DoubleList(n * 8 + 16);
        List<JoinContext> reverseJoins = new ArrayList<>(n);

        // ---- 第一个顶点 ----
        Vertex v0 = vertices.get(0);
        Vertex v1 = vertices.get(1);
        // 使用第一个顶点的法线（从 v0 到 v1 方向）
        double dx0 = v1.getX() - v0.getX();
        double dy0 = v1.getY() - v0.getY();
        double len0 = Math.hypot(dx0, dy0);
        // 如果路径长度为零，使用默认法线 (0, 1)
        double nx0 = (len0 < 1e-12) ? 0 : -dy0 / len0;
        double ny0 = (len0 < 1e-12) ? 1 : dx0 / len0;
        double hw0 = v0.getWidth() * 0.5;
        double left0X = v0.getX() + nx0 * hw0;
        double left0Y = v0.getY() + ny0 * hw0;
        double right0X = v0.getX() - nx0 * hw0;
        double right0Y = v0.getY() - ny0 * hw0;

        // 起点 Cap
        cap.addCap(new CapContext(v0.getX(), v0.getY(), dx0/len0, dy0/len0, hw0,
                right0X, right0Y, left0X, left0Y, true), poly);

        poly.add(left0X, left0Y);

        // 初始化前一个顶点的左、右边缘（顶点0）
        double prevLeftX = left0X, prevLeftY = left0Y;
        double prevRightX = right0X, prevRightY = right0Y;

        // ---- 滑动窗口循环：i 从 2 到 n-1 (i 是 next 的索引) ----
        for (int i = 2; i < n; i++) {
            Vertex vPrev = vertices.get(i - 2);
            Vertex vCurr = vertices.get(i - 1);
            Vertex vNext = vertices.get(i);

            // 计算 current 顶点的法线（使用 prev 和 next 的方向差）
            double dx = vNext.getX() - vPrev.getX();
            double dy = vNext.getY() - vPrev.getY();
            double len = Math.hypot(dx, dy);
            double nx = (len < 1e-12) ? 0 : -dy / len;
            double ny = (len < 1e-12) ? 1 : dx / len;
            double hw = vCurr.getWidth() * 0.5;
            double lx = vCurr.getX() + nx * hw;
            double ly = vCurr.getY() + ny * hw;
            double rx = vCurr.getX() - nx * hw;
            double ry = vCurr.getY() - ny * hw;

            // ---- 正向 Join（左侧） ----
            // 连接前一个左边缘 (prevLeft) 和当前左边缘 (lx, ly)，中心为当前顶点
            JoinContext leftJoin = new JoinContext(prevLeftX, prevLeftY, lx, ly,
                    vCurr.getX(), vCurr.getY(), hw, miterLimit);
            join.addJoin(leftJoin, poly);
            poly.add(lx, ly);

            // ---- 反向 Join（右侧） ----
            // 存储顺序：当前右边缘 -> 前一个右边缘，便于反向遍历
            JoinContext rightJoin = new JoinContext(rx, ry, prevRightX, prevRightY,
                    vCurr.getX(), vCurr.getY(), hw, miterLimit);
            reverseJoins.add(rightJoin);

            // 更新前一个顶点的数据为当前顶点
            prevLeftX = lx;
            prevLeftY = ly;
            prevRightX = rx;
            prevRightY = ry;
        }

        // ---- 最后一个顶点 ----
        Vertex last = vertices.get(n - 1);
        Vertex prevLast = vertices.get(n - 2);
        // 使用最后一段的方向作为切线（反向）
        double lxLast = last.getX() - prevLast.getX();
        double lyLast = last.getY() - prevLast.getY();
        double lenLast = Math.hypot(lxLast, lyLast);
        if (lenLast < 1e-12) { lxLast = 0; lyLast = 1; } // fallback
        double nxLast = -lyLast / lenLast;
        double nyLast = lxLast / lenLast;
        double hwLast = last.getWidth() * 0.5;
        double leftLastX = prevLeftX, leftLastY = prevLeftY;
        double rightLastX = last.getX() - nxLast * hwLast;
        double rightLastY = last.getY() - nyLast * hwLast;

        // 最后一个反向 Join
        JoinContext lastRightJoin = new JoinContext(rightLastX, rightLastY,
                prevRightX, prevRightY,
                last.getX(), last.getY(), hwLast, miterLimit);
        reverseJoins.add(lastRightJoin);

        // 终点 Cap
        cap.addCap(new CapContext(
                last.getX(), last.getY(),
                -lxLast, -lyLast,
                hwLast,
                rightLastX, rightLastY,
                leftLastX, leftLastY, false), poly);

        // ---- 反向输出右侧边 ----
        poly.add(rightLastX, rightLastY);
        for (int i = reverseJoins.size() - 1; i >= 0; i--) {
            JoinContext jc = reverseJoins.get(i);
            // jc 的顺序是 (currRight, prevRight)，当前轮廓的最后一个点是 currRight，
            // 直接调用 join.addJoin 并添加 prevRight 即可完成连接
            join.addJoin(jc, poly);
            poly.add(jc.getPrevX(), jc.getPrevY());
        }

        // ---- 闭合轮廓 ----
        poly.add(right0X, right0Y);

        double[] outline = poly.toArray();
        if (outline.length < 6) return new Polygon(new double[0]);
        return new Polygon(outline);
    }
}