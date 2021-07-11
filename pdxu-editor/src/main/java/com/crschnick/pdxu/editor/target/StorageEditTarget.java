package com.crschnick.pdxu.editor.target;

import com.crschnick.pdxu.app.installation.GameFileContext;
import com.crschnick.pdxu.app.savegame.SavegameActions;
import com.crschnick.pdxu.app.savegame.SavegameEntry;
import com.crschnick.pdxu.app.savegame.SavegameStorage;
import com.crschnick.pdxu.io.node.ArrayNode;
import com.crschnick.pdxu.io.parser.TextFormatParser;
import com.crschnick.pdxu.model.SavegameInfo;

import java.util.Map;

public class StorageEditTarget<T, I extends SavegameInfo<T>> extends EditTarget {

    private final GameFileContext context;
    private final SavegameStorage<T,I> storage;
    private final SavegameEntry<T, I> entry;
    private final EditTarget target;

    public StorageEditTarget(SavegameStorage<T, I> storage, SavegameEntry<T, I> entry, EditTarget target) {
        super(storage.getSavegameFile(entry));
        this.storage = storage;
        this.entry = entry;
        this.target = target;
        this.context = GameFileContext.fromInfo(entry.getInfo());
    }

    @Override
    public boolean canSave() {
        return target.canSave();
    }

    @Override
    public boolean isSavegame() {
        return target.isSavegame();
    }

    @Override
    public Map<String, ArrayNode> parse() throws Exception {
        return target.parse();
    }

    @Override
    public void write(Map<String, ArrayNode> nodeMap) throws Exception {
        target.write(nodeMap);
        SavegameActions.reloadSavegame(entry);
    }

    @Override
    public TextFormatParser getParser() {
        return target.getParser();
    }

    @Override
    public String getName() {
        return storage.getEntryName(entry);
    }

    @Override
    public GameFileContext getFileContext() {
        return context;
    }
}