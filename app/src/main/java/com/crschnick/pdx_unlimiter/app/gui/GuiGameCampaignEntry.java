package com.crschnick.pdx_unlimiter.app.gui;

import com.crschnick.pdx_unlimiter.app.PdxuApp;
import com.crschnick.pdx_unlimiter.app.game.GameCampaignEntry;
import com.crschnick.pdx_unlimiter.app.game.GameIntegration;
import com.crschnick.pdx_unlimiter.eu4.savegame.SavegameInfo;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXMasonryPane;
import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.crschnick.pdx_unlimiter.app.gui.GuiStyle.*;

public class GuiGameCampaignEntry {


    public static <T,I extends SavegameInfo<T>> Node createCampaignEntryNode(GameCampaignEntry<T,I> e) {
        VBox main = new VBox();
        main.setAlignment(Pos.CENTER);
        main.setFillWidth(true);
        main.getProperties().put("entry", e);
        Label l = new Label(e.getDate().toDisplayString());
        l.getStyleClass().add(CLASS_DATE);

        JFXTextField name = new JFXTextField();
        name.getStyleClass().add(CLASS_TEXT_FIELD);
        name.setAlignment(Pos.CENTER);
        name.textProperty().bindBidirectional(e.nameProperty());

        Button open = new JFXButton();
        open.setOnMouseClicked((m) -> {
            GameIntegration.<T,I>current().openCampaignEntry(e);
        });
        open.setGraphic(new FontIcon());
        open.getStyleClass().add("open-button");


        Button achievements = new JFXButton();
        achievements.setGraphic(new FontIcon());
        achievements.setOnMouseClicked((m) -> {
            AchievementWindow.showAchievementList(e);
        });
        achievements.getStyleClass().add("achievement-button");


        Button del = new JFXButton();
        del.setGraphic(new FontIcon());
        del.setOnMouseClicked((m) -> {
            if (DialogHelper.showSavegameDeleteDialog()) {
                GameIntegration.<T,I>current().getSavegameCache().delete(e);
            }
        });
        del.getStyleClass().add("delete-button");


        var tagImage = GameIntegration.<T,I>current().getGuiFactory().createImage(e);
        HBox tagBar = new HBox(tagImage.getValue(), l);
        tagBar.getStyleClass().add(CLASS_TAG_BAR);
        tagImage.addListener((change, o, n) -> {
            Platform.runLater(() -> {
                tagBar.getChildren().set(0, tagImage.getValue());
                tagBar.layout();
            });
        });


        BorderPane layout = new BorderPane();
        layout.setLeft(tagBar);
        layout.setCenter(name);

        HBox buttonBar = new HBox(achievements, open, del);
        buttonBar.getStyleClass().add(CLASS_BUTTON_BAR);
        layout.setRight(buttonBar);

        tagBar.setAlignment(Pos.CENTER);
        layout.getStyleClass().add(CLASS_ENTRY_BAR);
        main.getChildren().add(layout);
        Node content = createSavegameInfoNode(e);

        InvalidationListener lis = (change) -> {
            Platform.runLater(() -> {
                layout.setBackground(GameIntegration.<T,I>current().getGuiFactory().createEntryInfoBackground(e));
            });
        };
        e.infoProperty().addListener(lis);
        if (e.infoProperty().isNotNull().get()) {
            lis.invalidated(null);
        }

        main.getChildren().add(content);
        main.getStyleClass().add(CLASS_ENTRY);
        main.setOnMouseClicked(event -> {
            if (e.infoProperty().isNotNull().get()) {
                GameIntegration.<T,I>current().selectEntry(e);
            }
        });
        return main;
    }

    private static <T, I extends SavegameInfo<T>>Node createSavegameInfoNode(GameCampaignEntry<T,I> entry) {
        StackPane stack = new StackPane();
        JFXMasonryPane grid =
                new JFXMasonryPane();
        grid.getStyleClass().add(CLASS_CAMPAIGN_ENTRY_NODE_CONTAINER);
        grid.setLayoutMode(JFXMasonryPane.LayoutMode.MASONRY);
        grid.setHSpacing(10);
        grid.setVSpacing(10);
        grid.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> 3 * grid.getCellHeight() + 2 * grid.getVSpacing() + grid.getPadding().getBottom() + grid.getPadding().getTop(), grid.paddingProperty()));
        grid.setLimitRow(3);

        JFXSpinner loading = new JFXSpinner();
        loading.getStyleClass().add(CLASS_ENTRY_LOADING);
        stack.getChildren().add(grid);
        if (entry.infoProperty().isNotNull().get()) {
            GameIntegration.<T,I>current().getGuiFactory().fillNodeContainer(entry, grid);
        } else {
            stack.getChildren().add(loading);
        }

        AtomicBoolean load = new AtomicBoolean(false);
        stack.sceneProperty().addListener((c, o, n) -> {
            if (stack.localToScreen(0, 0) == null) {
                return;
            }

            if (stack.localToScreen(0, 0).getY() < PdxuApp.getApp().getScene().getWindow().getHeight() && !load.get()) {
                load.set(true);
                GameIntegration.<T,I>current().getSavegameCache().loadEntryAsync(entry);
            }
        });

        entry.infoProperty().addListener((change) -> {
            Platform.runLater(() -> {
                stack.getChildren().remove(loading);
                GameIntegration.<T,I>current().getGuiFactory().fillNodeContainer(entry, grid);
            });
        });

        return stack;
    }
}