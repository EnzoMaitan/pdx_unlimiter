package com.crschnick.pdxu.app.installation.dist;

import com.crschnick.pdxu.app.installation.Game;
import com.crschnick.pdxu.app.util.OsHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.lang3.SystemUtils;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public class GameDists {

    private static final List<BiFunction<Game, Path, Optional<GameDist>>> FAST_COMPOUND_TYPES = List.of(
            SteamDist::getDist);

    private static final List<BiFunction<Game, Path, Optional<GameDist>>> ALL_COMPOUND_TYPES = List.of(
            SteamDist::getDist,
            WindowsStoreDist::getDist);

    private static final List<BiFunction<Game, Path, Optional<GameDist>>> BASIC_DISTS = List.of(
            PdxLauncherDist::getDist,
            LegacyLauncherDist::getDist,
            NoLauncherDist::getDist
    );

    public static Optional<GameDist> detectDist(Game g, boolean checkXbox) {
        return getCompoundDistFromDirectory(g, null, checkXbox)
                .or(() -> {
                        for (var p : getInstallDirSearchPaths(g)) {
                            var dist = GameDists.getBasicDistFromDirectory(g, p);
                            if (dist.isPresent()) {
                                return dist;
                            }
                        }
                        return Optional.empty();
                });
    }

    public static GameDist detectDistFromDirectory(Game g, Path dir) {
        Objects.requireNonNull(dir);

        return getCompoundDistFromDirectory(g, dir, true)
                .or(() -> GameDists.getBasicDistFromDirectory(g, dir))
                .orElse(new CatchAllDist(g, dir));
    }

    static Optional<GameDist> getBasicDistFromDirectory(Game g, Path dir) {
        // Basic dists do require a directory
        Objects.requireNonNull(dir);

        for (var e : BASIC_DISTS) {
            var r = e.apply(g, dir);
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    private static Optional<GameDist> getCompoundDistFromDirectory(Game g, Path dir, boolean checkXbox) {
        for (var e : checkXbox ? ALL_COMPOUND_TYPES : FAST_COMPOUND_TYPES) {
            var r = e.apply(g, dir);
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    public static JsonNode toNode(GameDist d) {
        return new TextNode(d.getInstallLocation().toString());
    }

    private static List<Path> getInstallDirSearchPaths(Game g) {
        var installDirSearchPaths = new ArrayList<Path>();
        if (SystemUtils.IS_OS_WINDOWS) {
            for (var root : FileSystems.getDefault().getRootDirectories()) {
                for (var name : g.getCommonInstallDirNames()) {
                    installDirSearchPaths.add(root.resolve("Program Files (x86)").resolve(name));
                }

                // Paradox Games Launcher path
                if (g.getParadoxGamesLauncherName() != null) {
                    installDirSearchPaths.add(root.resolve("Program Files (x86)").resolve("Paradox Interactive")
                            .resolve("games").resolve(g.getParadoxGamesLauncherName()));
                }
            }
        } else {
            for (var name : g.getCommonInstallDirNames()) {
                installDirSearchPaths.add(OsHelper.getUserDocumentsPath().resolve("Paradox Interactive").resolve(name));
            }
        }
        return installDirSearchPaths;
    }
}
