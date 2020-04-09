package fr.themsou.document.render;

import fr.themsou.document.editions.Edition;
import fr.themsou.document.editions.elements.Element;
import fr.themsou.document.editions.elements.TextElement;
import fr.themsou.main.Main;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

public class PageRenderer extends Pane {

    PageStatus status = PageStatus.HIDE;

    private ImageView renderView;
    private int page;
    private ArrayList<Element> elements = new ArrayList<>();
    public double mouseX = 0;
    public double mouseY = 0;

    private ProgressBar loader = new ProgressBar();

    public PageRenderer(int page){
        this.page = page;
        setStyle("-fx-background-color: white;");

        // LOADER
        loader.setPrefWidth(300);
        loader.setPrefHeight(20);
        loader.translateXProperty().bind(widthProperty().divide(2).subtract(loader.widthProperty().divide(2)));
        loader.translateYProperty().bind(heightProperty().divide(2).subtract(loader.heightProperty().divide(2)));
        loader.setVisible(false);

        // BINDINGS & SIZES SETUP
        PDRectangle pageSize = Main.mainScreen.document.pdfPagesRender.getPageSize(page);
        final double ratio = pageSize.getHeight() / pageSize.getWidth();

        setWidth(Main.mainScreen.pageWidthProperty().get());
        setHeight(Main.mainScreen.pageWidthProperty().get() * ratio);

        prefWidthProperty().bind(Main.mainScreen.pageWidthProperty());
        prefHeightProperty().bind(widthProperty().multiply(ratio));

        // BORDER
        DropShadow ds = new DropShadow();
        ds.setColor(Color.BLACK);
        setEffect(ds);

        // UPDATE MOUSE COORDINATES
        setOnMouseMoved(e -> {
            mouseX = e.getX();
            mouseY = e.getY();
        });
        setOnMouseDragged(e -> {

            mouseX = e.getX();
            mouseY = e.getY();
        });
        setOnMouseEntered(event -> Main.mainScreen.document.setCurrentPage(page));

        // START RENDER WHEN IS INTO THE VISIBLE PART OF THE SCROLL PANE
        Main.mainScreen.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            updateShowStatus();
        });

    }

    public void updateShowStatus(){

        Bounds mainScreenBounds = Main.mainScreen.localToScene(Main.mainScreen.getBoundsInParent());
        Bounds nodeBounds = localToScene(getBoundsInLocal());
        if(mainScreenBounds.intersects(nodeBounds.getMinX(), nodeBounds.getMinY()-nodeBounds.getHeight(), nodeBounds.getWidth(), nodeBounds.getHeight()*2)){
            setVisible(true);
            if(status == PageStatus.HIDE) render();
        }else{
            setVisible(false);
        }

    }
    private void render(){
        status = PageStatus.RENDERING;
        loader.setVisible(true);
        new Thread(() -> {
            try{
                renderView = new ImageView(SwingFXUtils.toFXImage((BufferedImage) Main.mainScreen.document.pdfPagesRender.renderPage(page), null));
            }catch(IOException e){
                e.printStackTrace();
                status = PageStatus.FAIL;
            }

            Platform.runLater(() -> {
                renderView.fitHeightProperty().bind(heightProperty());
                renderView.fitWidthProperty().bind(widthProperty());

                loader.setVisible(false);
                getChildren().add(renderView);
                status = PageStatus.RENDERED;
            });

        }).start();
    }

    public void clearElements(){
        getChildren().clear();
        getChildren().add(renderView);
        elements = new ArrayList<>();
        Main.lbTextTab.updateOnFileElementsList();
    }

    public void addElementSimple(Element element){

        if(element != null){
            elements.add(element);
            getChildren().add((Shape) element);
        }
    }
    public void addElement(Element element, boolean update){

        if(element != null){

            elements.add(element);
            getChildren().add((Shape) element);
            Edition.setUnsave();
            if(element instanceof TextElement){
                if(update) Main.lbTextTab.addOnFileElement((TextElement) element);
            }
        }
    }
    public void removeElement(Element element, boolean update){

        if(element != null){
            elements.remove(element);
            getChildren().remove((Shape) element);
            Edition.setUnsave();
            if(element instanceof TextElement){
                if(update) Main.lbTextTab.removeOnFileElement((TextElement) element);
            }

        }
    }

    public int getPage() {
        return page;
    }
    public ArrayList<Element> getElements() {
        return elements;
    }
}
