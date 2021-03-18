package fr.clementgre.pdf4teachers.panel.sidebar.grades.export;

import fr.clementgre.pdf4teachers.document.editions.elements.GradeElement;
import fr.clementgre.pdf4teachers.document.editions.elements.TextElement;
import fr.clementgre.pdf4teachers.panel.sidebar.grades.GradeRating;
import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.utils.StringUtils;
import fr.clementgre.pdf4teachers.utils.dialog.DialogBuilder;
import javafx.scene.control.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class GradeExportRenderer {

    String text = "";

    public ArrayList<GradeRating> gradeScale;
    ArrayList<ExportFile> files = new ArrayList<>();
    int exportTier;

    int exported = 0;
    boolean mkdirs = true;
    boolean erase = false;

    GradeExportWindow.ExportPane pane;
    public GradeExportRenderer(GradeExportWindow.ExportPane pane){
        this.pane = pane;
        this.exportTier = (int) pane.settingsTiersExportSlider.getValue();

        if(MainWindow.mainScreen.hasDocument(false)) MainWindow.mainScreen.document.save();

    }

    public int start(){
        if(!getFiles()){
            return exported;
        }

        if(pane.type != 1){
            try{

                if(pane.settingsAttributeTotalLine.isSelected()){
                    generateNamesLine(false);
                    generateGradeScaleLine();
                }else{
                    generateNamesLine(true);
                }
                if(pane.settingsAttributeAverageLine.isSelected()){
                    generateMoyLine();
                }
                for(ExportFile file : files){
                    generateStudentLine(file);
                }
                if(!save(null)) return exported;
            }catch(Exception e){
                e.printStackTrace();
                DialogBuilder.showErrorAlert(TR.tr("gradeExportWindow.fatalError.title"), e.getMessage(), false);
                return exported;
            }
        }else{ // SPLIT

            for(ExportFile file : files){
                try{
                    if(pane.settingsAttributeTotalLine.isSelected()){
                        generateNamesLine(false);
                        generateGradeScaleLine();
                    }else{
                        generateNamesLine(true);

                    }
                    generateStudentLine(file);

                    if(!save(file)) return exported;

                }catch(Exception e){
                    e.printStackTrace();
                    boolean result = DialogBuilder.showErrorAlert(TR.tr("gradeExportWindow.error.title", file.file.getName()), e.getMessage(), true);
                    if(result) return exported;
                }
            }

        }
        return exported;
    }

    // GENERATORS

    public void generateNamesLine(boolean includeGradeScale){

        text += TR.tr("gradeExportWindow.csv.titles.parts");

        for(GradeRating rating : gradeScale){
            text += ";" + rating.name + (includeGradeScale ? " /" + rating.total : "");
        }
        text += "\n";
    }
    public void generateGradeScaleLine(){

        text += TR.tr("gradeExportWindow.csv.titles.gradeScale");

        for(GradeRating rating : gradeScale){
            text += ";" + rating.total;
        }
        text += "\n";

    }
    public void generateMoyLine(){

        char x = 'B';
        int startY = pane.settingsAttributeTotalLine.isSelected() ? 4 : 3;
        int endY = startY + files.size()-1;

        text += TR.tr("gradeExportWindow.csv.titles.average");

        for(GradeRating rating : gradeScale){
            text += ";=" + TR.tr("gradeExportWindow.csv.formulas.average.name").toUpperCase() + "(" + x + startY + ":" + x + endY + ")";
            x++;
        }
        text += "\n";
    }
    public void generateStudentLine(ExportFile file){

        if(pane.studentNameSimple != null){
            text += pane.studentNameSimple.getText();
        }else{
            text += StringUtils.removeAfterLastRegex(file.file.getName(), ".pdf").replaceAll(Pattern.quote(pane.studentNameReplace.getText()), pane.studentNameBy.getText());
        }

        for(GradeElement grade : file.grades){
            text += ";" + (grade.getValue() == -1 ? "" : grade.getValue());
        }
        text += "\n";

        if(pane.settingsWithTxtElements.isSelected()){
            generateCommentsLines(file);
        }
    }
    public void generateCommentsLines(ExportFile file){

        text += TR.tr("gradeExportWindow.csv.titles.comments");

        if(file.comments.size() >= 1){
            ArrayList<String> lines = new ArrayList<>();

            file.comments.sort((element1, element2) ->
                    (element2.getPageNumber()-9999 + "" + (element2.getRealY()-9999) + "" + (element2.getRealX()-9999))
                            .compareToIgnoreCase(element1.getPageNumber()-9999 + "" + (element1.getRealY()-9999) + "" + (element1.getRealX()-9999)));

            for(int i = 1 ; i < file.grades.size(); i++){
                GradeElement grade = file.grades.get(i);
                int maxPage = grade.getPageNumber();
                int maxY = grade.getRealY();

                TextElement element = file.comments.size() > 0 ? file.comments.get(0) : null;
                int k = -1;
                while(element != null){

                    if(element.getPageNumber() == maxPage && element.getRealY() < maxY || element.getPageNumber() < maxPage){
                        k++;
                        if(lines.size() > k){
                            lines.set(k, lines.get(k) + ";" + element.getText().replaceAll(Pattern.quote("\n"), " "));
                        }else{
                            lines.add(";" + element.getText().replaceAll(Pattern.quote("\n"), ""));
                        }

                        file.comments.remove(0);
                        element = file.comments.size() > 0 ? file.comments.get(0) : null;
                    }else{
                        element = null;
                    }
                }
                for(k++; k < 20; k++){
                    if(lines.size() > k){
                        lines.set(k, lines.get(k) + ";");
                    }else{
                        lines.add(";");
                    }
                }
            }
            int k = 0;
            for(TextElement element : file.comments){
                if(lines.size() > k){
                    lines.set(k, lines.get(k) + ";" + element.getText().replaceAll(Pattern.quote("\n"), " "));
                }else{
                    lines.add(";" + element.getText().replaceAll(Pattern.quote("\n"), ""));
                }
                k++;
            }

            for(String line : lines){
                text += line + "\n";
            }
        }else text += "\n";
    }

    // OTHERS

    public boolean getFiles(){

        try {
            ExportFile defaultFile = new ExportFile(MainWindow.mainScreen.document.getFile(), exportTier, pane.settingsWithTxtElements.isSelected());
            gradeScale = defaultFile.generateGradeScale();

            if(!(pane.settingsOnlyCompleted.isSelected() && !defaultFile.isCompleted())){
                files.add(defaultFile);
            }

        }catch(Exception e){
            e.printStackTrace();
            DialogBuilder.showErrorAlert(TR.tr("gradeExportWindow.unableToReadEditionError.header", MainWindow.mainScreen.document.getFileName()) + "\n" +
                    TR.tr("gradeExportWindow.unableToReadEditionError.header.sourceDocument"), e.getMessage(), false);
            return false;
        }


        if(pane.type != 2){
            for(File file : MainWindow.filesTab.files.getItems()){
                try{
                    if(MainWindow.mainScreen.document.getFile().equals(file)) continue;
                    if(pane.settingsOnlySameDir.isSelected() && !MainWindow.mainScreen.document.getFile().getParent().equals(file.getParent())) continue;

                    ExportFile exportFile = new ExportFile(file, exportTier, pane.settingsWithTxtElements.isSelected());

                    if(pane.settingsOnlySameGradeScale.isSelected() && !exportFile.isSameGradeScale(gradeScale)) continue;
                    if(pane.settingsOnlyCompleted.isSelected() && !exportFile.isCompleted()) continue;

                    files.add(exportFile);

                }catch(Exception e) {
                    e.printStackTrace();
                    boolean result = DialogBuilder.showErrorAlert(TR.tr("gradeExportWindow.unableToReadEditionError.header", file.getName()) + "\n" +
                            TR.tr("gradeExportWindow.unableToReadEditionError.header.sourceDocument"), e.getMessage(), false);
                    if(result) return false;
                }
            }
        }
        return true;
    }

    public boolean save(ExportFile source) throws IOException {

        String filePath = pane.filePath.getText();
        String fileName;

        if(source != null){ // type = 1 -> Splited export
            fileName = pane.fileNamePrefix.getText() + StringUtils.removeAfterLastRegex(source.file.getName(), ".pdf")
                    .replaceAll(Pattern.quote(pane.fileNameReplace.getText()), pane.fileNameBy.getText()) + pane.fileNameSuffix.getText();
        }else{ // other
            fileName = StringUtils.removeAfterLastRegex(pane.fileNameSimple.getText(), ".csv");
        }

        File file = new File(filePath + File.separator + fileName + ".csv");
        file.getParentFile().mkdirs();

        if(!file.createNewFile()){
            switch(fileAlreadyExist(file)) { // 0: Continue | 1: Cancel | 2: Cancel All | 3: ContinueAll | 4: Rename
                case 1:
                    return true;
                case 2:
                    return false;
                case 4:
                    String tmpName = fileName; int i = 1;
                    while(new File(filePath + File.separator + tmpName + ".csv").exists()){
                        tmpName = fileName + " (" + i + ")";
                        i++;
                    }
                    file = new File(filePath + File.separator + tmpName + ".csv");
                    file.createNewFile();
            }
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));

        writer.write(text);

        writer.flush();
        writer.close();

        exported++;
        text = "";
        return true;
    }

    public int fileAlreadyExist(File file){

        Alert alert = DialogBuilder.getAlert(Alert.AlertType.WARNING, TR.tr("dialog.file.alreadyExist.title"));
        alert.setHeaderText(TR.tr("dialog.file.alreadyExist.header", file.getName()));
        alert.setContentText(TR.tr("dialog.file.alreadyExist.details", file.getParentFile().getAbsolutePath()));

        ButtonType yesButton = new ButtonType(TR.tr("dialog.actionError.overwrite"), ButtonBar.ButtonData.YES);
        ButtonType yesAlwaysButton = new ButtonType(TR.tr("dialog.actionError.overwriteAlways"), ButtonBar.ButtonData.YES);
        ButtonType renameButton = new ButtonType(TR.tr("dialog.actionError.rename"), ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType(TR.tr("dialog.actionError.skip"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType cancelAllButton = new ButtonType(TR.tr("dialog.actionError.stopAll"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yesButton, yesAlwaysButton, renameButton, cancelButton, cancelAllButton);

        Optional<ButtonType> option = alert.showAndWait();
        if(option.get() == cancelAllButton){
            return 2;
        }else if(option.get() == cancelButton){
            return 1;
        }else if(option.get() == yesButton){
            return 0;
        }else if(option.get() == yesAlwaysButton){
            erase = true;
            return 3;
        }else if(option.get() == renameButton){
            return 4;
        }
        return 1;

    }

}
