package com.crschnick.pdx_unlimiter.app;

import com.crschnick.pdx_unlimiter.app.achievement.AchievementManager;
import com.crschnick.pdx_unlimiter.app.game.GameAppManager;
import com.crschnick.pdx_unlimiter.app.game.GameInstallation;
import com.crschnick.pdx_unlimiter.app.game.GameIntegration;
import com.crschnick.pdx_unlimiter.app.gui.*;
import com.crschnick.pdx_unlimiter.app.installation.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.installation.LogManager;
import com.crschnick.pdx_unlimiter.app.installation.PdxuInstallation;
import com.crschnick.pdx_unlimiter.app.installation.Settings;
import com.crschnick.pdx_unlimiter.app.savegame.FileImporter;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameCache;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jnativehook.GlobalScreen;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public class PdxuApp extends Application {

    private static PdxuApp APP;
    private Image icon;
    private Pane layout;
    private BooleanProperty running = new SimpleBooleanProperty(true);

    public static PdxuApp getApp() {
        return APP;
    }

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (Exception e) {
            ErrorHandler.handleTerminalException(e);
        }
    }

    public Scene getScene() {
        return layout.getScene();
    }

    public void close() {
        running.setValue(false);
        Platform.exit();
        try {
            if (PdxuInstallation.getInstance().isNativeHookEnabled()) {
                GlobalScreen.unregisterNativeHook();
            }
        } catch (Exception e) {
            ErrorHandler.handleException(e);
        }
    }

    private void setup() throws Exception {
        PdxuInstallation.init();
        LogManager.init();
        ErrorHandler.init();

        LoggerFactory.getLogger(PdxuApp.class).info("Running pdxu with arguments: " + getParameters().getRaw());
        FileImporter.addToImportQueue(getParameters().getRaw().stream().map(Path::of).collect(Collectors.toList()));
        if (!PdxuInstallation.shouldStart()) {
            System.exit(0);
        }
    }

    private void postWindowSetup() throws Exception {
        Settings.init();

        GameImage.loadImages();
        layout = GuiLayout.createLayout();

        if (!GameIntegration.init()) {
            GuiSettings.showSettings(true);
        }
        GameAppManager.init();

        SavegameCache.loadData();
        FileImporter.init();
        AchievementManager.init();
        if (PdxuInstallation.getInstance().isNativeHookEnabled()) {
            GlobalScreen.registerNativeHook();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        APP = this;
        icon = new Image(PdxuApp.class.getResourceAsStream("logo.png"));
        primaryStage.getIcons().add(icon);
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                Stage.getWindows().stream()
                        .filter(w -> !w.equals(getScene().getWindow()))
                        .collect(Collectors.toList())
                        .forEach(w -> w.fireEvent(event));

                close();
            }
        });
        try {
            setup();
        } catch (Exception e) {
            ErrorHandler.handleTerminalException(e);
        }

        primaryStage.setTitle("Pdx-Unlimiter (" + PdxuInstallation.getInstance().getVersion() + ")");

        try {
            postWindowSetup();
        } catch (Exception e) {
            ErrorHandler.handleTerminalException(e);
        }

        Scene scene = new Scene(layout, 1000, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
        GuiStyle.addStylesheets(primaryStage.getScene());


    }

    public Image getIcon() {
        return icon;
    }

    public boolean isRunning() {
        return running.get();
    }

    public BooleanProperty runningProperty() {
        return running;
    }
}