package fr.clementgre.pdf4teachers.panel.sidebar.paint;

import fr.clementgre.pdf4teachers.components.SyncColorPicker;
import fr.clementgre.pdf4teachers.document.editions.elements.*;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.gallery.GalleryWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.MainScreen.MainScreen;
import fr.clementgre.pdf4teachers.panel.sidebar.SideTab;
import fr.clementgre.pdf4teachers.panel.sidebar.paint.lists.ImageListPane;
import fr.clementgre.pdf4teachers.panel.sidebar.paint.lists.VectorListPane;
import fr.clementgre.pdf4teachers.utils.PaneUtils;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.image.ImageUtils;
import fr.clementgre.pdf4teachers.utils.image.SVGPathIcons;
import fr.clementgre.pdf4teachers.utils.interfaces.StringToIntConverter;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.swing.plaf.PanelUI;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

public class PaintTab extends SideTab{

    public VBox root;

    // actions buttons
    public HBox commonActionButtons;
    
    public Button newImage;
    public Button newVector;
    public Button delete;
    
    public HBox vectorsActonButtons;
    public Button vectorCreateCurve;
    public ToggleButton vectorStraightLineMode;
    public ToggleGroup vectorDrawMode;
    public ToggleButton vectorModeDraw;
    public ToggleButton vectorModePoint;

    // common settings

    public TextField path;
    public Button vectorUndoPath;
    public Button browsePath;

    // vector Settings

    public VBox vectorsOptionPane;

    public ToggleButton doFillButton;
    public SyncColorPicker vectorFillColor;
    public SyncColorPicker vectorStrokeColor;
    public Spinner<Integer> vectorStrokeWidth;

    // advanced Options

    public TitledPane advancedOptionsPane;

    public Label widthTitle;
    public Label heightTitle;

    public Spinner<Integer> spinnerX;
    public Spinner<Integer> spinnerY;
    public Spinner<Integer> spinnerWidth;
    public Spinner<Integer> spinnerHeight;
    
    public Label repeatModeLabel;
    public ComboBox<String> repeatMode;
    public Label resizeModeLabel;
    public ComboBox<String> resizeMode;
    public Label rotateModeLabel;
    public ComboBox<String> rotateMode;
    
    // Lists
    
    public VectorListPane favouriteVectors;
    public ImageListPane favouriteImages;
    public VectorListPane lastVectors;
    public ImageListPane gallery;
    
    // WINDOWS
    
    public GalleryWindow galleryWindow = null;
    
    
    
    public PaintTab(){
        super("paint", SVGPathIcons.DRAW_POLYGON, 28, 30, null);
        MainWindow.paintTab = this;
    }

