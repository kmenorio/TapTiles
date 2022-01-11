package com.taptiles;


/**
 * A simple TapTiles clone implemented in JavaFX. When compiling to a JAR file,
 * it is recommended to package and run the JAR in Java 8 due to build issues
 * related to newer Java and JavaFX versions.
 * 
 * @author Kyle Enorio
 * @date January 3, 2021
 * @version 1.0.0
 */


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;


public class TapTiles extends Application {
    private final Integer WIN_X = 400;      // window width
    private final Integer WIN_Y = 450;      // window height                    
    
    private final Integer TILE_X = 100;     // tile width
    private final Integer TILE_Y = 150;     // tile height
    
    private final Integer GUIDE_X = 100;    // background for key guide width
    private final Integer GUIDE_Y = 40;     // background for key guide height
    
    private final Integer BTN_X = 100;      // menu button width
    private final Integer BTN_Y = 100;      // menu button height
    
    private final Integer TILE_COUNT = 4;   // number of total tiles            
    
    // arraylist to store rectangle objects for the tiles
    private final ArrayList<Rectangle> TILE_RECT;
    
    // used to track the frontmost scrolling tile
    // index 0: contains position
    // index 1: contains tile index from TILE_RECT
    private final Queue<Integer[]> TILE_QUEUE;
    
    // tile moves at a factor of TILE_Y to avoid space between two tiles
    // because tile is placed at -TILE_Y
    private final Integer[] SPEED_MOVE = { 2, 3, 5, 10, 15 };
    
    private final Integer[] SPEED_LEVEL = { 10, 25, 45, 75, 110 };
    
    private final Label SCORE_ACTIVE;
    private final Label SCORE_HIGH;
    
    private final String KEYS = "DFJK";
    
    private final HashMap<String, Boolean> KEYS_ACTIVE; // tracks pressed keys
    private final ArrayList<Rectangle> KEYS_GUIDE;      // ui for set keys
    
    // global instance of menu ui to avoid creating menu from scratch each call
    private final VBox MENU_PANE;   
    
    private final String SOUND_DIR = "audio/";
    private final String[] SOUND_NOTES = {
        "01_c6.wav", "02_c#6.wav", "03_d6.wav", 
        "04_d#6.wav", "05_e6.wav", "06_f6.wav", 
        "07_f#6.wav", "08_g6.wav", "09_g#6.wav", 
        "10_a6.wav", "11_a#6.wav", "12_b6.wav", 
        "13_c7.wav", "14_c#7.wav", "15_d7.wav",
        "16_d#7.wav", "17_e7.wav", "18_f7.wav", 
        "19_f#7.wav", "20_g7.wav", "21_a#7.wav", 
        "21_g#7.wav", "22_a7.wav", "23_b7.wav", 
    };
    
    // global instance of audio to avoid loading wav each call
    private final ArrayList<AudioClip> SOUND_PLAYER;
    
    private final Label SOUND_STATUS;               // ui display for loaded sheet
    
    private final ArrayList<Integer> SOUND_SHEET;   // indexes of wav from sheet 
    
    private File sheetFile;
    
    private Boolean isGameRunning;
    private Boolean isKeyHighlighted;
    private Boolean isSheetLoaded;
    
    private Integer score;
    private Integer hiScore;
    
    // global instance of animation timer to allow start/stop anywhere
    private AnimationTimer tileTimer;
    
