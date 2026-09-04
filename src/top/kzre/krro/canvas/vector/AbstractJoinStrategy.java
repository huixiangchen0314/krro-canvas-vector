package top.kzre.krro.canvas.vector;

public abstract class AbstractJoinStrategy implements JoinStrategy{

    @Override
    public boolean shouldJoin(JoinContext context){
        boolean tangentOutSide = context.isTangentOutSide();
        if(!tangentOutSide) {
            return false;
        }

        double prevX = context.getPrevX();
        double prevY = context.getPrevY();
        double currX = context.getCurrX();
        double currY = context.getCurrY();
        double cx = context.getCenterX();
        double cy = context.getCenterY();
        double r = context.getHalfWidth();
        double normalX = context.getOutsideNormalX();
        double normalY = context.getOutsideNormalY();

        // 计算两条边缘的方向向量
        double prevDX = prevX - cx;
        double prevDY = prevY - cy;
        double currDX = currX - cx;
        double currDY = currY - cy;

        double crossCenter = prevDX * currDY - prevDY * currDX;

        // crossCenter 负说明是钝角
        return !(crossCenter < -1e-9);
    }
}
