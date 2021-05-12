package com.crschnick.pdx_unlimiter.app.installation.dist;

import com.crschnick.pdx_unlimiter.app.installation.Game;

import java.nio.file.Path;

public final class CatchAllDist extends GameDist {

    public CatchAllDist(Game game, Path installLocation) {
        super(game, "Catch All", installLocation);
    }

    @Override
    public boolean supportsLauncher() {
        return false;
    }

    @Override
    public boolean supportsDirectLaunch() {
        return false;
    }
}
