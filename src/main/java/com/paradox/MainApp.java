package com.paradox;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * PARADOX - Simple Deadlock Detection System.
 *
 * Entry point of the application. It loads the single window with the
 * three tabs (Dashboard, Simulation, Deadlock Detection) and applies the
 * stylesheet.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/simulation.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1020, 700);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        stage.setTitle("PARADOX - Simple Deadlock Detection System");
        stage.setMinWidth(950);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
