package com.crschnick.pdx_unlimiter.app.savegame;

import com.crschnick.pdx_unlimiter.app.game.GameCampaign;
import com.crschnick.pdx_unlimiter.app.game.GameCampaignEntry;
import com.crschnick.pdx_unlimiter.app.game.SavegameManagerState;
import com.crschnick.pdx_unlimiter.app.installation.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.util.ThreadHelper;
import com.crschnick.pdx_unlimiter.core.data.GameVersion;
import com.crschnick.pdx_unlimiter.core.savegame.SavegameInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

public class SavegameActions {

    public static boolean isEntryCompatible(GameCampaignEntry<?,?> entry) {
        SavegameManagerState s = SavegameManagerState.get();
        boolean missingMods = entry.getInfo().getMods().stream()
                .map(m -> s.current().getInstallation().getModForName(m))
                .anyMatch(Optional::isEmpty);

        boolean missingDlc = entry.getInfo().getDlcs().stream()
                .map(m -> s.current().getInstallation().getDlcForName(m))
                .anyMatch(Optional::isEmpty);

        return areCompatible(s.current().getInstallation().getVersion(), entry.getInfo().getVersion()) &&
                !missingMods && !missingDlc;
    }

    public static boolean isVersionCompatible(GameCampaignEntry<?, ?> entry) {
        return areCompatible(
                SavegameManagerState.get().current().getInstallation().getVersion(),
                entry.getInfo().getVersion());
    }

    private static boolean areCompatible(GameVersion gameVersion, GameVersion saveVersion) {
        return gameVersion.getFirst() == saveVersion.getFirst() && gameVersion.getSecond() == saveVersion.getSecond();
    }

    public static <T, I extends SavegameInfo<T>> void openCampaignEntry(GameCampaignEntry<T, I> entry) {
        ThreadHelper.open(SavegameManagerState.get().<T,I>current().getSavegameCache().getPath(entry));
    }

    public static Optional<Path> exportCampaignEntry() {
        SavegameManagerState s = SavegameManagerState.get();
        try {
            var path = s.current().getInstallation().getExportTarget(
                    s.current().getSavegameCache(), s.globalSelectedEntryProperty().get());
            s.current().getSavegameCache().exportSavegame(s.globalSelectedEntryProperty().get(), path);
            return Optional.of(path);
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return Optional.empty();
        }
    }

    public static <T, I extends SavegameInfo<T>> void moveCampaignEntry(
            GameCampaign<T,I> campaign, GameCampaignEntry<T,I> entry) {
        SavegameManagerState s = SavegameManagerState.get();
        s.<T,I>current().getSavegameCache().moveEntry(campaign, entry);
        SavegameManagerState.get().selectEntry(entry);
    }

    public static void launchCampaignEntry() {
        SavegameManagerState s = SavegameManagerState.get();
        if (s.globalSelectedEntryProperty().get() == null) {
            return;
        }

        var e = s.globalSelectedEntryProperty().get();

        if (!isEntryCompatible(e)) {
            boolean startAnyway = s.current().getGuiFactory().displayIncompatibleWarning(e);
            if (!startAnyway) {
                return;
            }
        }

        Optional<Path> p = exportCampaignEntry();
        if (p.isPresent()) {
            try {
                s.current().getInstallation().writeLaunchConfig(
                        s.globalSelectedEntryProperty().get().getName(),
                        s.globalSelectedCampaignProperty().get().getLastPlayed(), p.get());

                var mods = e.getInfo().getMods().stream()
                        .map(m -> s.current().getInstallation().getModForName(m))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
                var dlcs = e.getInfo().getDlcs().stream()
                        .map(d -> s.current().getInstallation().getDlcForName(d))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
                s.current().getInstallation().writeDlcLoadFile(mods, dlcs);

                s.globalSelectedCampaignProperty().get().lastPlayedProperty().setValue(Instant.now());
                s.current().getInstallation().startDirectly();
            } catch (Exception ex) {
                ErrorHandler.handleException(ex);
                return;
            }
        }
    }


    public static void importLatestSavegame() {
        var savegames = SavegameManagerState.get().current().getSavegameWatcher().getSavegames();
        if (savegames.size() == 0) {
            return;
        }

        FileImporter.addToImportQueue(savegames.get(0).toImportString());
    }
}