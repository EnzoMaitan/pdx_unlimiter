package com.crschnick.pdx_unlimiter.app.savegame;

import com.crschnick.pdx_unlimiter.app.game.*;
import com.crschnick.pdx_unlimiter.app.gui.ImageLoader;
import com.crschnick.pdx_unlimiter.app.installation.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.installation.PdxuInstallation;
import com.crschnick.pdx_unlimiter.app.installation.TaskExecutor;
import com.crschnick.pdx_unlimiter.app.util.JsonHelper;
import com.crschnick.pdx_unlimiter.app.util.MemoryChecker;
import com.crschnick.pdx_unlimiter.app.util.RakalyHelper;
import com.crschnick.pdx_unlimiter.core.data.GameDate;
import com.crschnick.pdx_unlimiter.core.data.GameDateType;
import com.crschnick.pdx_unlimiter.core.parser.Node;
import com.crschnick.pdx_unlimiter.core.savegame.SavegameInfo;
import com.crschnick.pdx_unlimiter.core.savegame.SavegameParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.scene.image.Image;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class SavegameCache<
        T,
        I extends SavegameInfo<T>> {

    public static Eu4SavegameCache EU4;
    public static Hoi4SavegameCache HOI4;
    public static StellarisSavegameCache STELLARIS;
    public static Ck3SavegameCache CK3;
    public static Set<SavegameCache<?, ?>> ALL;

    private Logger logger = LoggerFactory.getLogger(getClass());


    private String fileEnding;
    private String name;
    private GameDateType dateType;
    private Path path;
    private SavegameParser parser;
    private volatile ObservableSet<SavegameCollection<T, I>> collections = FXCollections.synchronizedObservableSet(
            FXCollections.observableSet(new HashSet<>()));

    public SavegameCache(String name, String fileEnding, GameDateType dateType, SavegameParser parser) {
        this.name = name;
        this.parser = parser;
        this.fileEnding = fileEnding;
        this.dateType = dateType;
        this.path = PdxuInstallation.getInstance().getSavegameLocation().resolve(name);
    }

    public static void init() {
        if (GameInstallation.EU4 != null) {
            EU4 = new Eu4SavegameCache();
        }
        if (GameInstallation.HOI4 != null) {
            HOI4 = new Hoi4SavegameCache();
        }
        if (GameInstallation.STELLARIS != null) {
            STELLARIS = new StellarisSavegameCache();
        }
        if (GameInstallation.CK3 != null) {
            CK3 = new Ck3SavegameCache();
        }
        ALL = Stream.of(EU4, HOI4, STELLARIS, CK3)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (SavegameCache<?, ?> cache : ALL) {
            cache.loadData();
        }
    }

    public static void reset() {
        for (SavegameCache<?, ?> cache : ALL) {
            cache.saveData();
        }

        EU4 = null;
        HOI4 = null;
        STELLARIS = null;
        CK3 = null;
        ALL = Set.of();
    }

    private void loadData() {
        try {
            Files.createDirectories(getPath());
        } catch (IOException e) {
            ErrorHandler.handleTerminalException(e);
            return;
        }

        JsonNode node = null;
        try {
            if (Files.exists(getDataFile())) {
                InputStream in = Files.newInputStream(getDataFile());
                ObjectMapper o = new ObjectMapper();
                node = o.readTree(in.readAllBytes());
            }
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return;
        }

        if (node == null) {
            return;
        }

        {
            JsonNode c = node.required("campaigns");
            for (int i = 0; i < c.size(); i++) {
                String name = c.get(i).required("name").textValue();
                GameDate date = dateType.fromString(c.get(i).required("date").textValue());
                UUID id = UUID.fromString(c.get(i).required("uuid").textValue());
                if (!Files.isDirectory(getPath().resolve(id.toString()))) {
                    continue;
                }

                Instant lastDate = Instant.parse(c.get(i).required("lastPlayed").textValue());
                Image image = ImageLoader.loadImage(getPath().resolve(id.toString()).resolve("campaign.png"));
                collections.add(new GameCampaign<T, I>(lastDate, name, id, date, image));
            }
        }

        {
            // Legacy support, can be missing
            JsonNode f = Optional.ofNullable(node.get("folders")).orElse(JsonNodeFactory.instance.arrayNode());
            for (int i = 0; i < f.size(); i++) {
                String name = f.get(i).required("name").textValue();
                UUID id = UUID.fromString(f.get(i).required("uuid").textValue());
                if (!Files.isDirectory(getPath().resolve(id.toString()))) {
                    continue;
                }

                Instant lastDate = Instant.parse(f.get(i).required("lastPlayed").textValue());
                collections.add(new SavegameFolder<>(lastDate, name, id));
            }
        }


        for (SavegameCollection<T, I> collection : collections) {
            try {
                String typeName = collection instanceof GameCampaign ? "campaign" : "folder";
                InputStream campaignIn = Files.newInputStream(
                        getPath().resolve(collection.getUuid().toString()).resolve(typeName + ".json"));
                JsonNode campaignNode = new ObjectMapper().readTree(campaignIn.readAllBytes());
                StreamSupport.stream(campaignNode.required("entries").spliterator(), false).forEach(entryNode -> {
                    UUID eId = UUID.fromString(entryNode.required("uuid").textValue());
                    String name = Optional.ofNullable(entryNode.get("name")).map(JsonNode::textValue).orElse(null);
                    GameDate date = dateType.fromString(entryNode.required("date").textValue());
                    String checksum = entryNode.required("checksum").textValue();
                    collection.add(new GameCampaignEntry<T, I>(name, eId, null, checksum, date));
                });
            } catch (Exception e) {
                ErrorHandler.handleException(e, "Could not load campaign config of " + collection.getName(), null);
            }
        }
    }

    private void saveData() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();

        ArrayNode c = n.putArray("campaigns");
        getCollections().stream().filter(col -> col instanceof GameCampaign).forEach(col -> {
            GameCampaign<T,I> campaign = (GameCampaign<T, I>) col;
            ObjectNode campaignFileNode = JsonNodeFactory.instance.objectNode();
            ArrayNode entries = campaignFileNode.putArray("entries");
            campaign.getSavegames().stream()
                    .map(entry -> JsonNodeFactory.instance.objectNode()
                            .put("name", entry.getName())
                            .put("date", entry.getDate().toString())
                            .put("checksum", entry.getChecksum())
                            .put("uuid", entry.getUuid().toString()))
                    .forEach(entries::add);

            Path cFile = getPath()
                    .resolve(campaign.getUuid().toString()).resolve("campaign.json");
            Path backupCFile = getPath()
                    .resolve(campaign.getUuid().toString()).resolve("campaign_old.json");

            try {
                if (Files.exists(cFile)) {
                    Files.copy(cFile, backupCFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                ErrorHandler.handleException(e);
            }

            try {
                OutputStream out = Files.newOutputStream(cFile);
                JsonHelper.write(campaignFileNode, out);
            } catch (IOException e) {
                ErrorHandler.handleException(e);
                return;
            }

            try {
                ImageLoader.writePng(campaign.getImage(), getPath()
                        .resolve(campaign.getUuid().toString()).resolve("campaign.png"));
            } catch (IOException e) {
                ErrorHandler.handleException(e);
            }

            ObjectNode campaignNode = JsonNodeFactory.instance.objectNode()
                    .put("name", campaign.getName())
                    .put("date", campaign.getDate().toString())
                    .put("lastPlayed", campaign.getLastPlayed().toString())
                    .put("uuid", campaign.getUuid().toString());
            c.add(campaignNode);
        });


        ArrayNode f = n.putArray("folders");
        getCollections().stream().filter(col -> col instanceof SavegameFolder).forEach(col -> {
            SavegameFolder<T,I> folder = (SavegameFolder<T, I>) col;
            ObjectNode campaignFileNode = JsonNodeFactory.instance.objectNode();
            ArrayNode entries = campaignFileNode.putArray("entries");
            folder.getSavegames().stream()
                    .map(entry -> JsonNodeFactory.instance.objectNode()
                            .put("name", entry.getName())
                            .put("date", entry.getDate().toString())
                            .put("checksum", entry.getChecksum())
                            .put("uuid", entry.getUuid().toString()))
                    .forEach(entries::add);

            Path cFile = getPath()
                    .resolve(folder.getUuid().toString()).resolve("folder.json");
            Path backupCFile = getPath()
                    .resolve(folder.getUuid().toString()).resolve("folder_old.json");

            try {
                if (Files.exists(cFile)) {
                    Files.copy(cFile, backupCFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                ErrorHandler.handleException(e);
            }

            try {
                OutputStream out = Files.newOutputStream(cFile);
                JsonHelper.write(campaignFileNode, out);
            } catch (IOException e) {
                ErrorHandler.handleException(e);
                return;
            }

            ObjectNode folderNode = JsonNodeFactory.instance.objectNode()
                    .put("name", folder.getName())
                    .put("lastPlayed", folder.getLastPlayed().toString())
                    .put("uuid", folder.getUuid().toString());
            f.add(folderNode);
        });
        

        try {
            if (Files.exists(getDataFile())) {
                Files.copy(getDataFile(), getBackupDataFile(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ErrorHandler.handleException(e);
        }

        try {
            OutputStream out = Files.newOutputStream(getDataFile());
            JsonHelper.write(n, out);
        } catch (IOException e) {
            ErrorHandler.handleException(e);
        }
    }

    public synchronized void delete(SavegameCollection<T, I> c) {
        if (!this.collections.contains(c)) {
            return;
        }

        Path campaignPath = path.resolve(c.getUuid().toString());
        try {
            FileUtils.deleteDirectory(campaignPath.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (SavegameManagerState.get().globalSelectedCampaignProperty().get() == c) {
            SavegameManagerState.get().selectCollection(null);
        }

        this.collections.remove(c);
    }


    public synchronized void addNewFolder(String name) {
        var col = new SavegameFolder<T,I>(Instant.now(), name, UUID.randomUUID());
        try {
            Files.createDirectory(getPath().resolve(col.getUuid().toString()));
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return;
        }
        this.collections.add(col);
    }

    public synchronized GameCampaignEntry<T, I> addNewEntry(UUID campainUuid, UUID entryUuid, String checksum, I info) {
        GameCampaignEntry<T, I> e = new GameCampaignEntry<>(
                getDefaultEntryName(info),
                entryUuid,
                info,
                checksum,
                info.getDate());
        if (this.getSavegameCollection(campainUuid).isEmpty()) {
            logger.debug("Adding new campaign " + getDefaultCampaignName(e));
            GameCampaign<T, I> newCampaign = new GameCampaign<>(
                    Instant.now(),
                    getDefaultCampaignName(e),
                    campainUuid,
                    e.getDate(),
                    GameIntegration.getForSavegameCache(this).getGuiFactory().tagImage(e, info.getTag()));
            this.collections.add(newCampaign);
        }

        SavegameCollection<T, I> c = this.getSavegameCollection(campainUuid).get();
        logger.debug("Adding new entry " + e.getName());
        c.add(e);

        SavegameManagerState.get().selectEntry(e);
        return e;
    }

    protected abstract String getDefaultEntryName(I info);

    protected abstract String getDefaultCampaignName(GameCampaignEntry<T, I> latest);

    public synchronized boolean contains(GameCampaignEntry<?, ?> e) {
        return collections.stream()
                .anyMatch(c -> c.getSavegames().stream().anyMatch(ce -> ce.getUuid().equals(e.getUuid())));
    }

    public synchronized SavegameCollection<T, I> getSavegameCollection(GameCampaignEntry<T, I> e) {
        var campaign = collections.stream()
                .filter(c -> c.getSavegames().stream().anyMatch(ce -> ce.getUuid().equals(e.getUuid())))
                .findAny();
        return campaign.orElseThrow(() -> new IllegalArgumentException(
                "Could not find savegame collection for entry " + e.getName()));
    }

    public synchronized void moveEntry(
            SavegameCollection<T,I> to, GameCampaignEntry<T,I> entry) {
        var from = getSavegameCollection(entry);
        if (from == to) {
            return;
        }

        var srcDir = getPath(entry).toFile();
        try {
            FileUtils.copyDirectory(
                    srcDir,
                    getPath().resolve(to.getUuid().toString()).resolve(entry.getUuid().toString()).toFile());
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return;
        }

        from.getSavegames().remove(entry);
        to.getSavegames().add(entry);

        try {
            FileUtils.deleteDirectory(srcDir);
        } catch (IOException e) {
            ErrorHandler.handleException(e);
        }
        if (from.getSavegames().size() == 0) {
            delete(from);
        }
    }

    public synchronized void delete(GameCampaignEntry<T, I> e) {
        SavegameCollection<T, I> c = getSavegameCollection(e);
        if (!this.collections.contains(c) || !c.getSavegames().contains(e)) {
            return;
        }

        Path campaignPath = path.resolve(c.getUuid().toString());
        try {
            FileUtils.deleteDirectory(campaignPath.resolve(e.getUuid().toString()).toFile());
        } catch (IOException ex) {
            ErrorHandler.handleException(ex);
        }

        if (SavegameManagerState.get().globalSelectedEntryProperty().get() == e) {
            SavegameManagerState.get().selectEntry(null);
        }
        c.getSavegames().remove(e);
        if (c.getSavegames().size() == 0) {
            delete(c);
        }
    }

    public void loadEntryAsync(GameCampaignEntry<T, I> e) {
        if (e.infoProperty().isNull().get()) {
            TaskExecutor.getInstance().submitTask(() -> {
                LoggerFactory.getLogger(SavegameCache.class).debug("Loading entry " + getEntryName(e));
                try {
                    loadEntry(e);
                } catch (Exception exception) {
                    ErrorHandler.handleException(exception);
                }
            }, false);
        }
    }

    private synchronized void loadEntry(GameCampaignEntry<T, I> e) throws Exception {
        if (!MemoryChecker.checkForEnoughMemory()) {
            return;
        }

        LoggerFactory.getLogger(SavegameCache.class).debug("Starting to load entry " + getEntryName(e));
        if (e.infoProperty().isNotNull().get()) {
            return;
        }

        var file = getPath(e).resolve("savegame." + fileEnding);
        byte[] content = Files.readAllBytes(file);
        boolean melt = parser.isBinaryFormat(content);
        if (melt) {
            content = RakalyHelper.meltSavegame(file);
        }
        var node = parser.parse(content);
        I info = loadInfo(melt, node);
        e.infoProperty().set(info);
        LoggerFactory.getLogger(SavegameCache.class).debug("Loaded entry " + getEntryName(e));
    }

    protected abstract I loadInfo(boolean melted, Node n) throws Exception;

    public synchronized Path getSavegameFile(GameCampaignEntry<T, I> e) {
        return getPath(e).resolve("savegame." + fileEnding);
    }

    public synchronized Path getPath(GameCampaignEntry<T, I> e) {
        Path campaignPath = path.resolve(getSavegameCollection(e).getUuid().toString());
        return campaignPath.resolve(e.getUuid().toString());
    }

    public synchronized Optional<SavegameCollection<T, I>> getSavegameCollection(UUID uuid) {
        for (SavegameCollection<T, I> c : collections) {
            if (c.getUuid().equals(uuid)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public synchronized String getFileName(GameCampaignEntry<T, I> e) {
        return getSavegameCollection(e).getName() + " (" + e.getName().replace(":", ".") + ")." + fileEnding;
    }

    public synchronized void exportSavegame(GameCampaignEntry<T, I> e, Path destPath) throws IOException {
        Path srcPath = getPath(e).resolve("savegame." + fileEnding);
        FileUtils.forceMkdirParent(destPath.toFile());
        FileUtils.copyFile(srcPath.toFile(), destPath.toFile(), false);
        destPath.toFile().setLastModified(Instant.now().toEpochMilli());
    }

    synchronized boolean importSavegame(Path file) {
        if (!MemoryChecker.checkForEnoughMemory()) {
            return false;
        }

        try {
            importSavegameData(file);
            saveData();
            System.gc();
            return true;
        } catch (Exception e) {
            ErrorHandler.handleException(e, "Could not import " + name + " savegame", file);
            return false;
        }
    }

    private String getSaveFileName() {
        return "savegame." + fileEnding;
    }

    private void importSavegameData(Path file) throws Exception {
        logger.debug("Reading file " + file.toString());
        byte[] content = Files.readAllBytes(file);
        logger.debug("Read " + content.length + " bytes");

        var checksum = parser.checksum(content);
        logger.debug("Checksum is " + checksum);
        var exists = getCollections().stream().flatMap(SavegameCollection::entryStream)
                .filter(ch -> ch.getChecksum().equals(checksum))
                .findAny();
        if (exists.isPresent()) {
            logger.debug("Entry " + exists.get().getName() + " with checksum already in storage");
            loadEntry(exists.get());
            SavegameManagerState.get().selectEntry(exists.get());
            return;
        } else {
            logger.debug("No entry with checksum found");
        }

        boolean melt = parser.isBinaryFormat(content);
        if (melt) {
            logger.debug("Detected binary format. Invoking Rakaly ...");
            content = RakalyHelper.meltSavegame(file);
            logger.debug("Rakaly finished melting");
        }

        logger.debug("Parsing savegame info ...");
        Node node = parser.parse(content);
        logger.debug("Parsed savegame info");
        I info = loadInfo(melt, node);
        logger.debug("Loaded info");
        UUID uuid = info.getCampaignUuid();
        logger.debug("Campaign UUID is " + uuid.toString());

        UUID saveUuid = UUID.randomUUID();
        logger.debug("Generated savegame UUID " + uuid.toString());
        Path campaignPath = getPath().resolve(uuid.toString());
        Path entryPath = campaignPath.resolve(saveUuid.toString());

        FileUtils.forceMkdir(entryPath.toFile());
        FileUtils.copyFile(file.toFile(), entryPath.resolve(getSaveFileName()).toFile());
        this.addNewEntry(uuid, saveUuid, checksum, info);
    }

    public String getEntryName(GameCampaignEntry<T, I> e) {
        String cn = getSavegameCollection(e).getName();
        String en = e.getName();
        return cn + " (" + en + ")";
    }

    public Path getPath() {
        return path;
    }

    public Path getDataFile() {
        return getPath().resolve("campaigns.json");
    }

    public Path getBackupDataFile() {
        return getPath().resolve("campaigns_old.json");
    }

    public int indexOf(SavegameCollection<?, ?> c) {
        var list = new ArrayList<SavegameCollection<T, I>>(getCollections());
        list.sort(Comparator.comparing(SavegameCollection::getLastPlayed));
        Collections.reverse(list);
        return list.indexOf(c);
    }

    public Stream<SavegameCollection<T, I>> collectionStream() {
        var list = new ArrayList<SavegameCollection<T, I>>(getCollections());
        list.sort(Comparator.comparing(SavegameCollection::getLastPlayed));
        Collections.reverse(list);
        return list.stream();
    }

    public ObservableSet<SavegameCollection<T, I>> getCollections() {
        return collections;
    }

    public String getFileEnding() {
        return fileEnding;
    }

    public String getName() {
        return name;
    }
}
