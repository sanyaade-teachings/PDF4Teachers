/*
 * Copyright (c) 2022-2025. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.interfaces.windows.booklet;

import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.interfaces.windows.AlternativeWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.utils.StringUtils;
import fr.clementgre.pdf4teachers.utils.dialogs.AlertIconType;
import fr.clementgre.pdf4teachers.utils.panes.PaneUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class BookletWindow extends AlternativeWindow<VBox> {
    
    private final ToggleButton convertKindMake = new ToggleButton(TR.tr("bookletWindow.convertKindMake"));
    private final ToggleButton convertKindDisassemble = new ToggleButton(TR.tr("bookletWindow.convertKindDisassemble"));
    
    private final CheckBox doNotReorderPages = new CheckBox(TR.tr("bookletWindow.doNotReorderPages"));
    private final CheckBox doTookPages4by4 = new CheckBox(TR.tr("bookletWindow.doTookPages4by4"));
    private final CheckBox doReverseOrder = new CheckBox(TR.tr("bookletWindow.doReverseOrder"));
    private final String copyName = StringUtils.removeAfterLastOccurrenceIgnoringCase(MainWindow.mainScreen.document.getFileName(), ".pdf") + "-backup.pdf";
    private final CheckBox doCopyOriginal = new CheckBox(TR.tr("bookletWindow.doCopyOriginal", copyName));
    
    private final Button convert = new Button(TR.tr("actions.convert"));
    
    public BookletWindow(){
        super(new VBox(), StageWidth.LARGE, TR.tr("bookletWindow.title"), TR.tr("bookletWindow.title"), TR.tr("bookletWindow.description"));
    }
    @Override
    public void setupSubClass(){
        
        HBox convertKind = new HBox();
        ToggleGroup convertKindGroup = new ToggleGroup();
        convertKindGroup.getToggles().addAll(convertKindMake, convertKindDisassemble);
        
        convertKindGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null){
                // Select a toggle that is not the one disabled
                if(convertKindGroup.getToggles().get(0) != oldValue)
                    convertKindGroup.getToggles().get(0).setSelected(true);
                else convertKindGroup.getToggles().get(1).setSelected(true);
            }
        });
        convertKindGroup.selectToggle(MainWindow.userData.bookletDoMakeBooklet ? convertKindMake : convertKindDisassemble);
        convertKindGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            MainWindow.userData.bookletDoMakeBooklet = newValue == convertKindMake;
            updateStatus();
        });
        
        convertKind.getChildren().addAll(convertKindMake, convertKindDisassemble);
        
        doNotReorderPages.setSelected(MainWindow.userData.bookletDoNotReorderPages);
        doTookPages4by4.setSelected(MainWindow.userData.bookletDoTookPages4by4);
        doReverseOrder.setSelected(MainWindow.userData.bookletDoReverseOrder);
        doCopyOriginal.setSelected(MainWindow.userData.bookletDoCopyOriginal);
        
        doNotReorderPages.selectedProperty().addListener((observable, oldValue, newValue) -> {
            MainWindow.userData.bookletDoNotReorderPages = newValue;
            updateStatus();
        });
        doTookPages4by4.selectedProperty().addListener((observable, oldValue, newValue) -> MainWindow.userData.bookletDoTookPages4by4 = newValue);
        doReverseOrder.selectedProperty().addListener((observable, oldValue, newValue) -> MainWindow.userData.bookletDoReverseOrder = newValue);
        doCopyOriginal.selectedProperty().addListener((observable, oldValue, newValue) -> MainWindow.userData.bookletDoCopyOriginal = newValue);
        
        doReverseOrder.visibleProperty().bind(convertKindDisassemble.selectedProperty());
        doTookPages4by4.disableProperty().bind(doNotReorderPages.selectedProperty());
        
        PaneUtils.setVBoxPosition(convertKind, 0, 0, new Insets(0, 0, 10, 0));
        PaneUtils.setVBoxPosition(doNotReorderPages, 0, 0, 2.5, 0);
        PaneUtils.setVBoxPosition(doTookPages4by4, 0, 0, 2.5, 0);
        PaneUtils.setVBoxPosition(doReverseOrder, 0, 0, 2.5, 0);
        PaneUtils.setVBoxPosition(doCopyOriginal, 0, 0, 2.5, 0);
        
        
        root.setSpacing(5);
        root.getChildren().addAll(convertKind, generateInfo(TR.tr("options.title"), false), doNotReorderPages, doTookPages4by4, doCopyOriginal, doReverseOrder);
        
        Button cancel = new Button(TR.tr("actions.cancel"));
        cancel.setOnAction(event -> close());
        
        setButtons(cancel, convert);
        updateStatus();
        
        convert.setOnAction((e) -> {
            if(MainWindow.mainScreen.hasDocument(true) && MainWindow.mainScreen.document.save(false) && Edition.isSave()){
                try{
                    MainWindow.mainScreen.getUndoEngine().lock();
                    new BookletEngine(convertKindMake.isSelected(), !doNotReorderPages.isSelected(), doTookPages4by4.isSelected(), doReverseOrder.isSelected(), doCopyOriginal.isSelected() ? copyName : null).convert(MainWindow.mainScreen.document);
                    close();
                }catch(IOException ex){
                    Log.eAlerted(ex);
                    MainWindow.mainScreen.document.pdfPagesRender.resumeRendering();
                }finally{
                    Platform.runLater(MainWindow.mainScreen.getUndoEngine()::unlock);
                }
            }
        });
    }
    @Override
    public void afterShown(){
    }
    
    private void updateStatus(){
        clearInfoBox();
        convert.setDisable(false);
        
        
        if(convertKindMake.isSelected()){
            if(MainWindow.mainScreen.document.numberOfPages % 4 != 0 && !doNotReorderPages.isSelected()){
                updateInfoBox(AlertIconType.ERROR, TR.tr("bookletWindow.error.make.multipleOf4"));
                convert.setDisable(true);
            }else if(MainWindow.mainScreen.document.numberOfPages % 2 != 0 && doNotReorderPages.isSelected()){
                updateInfoBox(AlertIconType.ERROR, TR.tr("bookletWindow.error.make.multipleOf2"));
                convert.setDisable(true);
            }
        }else{
            if(MainWindow.mainScreen.document.numberOfPages % 2 != 0 && !doNotReorderPages.isSelected()){
                updateInfoBox(AlertIconType.ERROR, TR.tr("bookletWindow.error.disassemble.multipleOf2"));
                convert.setDisable(true);
            }
        }
    }
    
}