    public TapTiles() {
        isGameRunning = false;
        isKeyHighlighted = true;
        isSheetLoaded = false;
        
        score = 0;
        hiScore = 0;
        
        TILE_RECT = new ArrayList<>();
        
        TILE_QUEUE = new LinkedList<>();
        
        SCORE_ACTIVE = new Label();
        SCORE_HIGH = new Label();
        
        KEYS_ACTIVE = new HashMap<>();
        KEYS_GUIDE = new ArrayList<>();
        
        MENU_PANE = new VBox();
        
        SOUND_PLAYER = new ArrayList<>();
        SOUND_STATUS = new Label();
        SOUND_SHEET = new ArrayList<>();
        
        initSound();
        initRect();
        initMenuPane();
        
        updateScoreInfo();
        updateSheetInfo();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
    
    /**
     * Returns the version of Java running the JAR/code. Due to the way
     * Java returns its own version, this function simplifies the version from
     * System.getProperty("java.version") into a single number.
     * a single number.
     * 
     * @return  Java version excluding sub-versions
     */
    private Integer getVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } 
        else {
            int dot = version.indexOf(".");
            if(dot != -1) { version = version.substring(0, dot); }
        } 
        return Integer.parseInt(version);
    }
    
    /**
     * Returns HBox object with a rectangle background and key label.
     * 
     * @param key       the character of a key to display
     * @param rectIndex index of rectangle to set as as background for key label
     * @return          hbox containing the rectangle and key label
     */
    private HBox createGuideTile(Character key, Integer rectIndex) {
        Label lblKey = new Label(key.toString());
        
        StackPane pnTileKey = new StackPane();
        pnTileKey.setAlignment(Pos.BOTTOM_CENTER);
        pnTileKey.getChildren().add(KEYS_GUIDE.get(rectIndex));
        pnTileKey.getChildren().add(lblKey);
        
        HBox pnTile = new HBox();
        pnTile.setAlignment(Pos.CENTER);
        pnTile.setPrefWidth(WIN_X);
        pnTile.setPrefHeight(100);
        pnTile.getChildren().add(pnTileKey);
        
        return pnTile;
    }
    
    /**
     * Adds a new rectangle representing a tile to an arraylist containing all
     * the tiles. Defaults the y-position of the tile outside the visible area.
     */
    private void createTileRect() {
        Rectangle rectTile = new Rectangle(0, -TILE_Y, TILE_X, TILE_Y);
        
        TILE_RECT.add(rectTile);
    }
    
    /**
     * Adds a new rectangle for the guide label to an arraylist containing all
     * the background rectangles for the guide labels.
     */
    private void createGuideRect() {
        Rectangle rectTile = new Rectangle(GUIDE_X, GUIDE_Y);
        rectTile.setFill(Color.LIGHTGRAY);
        
        KEYS_GUIDE.add(rectTile);
    }
    
    /**
     * Returns a VBox with all the controls needed for the menu. Required as a
     * separate function an even requires a parent window.
     * 
     * Responsible for restarting animations and displays the current sheet 
     * loaded and high score.
     * 
     * @param parent    parent window needed for the an event
     * @return          vbox containing the menu items and controls
     * @see Button Background BackgroundFill VBox
     */
    private VBox createMenuPane(Stage parent) {
        Button btnStart = new Button();
        btnStart.setText("Start");
        btnStart.setMinWidth(BTN_X);
        btnStart.setMaxWidth(BTN_Y);
        btnStart.setOnAction(event -> {
            restartAnim();
        });
        
        Button btnLoad = new Button();
        btnLoad.setMinWidth(BTN_X);
        btnLoad.setMaxWidth(BTN_Y);
        btnLoad.setText("Load");
        btnLoad.setOnAction(event -> {
            loadSheet(parent);
        });
        
        MENU_PANE.getChildren().add(btnStart);
        MENU_PANE.getChildren().add(btnLoad);
        MENU_PANE.getChildren().add(SCORE_HIGH);
        MENU_PANE.getChildren().add(SOUND_STATUS);
        
        BackgroundFill bgcMenuColor = new BackgroundFill(
                Color.rgb(177, 177, 177, 0.7),
                CornerRadii.EMPTY, 
                Insets.EMPTY);
        
        MENU_PANE.setBackground(new Background(bgcMenuColor));
        
        return MENU_PANE;
    }
    
    /**
     * Initializes menu position and size.
     * 
     * @see VBox
     */
    private void initMenuPane() {
        MENU_PANE.setAlignment(Pos.CENTER);
        MENU_PANE.setPrefHeight(WIN_Y);
        MENU_PANE.setPrefWidth(WIN_X);
    }
    
    /**
     * Initializes rectangles used as tiles or backgrounds.
     */
    private void initRect() {
        for (int i = 0; i < TILE_COUNT; i++) {
            createTileRect();
            createGuideRect();
        }
    }
    
    /**
     * Initializes all wav files into memory. Avoids reading wav files every
     * call, reducing processing needed.
     * 
     * @see AudioClip
     */
    private void initSound() {
        for (String s : SOUND_NOTES) {
            AudioClip sound = new AudioClip(getClass().getResource(SOUND_DIR + s).toString());
            SOUND_PLAYER.add(sound);
        }
    }
    
    /**
     * Updates score info in UI. Also updates high score each call since it
     * is hidden along with the menu.
     * 
     * @see Label
     */
    private void updateScoreInfo() {
        SCORE_ACTIVE.setText(score.toString());
        SCORE_HIGH.setText("Hiscore: " + hiScore.toString());
    }
    
    /**
     * Updates sheet loaded status in UI.
     * 
     * @see Label
     */
    private void updateSheetInfo() {
        if (isSheetLoaded) {
            SOUND_STATUS.setText("Loaded " + sheetFile.getName());
        }
        else {
            SOUND_STATUS.setText("No sheet loaded");
        }
    }
    
    /**
     * Loads and parses sheet data.
     * 
     * @param parent 
     */
    private void loadSheet(Stage parent) {
        FileChooser dialog = new FileChooser();
        sheetFile = dialog.showOpenDialog(parent);
        
        if (sheetFile != null) {
            try {
                /**
                 * Workaround used to read a file in entirety including 
                 * whitespace. Scanner was not used due to issues encountered 
                 * by some which led to a maximum of only 1024 bytes read.
                 * 
                 * NOTE: Memory map was not used as it is probably overkill, 
                 * which  means this block potentially could hog up the ram when 
                 * reading extremely  large files.
                */
                byte[] data;
                try (FileInputStream stream = new FileInputStream(sheetFile)) {
                    data = new byte[(int) sheetFile.length()];
                    stream.read(data);
                }
                // END BLOCK
                
                String sheet = new String(data, "UTF-8");
                String[] notes = sheet.split(" ");
                
                SOUND_SHEET.clear();
                
                for (String s : notes) {
                    Integer noteIndex = Integer.parseInt(s);
                    
                    if (noteIndex >= 0 && noteIndex < SOUND_NOTES.length) {
                        SOUND_SHEET.add(noteIndex);
                    }
                    else {  // cancel parse when index is not within note count
                        throw new IOException("Invalid index");
                    }
                }
                
                isSheetLoaded = true;
                updateSheetInfo();
            } catch (IOException | NumberFormatException e) { 
                System.out.println("ERROR: Failed to parse sheet!");
                
                isSheetLoaded = false;
                SOUND_SHEET.clear();
                
                updateSheetInfo();
            }
        }
    }
    
    /**
     * Resets all values to default. Restarts the animation (game) after reset.
     */
    private void restartAnim() {
        for (Rectangle r : TILE_RECT) {
            r.setX(0);
            r.setY(-TILE_Y);
        }
        
        for (int i = 0; i < KEYS_GUIDE.size(); i++) {
            KEYS_GUIDE.get(i).setFill(Color.LIGHTGRAY);
        }
        
        score = 0;
        isGameRunning = true;
        isKeyHighlighted = false;
        updateScoreInfo();
        
        TILE_QUEUE.clear();
        KEYS_ACTIVE.clear();
        
        MENU_PANE.setVisible(false);
        
        tileTimer = new TileTimer();
        tileTimer.start();
    }
    
    /**
     * Ends tile animation. Opens up menu and updates high score.
     */
    private void endAnim() {
        isGameRunning = false;
        
        if (score > hiScore) {
            hiScore = score;
            updateScoreInfo();
        }
        
        MENU_PANE.setVisible(true);
    }
    
    /**
     * Verifies if the key is valid and corresponds to the foremost tile.
     * 
     * @param event KeyEvent object for the triggered event
     */
    private void verifyKeyPressed(KeyEvent event) {
        String keyCode = event.getCode().toString();

        // accept KeyEvent only once from a key until key is released
        if (!KEYS_ACTIVE.containsKey(keyCode)) {
            KEYS_ACTIVE.put(keyCode, false);

            if (TILE_QUEUE.peek() != null) {
                Integer keyPos = KEYS.indexOf(keyCode);

                // key pressed should be valid and is not already pressed
                if (keyPos != -1 && isGameRunning && !KEYS_ACTIVE.get(keyCode)) {
                    KEYS_GUIDE.get(keyPos).setFill(Color.GRAY);

                    Integer[] tilePos = TILE_QUEUE.poll();
                    
                    if (Objects.equals(keyPos, tilePos[0])) {
                        TILE_RECT.get(tilePos[1]).setY(-TILE_Y); 
                        score++;
                        updateScoreInfo();
                    }
                    else {
                        endAnim();
                    }
                }
            }
        }
    }
    
    private void verifyKeyReleased(KeyEvent event) {
        String keyCode = event.getCode().toString();
        Integer keyPos = KEYS.indexOf(keyCode);

        KEYS_ACTIVE.remove(keyCode);

        // accepts only valid presses
        if (keyPos != -1) {
            if (!isKeyHighlighted) {
                if (isGameRunning) {
                    KEYS_GUIDE.get(keyPos).setFill(Color.LIGHTGRAY);
                }
                else if (!isGameRunning){
                    KEYS_GUIDE.get(keyPos).setFill(Color.RED);
                    isKeyHighlighted = true;
                }

                if (isSheetLoaded) {
                    Integer sheetIndex = (score - 1) % SOUND_SHEET.size();
                    Integer wavIndex = SOUND_SHEET.get(sheetIndex);

                    SOUND_PLAYER.get(wavIndex - 1).play();
                }
            }
        }
    }
    
    /**
     * Creates the UI for the game.
     * 
     * @param stage
     * @throws Exception 
     */
    @Override
    public void start(Stage stage) throws Exception {
        HBox pnScore = new HBox();  // container for score-related objects
        pnScore.setAlignment(Pos.CENTER);
        pnScore.setPrefWidth(WIN_X);
        pnScore.setPrefHeight(100);
        pnScore.getChildren().add(SCORE_ACTIVE);
        
        HBox pnGuide = new HBox();  // container for key guide objects
        pnGuide.setAlignment(Pos.BOTTOM_LEFT);
        pnGuide.setPrefWidth(WIN_X);
        pnGuide.setPrefHeight(WIN_Y);
        for (int i = 0; i < TILE_COUNT; i++) {
            pnGuide.getChildren().add(createGuideTile(KEYS.charAt(i), i));
        }
        
        Pane pnMain = new Pane();   // container for all objects
        pnMain.getChildren().add(pnGuide);
        pnMain.getChildren().addAll(TILE_RECT);
        pnMain.getChildren().add(pnScore);
        pnMain.getChildren().add(createMenuPane(stage));
        
        Scene scene = new Scene(pnMain, WIN_X, WIN_Y);
        scene.setOnKeyPressed(event -> {
            verifyKeyPressed(event);
        });
        scene.setOnKeyReleased(event -> {
            verifyKeyReleased(event);
        });
        try {
            scene.getStylesheets().addAll(
                    getClass().getResource("TapTiles.css").toExternalForm());
        } catch (NullPointerException e) {  // still runs program if no css found
            System.out.println("ERROR: Stylesheet not found!");
        }
        
        /**
         * Offsets window size if running in Java 8 or below. Due to empty space
         * appearing at the bottom of the window, the size is offset by a 
         * constant value because the empty space is ugly.
         */
        if (getVersion() <= 8) {
            stage.setWidth(scene.getWidth());
            stage.setHeight(WIN_Y + 29);
        }
        
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setTitle("Tap Tiles (JavaFX Version)");
        stage.show();
    }
    
    /**
     * Animates the tile to move downward.
     */
    private class TileTimer extends AnimationTimer {
        private final Random RANDOM = new Random();
        
        private Integer newTile = 0;
        private Integer speed = 0;
        
        public TileTimer() {
            generateRandomTile();
            speed = 0;
        }
        
        /**
         * Changes the speed of the tile according to the score.
         */
        private void adjustSpeed() {
            for (int i = 0; i < SPEED_LEVEL.length - 1; i++) {
                if (score >= SPEED_LEVEL[i] && score < SPEED_LEVEL[i + 1]) {
                    speed = i + 1;
                }
            }
        }
        
        /**
         * Designates the next tile to move if current moving tile is already
         * fully out.
         */
        private void generateRandomTile() {
            Integer nextTile = (newTile + 1) % TILE_COUNT;
            
            if (TILE_RECT.get(nextTile).getY() <= -TILE_Y) {
                newTile = nextTile;
                Integer[] pos = {RANDOM.nextInt(TILE_COUNT), newTile};
                
                TILE_RECT.get(newTile).setX(pos[0] * TILE_X);
                
                TILE_QUEUE.add(pos);
                adjustSpeed();
            }
        }
        
        /**
         * Moves the tile downwards.
         * 
         * @param l 
         */
        @Override
        public void handle(long l) {
            for(int i = 0; i < TILE_COUNT; i++) {
                // ends game if tile is no longer visible from bottom
                if (!isGameRunning || TILE_RECT.get(i).getY() >= WIN_Y) {
                    endAnim();
                    stop();
                }
                // moves tile if already moving or is next tile to move
                else if (TILE_RECT.get(i).getY() > -TILE_Y || i == newTile) {
                    if (i == newTile) {
                        // designates next tile to move if none is or is already moving
                        if (TILE_RECT.get(i).getY() >= 0 || TILE_QUEUE.peek() == null) {
                            generateRandomTile();
                            continue;
                        }
                    }
                    
                    TILE_RECT.get(i).setY(TILE_RECT.get(i).getY() + SPEED_MOVE[speed]);
                }
            }
        }
    }
}
