/*
 * Copyright (c) 2022. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.interfaces.windows.splitpdf;

import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.panel.sidebar.SideBar;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.dialogs.AlreadyExistDialogManager;
import fr.clementgre.pdf4teachers.utils.dialogs.DialogBuilder;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ErrorAlert;
import fr.clementgre.pdf4teachers.utils.interfaces.CallBack;
import fr.clementgre.pdf4teachers.utils.interfaces.TwoStepListAction;
import fr.clementgre.pdf4teachers.utils.interfaces.TwoStepListInterface;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SplitEngine {
    
    private final SplitWindow splitWindow;
    private final ArrayList<Color> colors = new ArrayList<>();
    
    // Stores the page indice of the first and last page of each section (ordered)
    private ArrayList<Integer> sectionsBounds = new ArrayList<>();
    
    public SplitEngine(SplitWindow splitWindow){
        this.splitWindow = splitWindow;
    }
    
    public void process() throws IOException {
        File out = splitWindow.getOutputDir();
        out.mkdirs();
        
        boolean recursive = sectionsBounds.size() > 2;
    
        AlreadyExistDialogManager alreadyExistDialogManager = new AlreadyExistDialogManager(recursive);
        new TwoStepListAction<>(true, recursive, new TwoStepListInterface<ExportPart, ExportPart>() {
            @Override
            public List<ExportPart> prepare(boolean recursive){
                
                ArrayList<ExportPart> exportParts = new ArrayList<>();
                for(int i = 0; i < sectionsBounds.size(); i+=2){
                    String path = out.getAbsolutePath() + File.separator + splitWindow.getNames()[i/2];
                    if(!path.toLowerCase().endsWith(".pdf")) path += ".pdf";
                    
                    exportParts.add(new ExportPart(new File(path), sectionsBounds.get(i), sectionsBounds.get(i+1)));
                }
                return exportParts;
            }
        
            @Override
            public Map.Entry<ExportPart, Integer> sortData(ExportPart exportPart, boolean recursive){
                
                if(exportPart.output.exists()){ // Check Already Exist
                    AlreadyExistDialogManager.ResultType result = alreadyExistDialogManager.showAndWait(exportPart.output);
                    if(result == AlreadyExistDialogManager.ResultType.SKIP)
                        return Map.entry(exportPart, TwoStepListAction.CODE_SKIP_2); // SKIP
                    else if(result == AlreadyExistDialogManager.ResultType.STOP)
                        return Map.entry(exportPart, TwoStepListAction.CODE_STOP); // STOP
                    else if(result == AlreadyExistDialogManager.ResultType.RENAME)
                        exportPart = new ExportPart(AlreadyExistDialogManager.rename(exportPart.output), exportPart.startIndex, exportPart.endIndex());
                }
            
                return Map.entry(exportPart, TwoStepListAction.CODE_OK);
            }
        
            @Override
            public String getSortedDataName(ExportPart exportPart, boolean recursive){
                return exportPart.output.getName();
            }
        
            @Override
            public TwoStepListAction.ProcessResult completeData(ExportPart exportPart, boolean recursive){
                try{
                    exportPart(exportPart);
                }catch(Exception e){
                    e.printStackTrace();
                    if(PlatformUtils.runAndWait(() -> new ErrorAlert(TR.tr("exportWindow.dialogs.exportError.header", exportPart.output.getName()), e.getMessage(), recursive).execute())){
                        return TwoStepListAction.ProcessResult.STOP;
                    }
                    if(!recursive){
                        return TwoStepListAction.ProcessResult.STOP_WITHOUT_ALERT;
                    }
                    return TwoStepListAction.ProcessResult.SKIPPED;
                }
                return TwoStepListAction.ProcessResult.OK;
            }
        
            @Override
            public void finish(int originSize, int sortedSize, int completedSize, HashMap<Integer, Integer> excludedReasons, boolean recursive){
                splitWindow.close();
                SideBar.selectTab(MainWindow.filesTab);
            
                String header;
                if(completedSize == 0) header = TR.tr("splitEngine.dialogs.completed.header.noDocument");
                else if(completedSize == 1) header = TR.tr("splitEngine.dialogs.completed.header.oneDocument");
                else header = TR.tr("splitEngine.dialogs.completed.header.multipleDocument", completedSize);
            
                String details;
                String alreadyExistText = !excludedReasons.containsKey(TwoStepListAction.CODE_SKIP_2) ? "" : "\n(" + TR.tr("exportWindow.dialogs.completed.ignored.alreadyExisting", excludedReasons.get(2)) + ")";
                details = TR.tr("splitEngine.dialogs.completed.exported", completedSize, originSize) + alreadyExistText;
            
                DialogBuilder.showAlertWithOpenDirButton(TR.tr("actions.export.completedMessage"), header, details, out.getAbsolutePath());
            }
        });
        
    }
    
    private record ExportPart(File output, int startIndex, int endIndex){}
    
    private void exportPart(ExportPart exportPart) throws IOException{
        PDDocument extracted = MainWindow.mainScreen.document.pdfPagesRender.editor.extractPages(exportPart.startIndex, exportPart.endIndex);
        extracted.save(exportPart.output);
        extracted.close();
        Platform.runLater(() -> {
            MainWindow.filesTab.openFiles(new File[]{exportPart.output});
        });
    }
    
    public void updateDetectedPages(CallBack callBack){
        new Thread(() -> {
            for(PageRenderer page : MainWindow.mainScreen.document.getPages()){
                BufferedImage image = MainWindow.mainScreen.document.pdfPagesRender.renderPageBasic(page.getPage(), 12, (int) (12 / page.getRatio()));
                colors.add(averageColor(image));
            }
            final List<Color> uniqueColors = getColorsUnique();
            Platform.runLater(() -> {
                splitWindow.getCustomColors().setAll(uniqueColors);
                callBack.call();
            });
        }, "Detect noticeable pages").start();
    }
    
    private List<Color> getColorsUnique(){
    
        List<Color> colors = new ArrayList<>(this.colors);
        
        // Remove similar colors
        for(int i = 0; i < colors.size(); i++){
            Color color = colors.get(i);
            colors = colors.stream()
                    .filter((match) -> getColorDiff(color, match) > .1 || color == match)
                    .toList();
        }
        
        return colors;
    }
    
    public int countMatchPages(){
        if(splitWindow.selection){
            sectionsBounds.clear();
            List<Integer> selected = MainWindow.mainScreen.document.getSelectedPages().stream().sorted().toList();
            int max = MainWindow.mainScreen.document.totalPages - 1;
            
            if(selected.get(0) != 0) sectionsBounds.add(0);
    
            int lastSelected = -1;
            for(Integer page : selected){
                if(lastSelected != page-1){ // Last page not selected -> end of section
                    sectionsBounds.add(page-1);
                }
                if(!selected.contains(page+1) && page+1 <= max){ // Next page not selected -> start of section
                    sectionsBounds.add(page+1);
                }
                lastSelected = page;
            }
    
            if(selected.get(selected.size()-1) != max) sectionsBounds.add(max);
            
        }else{
            if(colors.isEmpty()) return -1;
    
            Color match = splitWindow.getColor();
            double sensibility = splitWindow.getSensibility();
            sectionsBounds.clear();
            MainWindow.mainScreen.document.clearSelectedPages();
    
            int i = 0;
            boolean doLastPageDetected = true;
            for(Color color : colors){
        
                double diff = getColorDiff(color, match);
                if(diff < sensibility){
                    if(!doLastPageDetected) sectionsBounds.add(i-1); // End of section
                    doLastPageDetected = true;
                    MainWindow.mainScreen.document.addSelectedPage(i);
                }else{
                    if(doLastPageDetected) sectionsBounds.add(i); // Start of section
                    doLastPageDetected = false;
                }
                i++;
            }
            if(!doLastPageDetected) sectionsBounds.add(i-1);
        }
        
        if(sectionsBounds.isEmpty()) sectionsBounds = new ArrayList<>(Arrays.asList(0, colors.size()-1));
        //System.out.println(Arrays.toString(sectionsBounds.stream().map((p) -> p+1).toArray()));
        return sectionsBounds.size()/2;
    }
    
    // Colors difference between 0 and 1
    private double getColorDiff(Color c1, Color c2){
        return (Math.abs(c1.getRed() - c2.getRed()) + Math.abs(c1.getGreen() - c2.getGreen()) + Math.abs(c1.getBlue() - c2.getBlue())) / 3;
    }
    
    
    public static Color averageColor(BufferedImage bi) {
        long sumr = 0, sumg = 0, sumb = 0;
        
        for(int x = 0; x < bi.getWidth(); x++){
            for (int y = 0; y < bi.getHeight(); y++){
                java.awt.Color pixel = new java.awt.Color(bi.getRGB(x, y));
                sumr += pixel.getRed();
                sumg += pixel.getGreen();
                sumb += pixel.getBlue();
            }
        }
        float num = bi.getWidth() * bi.getHeight() * 255; // Switching from range 0-255 to range 0-1
        return Color.color(sumr / num, sumg / num, sumb / num);
    }
}