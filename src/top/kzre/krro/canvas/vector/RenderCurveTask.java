package top.kzre.krro.canvas.vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次性的曲线渲染任务，实现了 {@link Runnable} 接口。
 * <p>
 * 该任务封装了待渲染的曲线列表和渲染上下文，执行时会调用
 * {@link CurveRenderer#render(List, RenderContext)} 进行并行渲染。
 * 任务只能执行一次，重复调用 {@link #run()} 不会有任何效果。
 * </p>
 */
public final class RenderCurveTask implements Runnable {

    private final List<RenderableCurve> curves;
    private final RenderContext renderContext;
    private final AtomicBoolean done = new AtomicBoolean(false);

    public RenderCurveTask(List<RenderableCurve> curves, RenderContext renderContext) {
        if (curves == null ) {
            throw new IllegalArgumentException("curves must not be null");
        }
        if (renderContext == null) {
            throw new IllegalArgumentException("renderContext must not be null");
        }
        this.curves = curves;
        this.renderContext = renderContext;
    }

    @Override
    public void run() {
        // 原子地检查并设置状态，确保只执行一次
        if (!done.compareAndSet(false, true)) {
            return; // 已执行过，直接返回
        }
        if (curves.isEmpty()) {
            return;
        }
        CurveRenderer.render(curves, renderContext);
    }

    /**
     * 检查任务是否已完成（执行过）。
     * @return true 如果任务已经执行过，否则 false
     */
    public boolean isDone() {
        return done.get();
    }
}