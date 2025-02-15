/*
 * Copyright (c) 2020-2025. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.document.render.display;

import fr.clementgre.pdf4teachers.Main;
import fr.clementgre.pdf4teachers.document.Document;
import fr.clementgre.pdf4teachers.document.editions.Edition;
import fr.clementgre.pdf4teachers.document.editions.elements.*;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.MoveUndoAction;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.ResizeUndoAction;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.UType;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.pages.PageAddRemoveUndoAction;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.pages.PageMoveUndoAction;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.pages.PageRotateUndoAction;
import fr.clementgre.pdf4teachers.document.editions.undoEngine.pages.PagesCropUndoAction;
import fr.clementgre.pdf4teachers.document.render.convert.ConvertWindow;
import fr.clementgre.pdf4teachers.document.render.convert.ConvertedFile;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.utils.FilesUtils;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.dialogs.AlreadyExistDialogManager;
import fr.clementgre.pdf4teachers.utils.dialogs.DialogBuilder;
import fr.clementgre.pdf4teachers.utils.dialogs.FilesChooserManager;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ComboBoxDialog;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ErrorAlert;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.WrongAlert;
import fr.clementgre.pdf4teachers.utils.interfaces.TwoStepListAction;
import fr.clementgre.pdf4teachers.utils.interfaces.TwoStepListInterface;
import fr.clementgre.pdf4teachers.utils.objects.PositionDimensions;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCacheImpl;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.PageExtractor;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class PDFPagesEditor {
    
    private final PDFPagesRender renderer;
    private boolean edited;
    
    public PDFPagesEditor(PDFPagesRender renderer){
        this.renderer = renderer;
    }
    
    public void saveEditsIfNeeded(){
        if(edited) saveEdits();
    }
    public void saveEdits(){
        getDocument().setAllSecurityToBeRemoved(true);
        renderer.saveDocumentTo(renderer.getFile());
        edited = false;
    }
    
    // ascendPage and descendPage are registering an UndoAction,
    // but it is not the case of the others moving functions.
    public void ascendPage(PageRenderer page){
        MainWindow.mainScreen.registerNewAction(new PageMoveUndoAction(UType.PAGE, page, page.getPage()));
        movePage(page, -1);
    }
    public void descendPage(PageRenderer page){
        MainWindow.mainScreen.registerNewAction(new PageMoveUndoAction(UType.PAGE, page, page.getPage()));
        movePage(page, 1);
    }
    public void movePage(PageRenderer page, int pagesToPass){
        assert pagesToPass != 0 : "You can't move a page with pagesToPass = 0, this means to not move the page.";
        movePageByIndex(page, page.getPage() + pagesToPass);
    }
    // When the crop box is bigger than the media box, the media box is increased.
    private static @NotNull PDRectangle correctMediaBoxForCropBox(PDRectangle cropBox, PDRectangle mediaBox){
        float leftX = Math.min(cropBox.getLowerLeftX(), mediaBox.getLowerLeftX());
        float leftY = Math.min(cropBox.getLowerLeftY(), mediaBox.getLowerLeftY());
        float rightX = Math.max(cropBox.getUpperRightX(), mediaBox.getUpperRightX());
        float rightY = Math.max(cropBox.getUpperRightY(), mediaBox.getUpperRightY());
        return new PDRectangle(leftX, leftY, rightX - leftX, rightY - leftY);
    }
    
    public void rotatePage(PageRenderer page, boolean right, UType uType, boolean animated){
        int angle = right ? 90 : -90;
        page.quitVectorEditMode();
        getDocument().getPage(page.getPage()).setRotation(getDocument().getPage(page.getPage()).getRotation() + angle);
        edited = true;
        
        MainWindow.mainScreen.registerNewAction(new PageRotateUndoAction(uType, page, right));
        
        if(!animated){
            // If grid mode, one need to update all the pages.
            if(MainWindow.mainScreen.isGridView()) MainWindow.mainScreen.document.updatePagesPosition();
            else page.updatePosition(-1, true);
            
            page.updateRender();
        }else{
            Timeline timeline = new Timeline(60);
            timeline.getKeyFrames().clear();
            timeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(200), new KeyValue(page.rotateProperty(), angle))
            );
            timeline.play();
            
            AtomicBoolean timelineFinished = new AtomicBoolean(false);
            AtomicBoolean renderFinished = new AtomicBoolean(false);
            // The last event to be called will call endRotateAnimation()
            timeline.setOnFinished((e) -> {
                timelineFinished.set(true);
                if(renderFinished.get()) endRotateAnimation(page, timeline);
            });
            page.updateRenderAsync(() -> {
                renderFinished.set(true);
                if(timelineFinished.get()) endRotateAnimation(page, timeline);
            }, false);
        }
    }
    private void endRotateAnimation(PageRenderer page, Timeline timeline){
        timeline.setOnFinished(null);
        timeline.stop();
        page.setRotate(0);
        // If grid mode, one need to update all the pages.
        if(MainWindow.mainScreen.isGridView()) MainWindow.mainScreen.document.updatePagesPosition();
        else page.updatePosition(-1, true);
    }
    public void movePageByIndex(PageRenderer page, int index){
        if(page.getPage() == index) return;
        List<PageRenderer> savedSelectedPages = saveSelectedPages();
        
        page.quitVectorEditMode();
        PDPage docPage = getDocument().getPage(page.getPage());
        
        getDocument().removePage(docPage);
        addDocumentPage(index, docPage);
        
        Document document = MainWindow.mainScreen.document;
        
        // move page
        document.getPages().remove(page);
        document.getPages().add(index, page);
        
        // Update pages of all pages
        for(int i = 0; i < document.numberOfPages; i++) document.getPage(i).setPage(i);
        
        // update coordinates of the pages
        document.updatePagesPosition();
        
        // Update selection
        restoreSelectedPages(savedSelectedPages);
        document.setLastSelectedPage(index);
        
        // update current page
        document.setCurrentPage(index);
    }
    public void deletePage(PageRenderer page){
        
        page.quitVectorEditMode();
        if(MainWindow.mainScreen.document.save(true) && Edition.isSave()){
            
            List<PageRenderer> savedSelectedPages = saveSelectedPages();
            MainWindow.mainScreen.registerNewAction(new PageAddRemoveUndoAction(UType.PAGE, page.getPage(), page, getDocument().getPage(page.getPage()), true));
            
            deletePageUtil(page);
            restoreSelectedPages(savedSelectedPages);
        }
    }
    public void deletePageUtil(PageRenderer page){
        
        page.quitVectorEditMode();
        getDocument().removePage(page.getPage());
        edited = true;
        
        int pageNumber = page.getPage();
        
        // remove page elements
        while(!page.getElements().isEmpty()){
            if(page.getElements().getFirst() instanceof GradeElement grade){
                grade.setValue(-1);
                grade.switchPage(pageNumber == 0 ? 1 : pageNumber - 1);
            }else if(page.getElements().getFirst() instanceof SkillTableElement skillTable){
                skillTable.switchPage(pageNumber == 0 ? 1 : pageNumber - 1);
            }else{
                page.getElements().getFirst().delete(true, UType.NO_UNDO);
            }
        }
        Document document = MainWindow.mainScreen.document;
        // remove page
        page.remove();
        document.numberOfPages--;
        document.getPages().remove(pageNumber);
        MainWindow.mainScreen.pane.getChildren().remove(page);
        
        // Update pages of all pages
        for(int i = 0; i < document.numberOfPages; i++) document.getPage(i).setPage(i);
        
        // update coordinates of the pages
        document.updatePagesPosition();
        
        // update current page
        document.setCurrentPage(document.numberOfPages == pageNumber ? pageNumber - 1 : pageNumber);
        
        Edition.setUnsave("DeletePage");
        document.edition.save(false);
    }
    public void deleteSelectedPages(){
        if(MainWindow.mainScreen.document.save(true) && Edition.isSave()){
            
            List<PageRenderer> savedSelectedPages = saveSelectedPages();
            MainWindow.mainScreen.document.clearSelectedPages();
            int i = 0;
            
            for(PageRenderer page : savedSelectedPages){
                if(MainWindow.mainScreen.document.getPages().size() == 1) return;
                
                if(i == 0)
                    MainWindow.mainScreen.registerNewAction(
                            new PageAddRemoveUndoAction(UType.PAGE, page.getPage(), page, getDocument().getPage(page.getPage()), true));
                else
                    MainWindow.mainScreen.registerNewAction(
                            new PageAddRemoveUndoAction(UType.PAGE_NO_COUNT_BEFORE, page.getPage(), page, getDocument().getPage(page.getPage()), true));
                i++;
                
                page.quitVectorEditMode();
                deletePageUtil(page);
            }
            
        }
    }
    public void addPage(PDPage docPage, int index){
        MainWindow.mainScreen.registerNewAction(new PageAddRemoveUndoAction(UType.PAGE, index, null, docPage, false));
        List<PageRenderer> savedSelectedPages = saveSelectedPages();
        
        PageRenderer page = new PageRenderer(index);
        
        addDocumentPage(index, docPage);
        
        Document document = MainWindow.mainScreen.document;
        
        // add page
        document.getPages().add(index, page);
        MainWindow.mainScreen.addPage(page);
        document.numberOfPages++;
        
        // Update pages of all pages
        for(int i = 0; i < document.numberOfPages; i++) document.getPage(i).setPage(i);
        
        // update coordinates of the pages
        document.getPage(0).updatePosition(PageRenderer.getPageMargin(), true);
        document.updateShowsStatus();
        
        // update current page
        document.setCurrentPage(index);
        
        // Update selection
        restoreSelectedPages(savedSelectedPages);
        document.addSelectedPage(index);
        
        page.removeRender();
        Platform.runLater(page::updateRender);
    }
    public void newBlankPage(int originalPage, int index){
        addPage(new PDPage(MainWindow.mainScreen.document.pdfPagesRender.getPageRotatedCropBox(originalPage)), index);
    }
    
    public void newPdfPage(int index){
        
        final FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(TR.tr("dialog.file.extensionType.PDF"), "*.pdf"));
        chooser.setTitle(TR.tr("dialog.file.selectFile.title"));
        chooser.setInitialDirectory((new File(MainWindow.userData.lastOpenDir).exists() ? new File(MainWindow.userData.lastOpenDir) : new File(System.getProperty("user.home"))));
        
        File file = chooser.showOpenDialog(Main.window);
        if(file != null){
            if(file.getParentFile().exists()) MainWindow.userData.lastOpenDir = file.getParentFile().getAbsolutePath();
            try{
                PDDocument fileDoc = Loader.loadPDF(new RandomAccessReadBufferedFile(file));
                addPdfDocument(fileDoc, index);
            }catch(IOException e){
                Log.eNotified(e);
            }
        }
        
    }
    public void newConvertPage(int originalPage, int index){
        new ConvertWindow(MainWindow.mainScreen.document.pdfPagesRender.getPageRotatedCropBox(originalPage), (convertedFiles) -> {
            if(convertedFiles.isEmpty()) return;
            ConvertedFile file = convertedFiles.getFirst();
            addPdfDocument(file.document, index);
        });
    }
    public void addPdfDocument(PDDocument toAddDoc, int index){
        Document document = MainWindow.mainScreen.document;
        document.clearSelectedPages();
        
        PDFMergerUtility merger = new PDFMergerUtility();
        
        int addedPages = toAddDoc.getNumberOfPages();
        try{
            merger.appendDocument(getDocument(), toAddDoc);
            merger.mergeDocuments(RandomAccessStreamCacheImpl::new);
        }catch(IOException e){
            Log.eNotified(e);
        }
        
        edited = true;
        
        for(int j = 0; j < addedPages; j++){
            PageRenderer page = new PageRenderer(index);
            
            moveDocumentPage(getDocument().getNumberOfPages() - 1, index);
            
            // add page
            document.getPages().add(index, page);
            MainWindow.mainScreen.addPage(page);
            document.numberOfPages++;
            
            // Update pages of all pages
            for(int k = 0; k < document.numberOfPages; k++) document.getPage(k).setPage(k);
        }
        
        MainWindow.mainScreen.registerNewAction(new PageAddRemoveUndoAction(UType.PAGE, index, null, getDocument().getPage(index), false));
        document.addSelectedPage(index);
        // For each page, the index is the same because when undoing, each page will be removed with the same index.
        for(int j = index + 1; j < index + addedPages; j++){
            MainWindow.mainScreen.registerNewAction(new PageAddRemoveUndoAction(UType.PAGE_NO_COUNT_BEFORE, index, null, getDocument().getPage(j), false));
            document.addSelectedPage(j);
        }
        
        try{
            toAddDoc.close();
        }catch(IOException e){
            Log.eNotified(e);
        }
        
        // update coordinates of the pages
        document.getPage(0).updatePosition(PageRenderer.getPageMargin(), true);
        document.updateShowsStatus();
        
        // update current page
        document.setCurrentPage(index);
    }
    public boolean setPageMargin(int pageNumber, float marginTop, float marginRight, float marginBottom, float marginLeft,
                              boolean updateUI, boolean absolute, UType uType){
        PDPage page = getDocument().getPage(pageNumber);
        PDRectangle oldMediaBox = page.getCropBox();
        PDRectangle oldCropBox = page.getCropBox();
        int rotation = page.getRotation();
        
        if(!absolute){
            float width = oldCropBox.getWidth();
            if(rotation == 90 || rotation == 270) width = oldCropBox.getHeight();
            
            marginTop = marginTop * width / 100f;
            marginRight = marginRight * width / 100f;
            marginBottom = marginBottom * width / 100f;
            marginLeft = marginLeft * width / 100f;
        }
        
        boolean excessiveCrop;
        if(rotation == 90 || rotation == 270){
            excessiveCrop = marginLeft + marginRight <= -oldCropBox.getHeight() || marginBottom + marginTop <= -oldCropBox.getWidth();
        }else{
            excessiveCrop = marginBottom + marginTop <= -oldCropBox.getHeight() || marginLeft + marginRight <= -oldCropBox.getWidth();
        }
        
        if(excessiveCrop){
            new WrongAlert(TR.tr("pageCropping.tooMuchCrop.title"), TR.tr("pageCropping.tooMuchCrop.description"), false).show();
            Document document = MainWindow.mainScreen.document;
            document.getPage(0).updatePosition(PageRenderer.getPageMargin(), true);
            document.updateShowsStatus();
            document.updateBackgrounds();
            return false;
        }
        
        PDRectangle newCropBox = switch(rotation){
            case 90 -> new PDRectangle(
                    oldCropBox.getLowerLeftX() - marginTop,
                    oldCropBox.getLowerLeftY() - marginLeft,
                    Math.max(5, oldCropBox.getWidth() + marginTop + marginBottom),
                    Math.max(5, oldCropBox.getHeight() + marginLeft + marginRight)
            );
            case 180 -> new PDRectangle(
                    oldCropBox.getLowerLeftX() - marginRight,
                    oldCropBox.getLowerLeftY() - marginTop,
                    Math.max(5, oldCropBox.getWidth() + marginLeft + marginRight),
                    Math.max(5, oldCropBox.getHeight() + marginTop + marginBottom)
            );
            case 270 -> new PDRectangle(
                    oldCropBox.getLowerLeftX() - marginBottom,
                    oldCropBox.getLowerLeftY() - marginRight,
                    Math.max(5, oldCropBox.getWidth() + marginTop + marginBottom),
                    Math.max(5, oldCropBox.getHeight() + marginLeft + marginRight)
            );
            default -> new PDRectangle(
                    oldCropBox.getLowerLeftX() - marginLeft,
                    oldCropBox.getLowerLeftY() - marginBottom,
                    Math.max(5, oldCropBox.getWidth() + marginLeft + marginRight),
                    Math.max(5, oldCropBox.getHeight() + marginTop + marginBottom)
            );
        };
        
        MainWindow.mainScreen.registerNewAction(new PagesCropUndoAction(uType,
                new WeakReference<>(MainWindow.mainScreen.document.getPage(pageNumber)), oldCropBox, oldMediaBox));
        
        // Moving elements
        double newWidth = rotation == 90 || rotation == 270 ? newCropBox.getHeight() : newCropBox.getWidth();
        double newHeight = rotation == 90 || rotation == 270 ? newCropBox.getWidth() : newCropBox.getHeight();
        double oldWidth = rotation == 90 || rotation == 270 ? oldCropBox.getHeight() : oldCropBox.getWidth();
        double oldHeight = rotation == 90 || rotation == 270 ? oldCropBox.getWidth() : oldCropBox.getHeight();
        
        MainWindow.mainScreen.getUndoEngine().setOverrideUndoType(UType.PAGE_NO_COUNT_BEFORE);
        try{
            // We can't edit a text element font size if it is selected: Unselecting current element
            MainWindow.mainScreen.setSelected(null);
            for(Element el : MainWindow.mainScreen.document.getPage(pageNumber).getElements()){
                MainWindow.mainScreen.registerNewAction(new MoveUndoAction(UType.PAGE_NO_COUNT_BEFORE, el));
                
                // New element coordinates in Page user space
                double newX = marginLeft + el.getRealX() * oldWidth / Element.GRID_WIDTH;
                double newY = marginTop + el.getRealY() * oldHeight / Element.GRID_HEIGHT;
                double hScaleFactor = newWidth / oldWidth;
                double vScaleFactor = newHeight / oldHeight;
                
                el.setRealX((int) (newX * Element.GRID_WIDTH / newWidth));
                el.setRealY((int) (newY * Element.GRID_HEIGHT / newHeight));
                if(el instanceof GraphicElement ge){
                    MainWindow.mainScreen.registerNewAction(new ResizeUndoAction(UType.PAGE_NO_COUNT_BEFORE, ge));
                    ge.setRealWidth((int) (ge.getRealWidth() / hScaleFactor));
                    ge.setRealHeight((int) (ge.getRealHeight() / vScaleFactor));
                }
                
                if(el instanceof TextElement te){
                    // Undo action automatically added in font listener
                    te.setFont(new Font(te.getFont().getName(), te.getFont().getSize() / hScaleFactor));
                }
                el.checkLocation(false);
            }
            setPageCropBoxAllowingMargin(pageNumber, newCropBox, updateUI);
        }finally{
            MainWindow.mainScreen.getUndoEngine().setOverrideUndoType(null);
        }
        return true;
    }
    public void setPageCropBoxAllowingMargin(int pageNumber, PDRectangle box, boolean updateUI){
        PDRectangle mediaBox = getDocument().getPage(pageNumber).getMediaBox();
        PDPage page = getDocument().getPage(pageNumber);
        
        PDRectangle newMediaBox = correctMediaBoxForCropBox(box, mediaBox);
        
        page.setCropBox(box);
        page.setMediaBox(newMediaBox);
        edited = true;
        
        // update coordinates & rerender pages
        if(updateUI){
            Document document = MainWindow.mainScreen.document;
            document.getPage(0).updatePosition(PageRenderer.getPageMargin(), true);
            document.updateShowsStatus();
            document.updateBackgrounds();
        }
    }
    
    // "UTILS"
    
    public List<PageRenderer> saveSelectedPages(){
        return MainWindow.mainScreen.document.getSelectedPages().stream().map((i) -> MainWindow.mainScreen.document.getPage(i)).toList();
    }
    public void restoreSelectedPages(List<PageRenderer> savedPages){
        MainWindow.mainScreen.document.getSelectedPages().clear();
        for(PageRenderer page : savedPages){
            if(MainWindow.mainScreen.document.getPages().contains(page)) // Check page still exists
                MainWindow.mainScreen.document.getSelectedPages().add(page.getPage());
        }
        MainWindow.mainScreen.document.updateSelectedPages();
    }
    public void addDocumentPage(final int index, final PDPage page){
        PDDocument document = getDocument();
        if(index >= document.getNumberOfPages())
            document.addPage(page);
        else{
            ArrayList<PDPage> pages = new ArrayList<>();
            
            // save pages
            for(int i = 0; i < document.getPages().getCount(); i++){
                if(index == i) pages.add(page);
                pages.add(document.getPage(i));
            }
            // remove pages
            while(document.getPages().getCount() != 0) document.removePage(0);
            
            // add pages
            for(PDPage pageToAdd : pages) document.addPage(pageToAdd);
        }
        edited = true;
    }
    
    private void moveDocumentPage(final int from, final int to){
        PDDocument document = getDocument();
        ArrayList<PDPage> pages = new ArrayList<>();
        
        // save non-from pages
        for(int i = 0; i < document.getPages().getCount(); i++){
            if(i != from) pages.add(document.getPage(i));
        }
        // save from page
        pages.add(to, document.getPages().get(from));
        
        // remove pages
        while(document.getPages().getCount() != 0) document.removePage(0);
        
        // add pages
        for(PDPage pageToAdd : pages) document.addPage(pageToAdd);
        edited = true;
    }
    
    public PDDocument extractPages(List<Integer> indices) throws IOException{
        Splitter splitter = new Splitter();
        
        PDDocument output = null;
        List<PDDocument> documents = splitter.split(getDocument());
        
        PDFMergerUtility merger = new PDFMergerUtility();
        
        
        for(int index : indices){
            if(output == null) output = documents.get(index);
            else{
                merger.appendDocument(output, documents.get(index));
                merger.mergeDocuments(RandomAccessStreamCacheImpl::new);
                documents.get(index).close();
            }
        }
        return output;
    }
    public PDDocument extractPages(int startIndex, int endIndex) throws IOException{
        return new PageExtractor(getDocument(), startIndex + 1, endIndex + 1).extract();
    }
    
    // OTHER
    
    public void capture(int pageIndex, boolean selection, boolean allPages, PositionDimensions dimensions){
        Image pageImage;
        List<Image> images = new ArrayList<>();
        PageRenderer page;
        
        if(selection && MainWindow.mainScreen.document.getSelectedPages().isEmpty()){
            selection = false;
            pageIndex = 0;
        }
        if(allPages){
            page = MainWindow.mainScreen.document.getPage(0);
        }else if(selection){
            page = saveSelectedPages().getFirst();
        }else{
            page = MainWindow.mainScreen.document.getPage(pageIndex);
        }
        page.quitVectorEditMode();
        pageImage = capturePagePreview(page, null);
        images.add(capturePagePreview(page, dimensions));
        
        List<String> definitions = ConvertWindow.definitions;
        definitions.set(0, (pageImage.getWidth() * pageImage.getHeight()) / 1000000d + "Mp (" + TR.tr("document.pageActions.capture.dialog.definitionComboBox.thisDocumentDisplayDefinition") + ")");
        
        ComboBoxDialog<String> alert = new ComboBoxDialog<>(TR.tr("document.pageActions.capture.dialog.title"), TR.tr("document.pageActions.capture.dialog.title"), TR.tr("document.pageActions.capture.dialog.details"));
        alert.setItems(FXCollections.observableList(definitions));
        alert.setSelected(definitions.getFirst());
        
        ImageView graphic = new ImageView(images.getFirst());
        graphic.setPreserveRatio(true);
        graphic.setFitWidth(600);
        graphic.setFitHeight(250);
        VBox.setMargin(graphic, new Insets(10, 0, 10, 10));
        ((VBox) alert.getDialogPane().getContent()).getChildren().add(graphic);
        
        alert.getButtonTypes().clear();
        alert.addDefaultButton(TR.tr("actions.save"));
        alert.addCancelButton(ButtonPosition.CLOSE);
        if(!allPages && !selection) alert.addRightButton(TR.tr("actions.copyClipboard"));
        
        ButtonPosition buttonPos = alert.getShowAndWaitGetButtonPosition(ButtonPosition.CLOSE);
        if(buttonPos == ButtonPosition.CLOSE) return;
        String choosed = alert.getSelected();
        if(choosed != null){
            int definition = (int) (Double.parseDouble(choosed.split("Mp")[0]) * 1000000);
            
            AlreadyExistDialogManager alreadyExistDialogManager = new AlreadyExistDialogManager(allPages || selection);
            boolean finalSelection = selection;
            int finalPageIndex = pageIndex;
            new TwoStepListAction<>(true, allPages || selection, new TwoStepListInterface<Integer, Map.Entry<File, Integer>>() {
                File exportDir;
                
                @Override
                public List<Integer> prepare(boolean recursive){
                    if(allPages){
                        return MainWindow.mainScreen.document.getPages().stream().map(PageRenderer::getPage).collect(Collectors.toList());
                    }else if(finalSelection){
                        return new ArrayList<>(MainWindow.mainScreen.document.getSelectedPages());
                    }else{
                        return Collections.singletonList(finalPageIndex);
                    }
                }
                
                @Override
                public Map.Entry<Map.Entry<File, Integer>, Integer> filterData(Integer pageIndex, boolean recursive){
                    File file;
                    if(!recursive){
                        if(buttonPos == ButtonPosition.DEFAULT){
                            file = FilesChooserManager.showSaveDialog(FilesChooserManager.SyncVar.LAST_GALLERY_OPEN_DIR, MainWindow.mainScreen.document.getFileName() + " (" + (pageIndex + 1) + "-" + MainWindow.mainScreen.document.getPagesNumber() + ").png", TR.tr("dialog.file.extensionType.png"), ".png");
                            if(file == null){
                                return Map.entry(Map.entry(new File(""), pageIndex), TwoStepListAction.CODE_STOP);
                            }
                            exportDir = file.getParentFile();
                        }else{
                            return Map.entry(Map.entry(new File(""), pageIndex), TwoStepListAction.CODE_OK);
                        }
                    }else{
                        if(exportDir == null){
                            exportDir = FilesChooserManager.showDirectoryDialog(FilesChooserManager.SyncVar.LAST_GALLERY_OPEN_DIR);
                            if(exportDir == null)
                                return Map.entry(Map.entry(new File(""), pageIndex), TwoStepListAction.CODE_STOP);
                        }
                        file = new File(exportDir.getAbsolutePath() + File.separator + MainWindow.mainScreen.document.getFileName() + " (" + (pageIndex + 1) + "-" + MainWindow.mainScreen.document.getPagesNumber() + ").png");
                    }
                    if(file.exists() && recursive){
                        AlreadyExistDialogManager.ResultType result = alreadyExistDialogManager.showAndWait(file);
                        if(result == AlreadyExistDialogManager.ResultType.SKIP)
                            return Map.entry(Map.entry(file, pageIndex), TwoStepListAction.CODE_SKIP_1);
                        if(result == AlreadyExistDialogManager.ResultType.STOP)
                            return Map.entry(Map.entry(file, pageIndex), TwoStepListAction.CODE_STOP);
                        if(result == AlreadyExistDialogManager.ResultType.RENAME)
                            file = AlreadyExistDialogManager.rename(file);
                    }
                    return Map.entry(Map.entry(file, pageIndex), TwoStepListAction.CODE_OK);
                }
                
                @Override
                public String getSortedDataName(Map.Entry<File, Integer> data, boolean recursive){
                    return data.getKey().getName();
                }
                
                @Override
                public TwoStepListAction.ProcessResult completeData(Map.Entry<File, Integer> data, boolean recursive){
                    PageRenderer page = MainWindow.mainScreen.document.getPage(data.getValue());
                    try{
                        if(buttonPos == ButtonPosition.DEFAULT){
                            BufferedImage image = capturePage(page, dimensions, definition);
                            
                            try{
                                ImageIO.write(image, "png", data.getKey());
                            }catch(IOException e){
                                Log.e(e);
                                boolean result = PlatformUtils.runAndWait(() -> new ErrorAlert(TR.tr("dialog.file.saveError.header", FilesUtils.getPathReplacingUserHome(data.getKey().toPath())), e.getMessage(), recursive).execute());
                                if(!recursive) return TwoStepListAction.ProcessResult.STOP_WITHOUT_ALERT;
                                if(result) return TwoStepListAction.ProcessResult.STOP;
                                else return TwoStepListAction.ProcessResult.SKIPPED;
                            }
                        }else{ // clipboard copy
                            Image image = capturePageInFXImage(page, dimensions, definition);
                            PlatformUtils.runAndWait(() -> {
                                final Clipboard clipboard = Clipboard.getSystemClipboard();
                                final ClipboardContent content = new ClipboardContent();
                                
                                content.putImage(image);
                                clipboard.setContent(content);
                                return null;
                            });
                        }
                        
                    }catch(Exception e){
                        Log.e(e);
                        boolean result = PlatformUtils.runAndWait(() -> new ErrorAlert(null, e.getMessage(), recursive).execute());
                        if(!recursive) return TwoStepListAction.ProcessResult.STOP_WITHOUT_ALERT;
                        if(result) return TwoStepListAction.ProcessResult.STOP;
                        else return TwoStepListAction.ProcessResult.SKIPPED;
                    }
                    return TwoStepListAction.ProcessResult.OK;
                }
                
                @Override
                public void finish(int originSize, int sortedSize, int completedSize, HashMap<Integer, Integer> excludedReasons, boolean recursive){
                    if(exportDir == null){
                        MainWindow.footerBar.showToast(Color.web("#008e00"), Color.WHITE, TR.tr("messages.copied"));
                        return;
                    }
                    String alreadyExistText = !excludedReasons.containsKey(1) ? "" : "\n(" + TR.tr("document.pageActions.capture.completedDialog.ignored.alreadyExisting", excludedReasons.get(1)) + ")";
                    String details = TR.tr("document.pageActions.capture.completedDialog.exported", completedSize, originSize) + alreadyExistText;
                    
                    DialogBuilder.showAlertWithOpenDirButton(TR.tr("document.pageActions.capture.completedDialog.title"), TR.tr("document.pageActions.capture.completedDialog.header"), details, exportDir);
                    
                }
            });
            
        }
    }
    
    private Image capturePagePreview(PageRenderer page, PositionDimensions dimensions){
        if(!page.hasRenderedImage())
            return SwingFXUtils.toFXImage(capturePage(page, dimensions, 200000), null);
        if(dimensions == null){
            return page.getRenderedImage();
        }else{
            Image image = page.getRenderedImage();
            double factor = image.getHeight() / page.getHeight();
            
            int subX = (int) (dimensions.getX() * factor);
            int subY = (int) (dimensions.getY() * factor);
            int subWidth = (int) (dimensions.getWidth() * factor);
            int subHeight = (int) (dimensions.getHeight() * factor);
            
            return new WritableImage(image.getPixelReader(),
                    subX, subY, (int) (subWidth + subX > image.getWidth() ? image.getWidth() - subX : subWidth), (int) (subHeight + subY > image.getHeight() ? image.getHeight() - subY : subHeight));
        }
    }
    private BufferedImage capturePage(PageRenderer page, PositionDimensions dimensions, int pixels){ // A4 : 594 : 841
        
        int width = (int) (Math.sqrt(pixels) / (841d / 594d));
        int height = (int) (width * (page.getHeight() / page.getWidth()));
        
        BufferedImage image = MainWindow.mainScreen.document.pdfPagesRender.renderPageBasic(page.getPage(), width, height);
        
        if(dimensions == null){
            return image;
        }else{
            double factor = image.getHeight() / page.getHeight();
            int subX = (int) (dimensions.getX() * factor);
            int subY = (int) (dimensions.getY() * factor);
            int subWidth = (int) (dimensions.getWidth() * factor);
            int subHeight = (int) (dimensions.getHeight() * factor);
            return image.getSubimage(subX, subY, subWidth + subX > image.getWidth() ? image.getWidth() - subX - 1 : subWidth, subHeight + subY > image.getHeight() ? image.getHeight() - subY - 1 : subHeight);
        }
    }
    private Image capturePageInFXImage(PageRenderer page, PositionDimensions dimensions, int pixels){ // A4 : 594 : 841
        
        int width = (int) (Math.sqrt(pixels) / (841d / 594d));
        int height = (int) (width * (page.getHeight() / page.getWidth()));
        
        BufferedImage image = MainWindow.mainScreen.document.pdfPagesRender.renderPageBasic(page.getPage(), width, height);
        
        if(dimensions == null){
            return SwingFXUtils.toFXImage(image, null);
        }else{
            Image fxImage = SwingFXUtils.toFXImage(image, null);
            double factor = image.getHeight() / page.getHeight();
            int subX = (int) (dimensions.getX() * factor);
            int subY = (int) (dimensions.getY() * factor);
            int subWidth = (int) (dimensions.getWidth() * factor);
            int subHeight = (int) (dimensions.getHeight() * factor);
            return new WritableImage(fxImage.getPixelReader(),
                    subX, subY, subWidth + subX > image.getWidth() ? image.getWidth() - subX : subWidth, subHeight + subY > image.getHeight() ? image.getHeight() - subY : subHeight);
        }
    }
    
    public int clampAddPageIndex(int index){
        if(index < 0) return 0;
        return Math.min(index, getDocument().getNumberOfPages());
    }
    
    public boolean isEdited(){
        return edited;
    }
    public void markAsEdited(){
        edited = true;
    }
    public PDDocument getDocument(){
        return renderer.getDocument();
    }
    
}
