package com.crschnick.pdx_unlimiter.app.savegame;

import com.crschnick.pdx_unlimiter.app.game.GameCampaignEntry;
import com.crschnick.pdx_unlimiter.app.game.GameLocalisation;
import com.crschnick.pdx_unlimiter.core.data.Eu4Tag;
import com.crschnick.pdx_unlimiter.core.data.GameDateType;
import com.crschnick.pdx_unlimiter.core.parser.Node;
import com.crschnick.pdx_unlimiter.core.savegame.Eu4SavegameInfo;
import com.crschnick.pdx_unlimiter.core.savegame.Eu4SavegameParser;

public class Eu4SavegameCache extends SavegameCache<
        Eu4Tag,
        Eu4SavegameInfo> {

    public Eu4SavegameCache() {
        super("eu4", "eu4", GameDateType.EU4, new Eu4SavegameParser());
    }

    @Override
    protected String getDefaultEntryName(Eu4SavegameInfo info) {
        return info.getDate().toDisplayString();
    }

    @Override
    protected String getDefaultCampaignName(GameCampaignEntry<Eu4Tag, Eu4SavegameInfo> latest) {
        return GameLocalisation.getTagNameForEntry(latest, latest.getInfo().getTag());
    }

    @Override
    protected Eu4SavegameInfo loadInfo(Node n) throws Exception {
        return Eu4SavegameInfo.fromSavegame(n);
    }
}
