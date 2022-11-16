package com.crschnick.pdxu.app.installation;

import com.crschnick.pdxu.io.node.Node;
import com.crschnick.pdxu.io.parser.TextFormatParser;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class GameDlc {

    private Path filePath;
    private Path dataPath;
    private String name;

    @Getter
    private boolean affectsCompatibility;

    public static Optional<GameDlc> fromDirectory(Path p) throws Exception {
        if (!Files.isDirectory(p)) {
            return Optional.empty();
        }

        String dlcName = p.getFileName().toString();
        String dlcId = dlcName.split("_")[0];
        Path filePath = p.resolve(dlcId + ".dlc");
        Path dataPath = p.resolve(dlcId + ".zip");

        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        Node node = TextFormatParser.text().parse(filePath);
        GameDlc dlc = new GameDlc();
        dlc.filePath = filePath;
        dlc.dataPath = dataPath;
        dlc.name = node.getNodeForKey("name").getString();

        if (node.getNodeForKeyIfExistent("affects_compatibility").map(Node::getBoolean).orElse(false)) {
            dlc.affectsCompatibility = true;
        }

        if (node.getNodeForKeyIfExistent("affects_save_compatibility").map(Node::getBoolean).orElse(false)) {
            dlc.affectsCompatibility = true;
        }

        if (!node.hasKey("affects_compatibility") && !node.hasKey("affects_save_compatibility")) {
            dlc.affectsCompatibility = true;
        }

        return Optional.of(dlc);
    }
    public Path getInfoFilePath() {
        return filePath;
    }

    public String getName() {
        return name;
    }

    public Path getDataPath() {
        return dataPath;
    }
}
