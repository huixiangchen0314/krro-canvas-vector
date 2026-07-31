package top.kzre.krro.canvas.vector;

import top.kzre.krro.util.tile.TiledCanvas;

public class CanvasAAFactory {
    public static AntiAliasPolicy create(AntiAlias mode) {
        CanvasScanlineFiller.AAMode aaMode;
        switch (mode) {
            case SSAA_2x2: aaMode = CanvasScanlineFiller.AAMode.SSAA2x2; break;
            case ANALYTIC: aaMode = CanvasScanlineFiller.AAMode.ANALYTIC; break;
            default: aaMode = CanvasScanlineFiller.AAMode.NONE;
        }
        CanvasScanlineFiller filler = new CanvasScanlineFiller(aaMode);
        return (dest, w, h, polygon, color, rule, dirtyTiles, tileSize) -> {
            if (dirtyTiles == null) {
                // 遍历所有可能瓦片
                int minTX = TiledCanvas.tileX(0, tileSize);
                int maxTX = TiledCanvas.tileX(w - 1, tileSize);
                int minTY = TiledCanvas.tileY(0, tileSize);
                int maxTY = TiledCanvas.tileY(h - 1, tileSize);
                for (int ty = minTY; ty <= maxTY; ty++) {
                    for (int tx = minTX; tx <= maxTX; tx++) {
                        filler.fillTile(dest, w, h, polygon, color, rule, tx, ty, tileSize);
                    }
                }
            } else {
                for (long key : dirtyTiles) {
                    int tx = TiledCanvas.unpackTx(key);
                    int ty = TiledCanvas.unpackTy(key);
                    filler.fillTile(dest, w, h, polygon, color, rule, tx, ty, tileSize);
                }
            }
        };
    }
}