    @FXML
    public void initialize(){
        setContent(root);
        newImage.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.PICTURES, "darkgreen", 0, 22, 22, ImageUtils.defaultDarkColorAdjust));
        newVector.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.VECTOR_SQUARE, "darkblue", 0, 22, 22, ImageUtils.defaultDarkColorAdjust));
        delete.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.TRASH, "darkred", 0, 22, 22, ImageUtils.defaultDarkColorAdjust));

        vectorCreateCurve.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.CURVE, "black", 0, 22, 22, ImageUtils.defaultDarkColorAdjust));
        vectorStraightLineMode.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.RULES, "black", 0, 22, 22, ImageUtils.defaultDarkColorAdjust));
        vectorModeDraw.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.PAINT_BRUSH, "black", 0, 21, 21, ImageUtils.defaultDarkColorAdjust));
        vectorModePoint.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.PEN, "black", 0, 22, 22, ImageUtils.defaultDarkColorAdjust));

        vectorUndoPath.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.UNDO, "white", 0, 15, 15, ImageUtils.defaultWhiteColorAdjust));
        doFillButton.setGraphic(SVGPathIcons.generateImage(SVGPathIcons.FILL, "white", 0, 15, 15, ImageUtils.defaultWhiteColorAdjust));

        vectorStrokeWidth.getValueFactory().setConverter(new StringToIntConverter(0));

        PaneUtils.setPosition(spinnerX, 0, 0, -1, 26, true);
        PaneUtils.setPosition(spinnerY, 0, 0, -1, 26, true);
        PaneUtils.setPosition(spinnerWidth, 0, 0, -1, 26, true);
        PaneUtils.setPosition(spinnerHeight, 0, 0, -1, 26, true);

        spinnerX.getValueFactory().setConverter(new StringToIntConverter(0));
        spinnerY.getValueFactory().setConverter(new StringToIntConverter(0));
        spinnerWidth.getValueFactory().setConverter(new StringToIntConverter(0));
        spinnerHeight.getValueFactory().setConverter(new StringToIntConverter(0));

        ((SpinnerValueFactory.IntegerSpinnerValueFactory) spinnerX.getValueFactory()).setMax((int) Element.GRID_WIDTH);
        ((SpinnerValueFactory.IntegerSpinnerValueFactory) spinnerY.getValueFactory()).setMax((int) Element.GRID_HEIGHT);
        ((SpinnerValueFactory.IntegerSpinnerValueFactory) spinnerWidth.getValueFactory()).setMax((int) Element.GRID_WIDTH);
        ((SpinnerValueFactory.IntegerSpinnerValueFactory) spinnerHeight.getValueFactory()).setMax((int) Element.GRID_HEIGHT);

        PaneUtils.setHBoxPosition(path, 0, 30, 0);
        PaneUtils.setHBoxPosition(vectorUndoPath, 30, 30, 0);
        PaneUtils.setHBoxPosition(browsePath, 0, 30, 0);
        
        repeatMode.setItems(FXCollections.observableArrayList(Arrays.stream(GraphicElement.RepeatMode.values())
                .map((o) -> TR.tr(o.getKey())).collect(Collectors.toList())));
        repeatMode.getSelectionModel().select(0);
    
        resizeMode.setItems(FXCollections.observableArrayList(Arrays.stream(GraphicElement.ResizeMode.values())
                .map((o) -> TR.tr(o.getKey())).collect(Collectors.toList())));
        resizeMode.getSelectionModel().select(0);
    
        rotateMode.setItems(FXCollections.observableArrayList(Arrays.stream(GraphicElement.RotateMode.values())
                .map((o) -> TR.tr(o.getKey())).collect(Collectors.toList())));
        rotateMode.getSelectionModel().select(0);
        
        translate();
        setup();
    }

    public void translate(){
        // Advanced options
        advancedOptionsPane.setText(TR.tr("paintTab.advancedOptions"));
        repeatModeLabel.setText(TR.tr("paintTab.advancedOptions.repeatMode"));
        resizeModeLabel.setText(TR.tr("paintTab.advancedOptions.resizeMode"));
        rotateModeLabel.setText(TR.tr("paintTab.advancedOptions.rotateMode"));
        widthTitle.setText(TR.tr("letter.width"));
        heightTitle.setText(TR.tr("letter.height"));
        
    }

    public void setup(){
        
        delete.setOnAction(e -> deleteSelected());
    
        spinnerX.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(MainWindow.mainScreen.getSelected() instanceof GraphicElement element){
                if(element.getRealX() != newValue) element.setRealX(newValue);
            }
        });
        spinnerY.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(MainWindow.mainScreen.getSelected() instanceof GraphicElement element){
                if(element.getRealY() != newValue) element.setRealY(newValue);
            }
        });
        spinnerWidth.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(MainWindow.mainScreen.getSelected() instanceof GraphicElement element){
                if(element.getRealWidth() != newValue) element.setRealWidth(newValue);
            }
        });
        spinnerHeight.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(MainWindow.mainScreen.getSelected() instanceof GraphicElement element){
                if(element.getRealHeight() != newValue) element.setRealHeight(newValue);
            }
        });
        
        path.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if(e.getCode() == KeyCode.DELETE){
                if(path.getCaretPosition() == path.getText().length()){
                    deleteSelected();
                    e.consume();
                }
            }
        });
        
        newImage.setOnAction((e) -> {
            openGallery();
        });
        
        newImage.setOnAction((e) -> {
            PageRenderer page = MainWindow.mainScreen.document.getCurrentPageObject();
            
            ImageElement element = new ImageElement((int) (60 * Element.GRID_WIDTH / page.getWidth()), (int) (page.getMouseY() * Element.GRID_HEIGHT / page.getHeight()), page.getPage(), true,
                    50, new Random().nextBoolean() ? 150 : 20, GraphicElement.RepeatMode.KEEP_RATIO, GraphicElement.ResizeMode.CORNERS, GraphicElement.RotateMode.NEAR_CORNERS, "");
            
            page.addElement(element, true);
            element.centerOnCoordinatesY();
            MainWindow.mainScreen.setSelected(element);
        });
        
        MainWindow.mainScreen.selected.addListener(this::updateSelected);
        MainWindow.mainScreen.statusProperty().addListener(this::updateDocumentStatus);
        updateSelected(null, null, null);
    }
    
    private void deleteSelected(){
        Element element = MainWindow.mainScreen.getSelected();
        if(element != null){
            MainWindow.mainScreen.setSelected(null);
            element.delete();
        }
    }
    
    private void updateDocumentStatus(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue){
        if(newValue.intValue() == MainScreen.Status.OPEN){
            setGlobalDisable(false);
            setVectorsDisable(true);
        }else{
            setGlobalDisable(true);
            setVectorsDisable(true);
        }
    }
    public void updateSelected(ObservableValue<? extends Element> observable, Element oldValue, Element newValue){
        // Old element
        if(oldValue instanceof GraphicElement gElement){
            if(oldValue instanceof VectorElement element){ // Vector
            
            }else if(oldValue instanceof ImageElement element){ // Image
                element.imageIdProperty().unbind();
            }
            
        }
        
        // Disable/Enable nodes
        if(newValue instanceof GraphicElement gElement){
            setGlobalDisable(false);
    
            if(newValue instanceof VectorElement element){ // Vector
                setVectorsDisable(false);
            }else if(newValue instanceof ImageElement element){ // Image
                setVectorsDisable(true);
                path.setText(element.getImageId());
                element.imageIdProperty().bind(path.textProperty());
            }
            
            path.requestFocus();
            path.positionCaret(path.getText().length());
            gElement.realXProperty().addListener(this::editSpinXEvent);
            gElement.realYProperty().addListener(this::editSpinYEvent);
            gElement.realWidthProperty().addListener(this::editSpinWidthEvent);
            gElement.realHeightProperty().addListener(this::editSpinHeightEvent);
            
        }else{
            setGlobalDisable(true);
            setVectorsDisable(true);
        }
        
        // Load/Bind data
        if(newValue instanceof GraphicElement){
            
            if(newValue instanceof VectorElement){ // Vector
            
            }else{ // Image
            
            }
        }
    }
    
    public void editSpinXEvent(ObservableValue<? extends Number> observable, Number oldValue, Number newValue){
        if(!spinnerX.getValue().equals(newValue)) spinnerX.getValueFactory().setValue(newValue.intValue());
    }
    public void editSpinYEvent(ObservableValue<? extends Number> observable, Number oldValue, Number newValue){
        if(!spinnerY.getValue().equals(newValue)) spinnerY.getValueFactory().setValue(newValue.intValue());
    }
    public void editSpinWidthEvent(ObservableValue<? extends Number> observable, Number oldValue, Number newValue){
        if(!spinnerWidth.getValue().equals(newValue)) spinnerWidth.getValueFactory().setValue(newValue.intValue());
    }
    public void editSpinHeightEvent(ObservableValue<? extends Number> observable, Number oldValue, Number newValue){
        if(!spinnerHeight.getValue().equals(newValue)) spinnerHeight.getValueFactory().setValue(newValue.intValue());
    }
    
    public void setVectorsDisable(boolean disable){
        vectorUndoPath.setDisable(disable);
        setVectorActionButtonsVisible(!disable);
        setVectorOptionPaneVisible(!disable);
    }
    public void setVectorActionButtonsVisible(boolean visible){
        if(!visible){ // REMOVE
            commonActionButtons.getChildren().remove(vectorsActonButtons);
            
        }else if(!commonActionButtons.getChildren().contains(vectorsActonButtons)){ // ADD
            commonActionButtons.getChildren().add(vectorsActonButtons);
        }
    }
    public void setVectorOptionPaneVisible(boolean visible){
        if(!visible){ // REMOVE
            root.getChildren().remove(vectorsOptionPane);
            
        }else if(!root.getChildren().contains(vectorsOptionPane)){ // ADD
            root.getChildren().add(1, vectorsOptionPane);
        }
    }
    public void setGlobalDisable(boolean disable){
        advancedOptionsPane.setDisable(disable);
        delete.setDisable(disable);
        path.setDisable(disable);
        browsePath.setDisable(disable);
    }
    
    public void openGallery(){
        if(galleryWindow != null){
            galleryWindow.setIconified(false);
            galleryWindow.requestFocus();
        }else{
            galleryWindow = new GalleryWindow();
        }
    }
}
