package top.kzre.krro.canvas.vector;

public class RasterizerConfig {
    private double flatness = 0.25;
    private double miterLimit = 4.0;
    private int roundSteps = 16;
    private AntiAlias antiAlias = AntiAlias.SSAA_2x2;

    public RasterizerConfig() {}

    public double getFlatness() {
        return flatness;
    }

    public void setFlatness(double flatness) {
        this.flatness = flatness;
    }

    public double getMiterLimit() {
        return miterLimit;
    }

    public void setMiterLimit(double miterLimit) {
        this.miterLimit = miterLimit;
    }

    public int getRoundSteps() {
        return roundSteps;
    }

    public void setRoundSteps(int roundSteps) {
        this.roundSteps = roundSteps;
    }

    public AntiAlias getAntiAlias() {
        return antiAlias;
    }

    public void setAntiAlias(AntiAlias antiAlias) {
        this.antiAlias = antiAlias;
    }
}