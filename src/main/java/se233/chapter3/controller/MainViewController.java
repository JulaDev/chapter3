package se233.chapter3.controller;
// 642115009 Julaluck Yeta
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import se233.chapter3.Launcher;
import se233.chapter3.model.FileFreq;
import se233.chapter3.model.PDFdocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class MainViewController {
    LinkedHashMap<String, ArrayList<FileFreq>> uniqueSets;

    @FXML
    private ListView<String> inputListView;
    ArrayList<String> listViewPath = new ArrayList<>();

    @FXML
    private Button startButton;
    @FXML
    public MenuItem fileClose;
    @FXML
    private ListView listView;
    private Scene scene;
    @FXML
    public void initialize() {
        //ex4
        fileClose.setOnAction(event -> {
            Launcher.stage.close();
        });

        inputListView.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            final boolean isAccepted = db.getFiles().get(0).getName().toLowerCase().endsWith(".pdf");
            if (db.hasFiles() && isAccepted) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        inputListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                String filePath;
                int total_files = db.getFiles().size();
                for (int i = 0; i < total_files; i++) {
                    File file = db.getFiles().get(i);
                    filePath = file.getAbsolutePath();

                    listViewPath.add(filePath);
                    inputListView.getItems().add(file.getName());
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        startButton.setOnAction(event -> {
            Parent bgRoot = Launcher.stage.getScene().getRoot();
            Task<Void> processTask = new Task<Void>() {
                @Override
                public Void call() throws IOException {
                    ProgressIndicator pi = new ProgressIndicator();
                    VBox box = new VBox(pi);
                    box.setAlignment(Pos.CENTER);
                    Launcher.stage.getScene().setRoot(box);

                    ExecutorService executor = Executors.newFixedThreadPool(4);
                    final ExecutorCompletionService<Map<String, FileFreq>> completionService = new
                            ExecutorCompletionService<>(executor);

                    List<String> inputListViewItems = listViewPath;
                    int total_files = inputListViewItems.size();
                    Map<String, FileFreq>[] wordMap = new Map[total_files];

                    for (String inputListViewItem : inputListViewItems) {
                        try {
                            PDFdocument p = new PDFdocument(inputListViewItem);
                            completionService.submit(new WordMapPageTask(p));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    for (int i = 0; i < total_files; i++) {
                        try {
                            Future<Map<String, FileFreq>> future = completionService.take();
                            wordMap[i] = future.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    //Exercise 2 but not finish. lol
                    try {
                        WordMapMergeTask merger = new WordMapMergeTask(wordMap);
                        Future<LinkedHashMap<String, ArrayList<FileFreq>>> future = executor.submit(merger);
                        uniqueSets = future.get();

                        uniqueSets = uniqueSets.entrySet()
                                .stream()
                                .collect(Collectors.toMap(map -> {
                                    String addition = " (";
                                    for (int i=0;i< map.getValue().size();i++) {
                                        addition = addition + map.getValue().get(i).getFreq();
                                        if (i == map.getValue().size() - 1) {
                                            addition+=")";
                                        } else {
                                            addition+=",";
                                        }
                                    }
                                    return map.getKey()+addition;
                                }, e -> e.getValue(), (fileFreqs, fileFreqs2) -> fileFreqs, () -> new LinkedHashMap<String, ArrayList<FileFreq>>()));
                        /*for(String word : uniqueSets.keySet()){

                            ArrayList<Integer> wordFreq = new ArrayList<>();
                            for(FileFreq f : uniqueSets.get(word)){

                                wordFreq.add(f.getFreq());
                            }
                            listView.getItems().add(word + ": \n" + wordFreq.toString() ); //ใส่อันนี้เลขข้่งหลังขึ้นแต่ pop up ไม่ขึ้น
                        }*/

                        listView.getItems().addAll(uniqueSets.keySet());//ถ้าใส่อันนี้ pop up ขึ้นแต่เลขข้างหลังไม่ขึ้น

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        executor.shutdown();
                    }
                    return null;
                }
            };
            processTask.setOnSucceeded(e -> {
                Launcher.stage.getScene().setRoot(bgRoot);
            });
            Thread thread = new Thread(processTask);
            thread.setDaemon(true);
            thread.start();
        });
        listView.setOnMouseClicked(event -> {
            ArrayList<FileFreq> listOfLinks = uniqueSets.get(listView.getSelectionModel().getSelectedItem());
            System.out.println(listOfLinks);
            ListView<FileFreq> popupListView = new ListView<>();
            LinkedHashMap<FileFreq,String> lookupTable = new LinkedHashMap<>();

            for (FileFreq listOfLink : listOfLinks) { //คาดว่าต้องแก้บรรทัดนี้
                lookupTable.put(listOfLink, listOfLink.getPath());
                popupListView.getItems().add(listOfLink);
            }
            popupListView.setPrefHeight(popupListView.getItems().size() * 28);
            popupListView.setOnMouseClicked(innerEven -> {
                Launcher.hs.showDocument("file://" + lookupTable.get(popupListView.getSelectionModel().getSelectedItem()));
                popupListView.getScene().getWindow().hide();
            });
            Popup popup = new Popup();
            popup.getContent().add(popupListView);
            popup.show(Launcher.stage);

            //exไหนสักอันที่กด esc ละปิด popup
            popupListView.setOnKeyPressed(e -> {
                if(e.getCode() == KeyCode.ESCAPE){
                    //Launcher.stage.close();
                    popupListView.setVisible(false);

                }
            });
        });

    }
} // End MainViewController class