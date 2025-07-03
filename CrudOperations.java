import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
public class CrudOperations extends Application {    
    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:xe";
    private static final String DB_USER = "system"; 
    private static final String DB_PASS = "rupa"; 
    private Connection conn; 
    private TreeView<String> treeView; 
    private VBox mainPane; 
    private TableView<RowData> tableView = new TableView<>(); 
    private ObservableList<RowData> tableData = FXCollections.observableArrayList(); 
    private List<String> currentColumns = null; 
    private String currentTable = null;
    public static class RowData {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final ObservableList<StringProperty> data;
        public RowData(List<String> data) {
            this.data = FXCollections.observableArrayList();
            for (String d : data) {
                this.data.add(new SimpleStringProperty(d));
            }
        }        
        public BooleanProperty selectedProperty() {
            return selected;
        }        
        public boolean isSelected() {
            return selected.get();
        }        
        public void setSelected(boolean val) {
            selected.set(val);
        }
        public ObservableList<StringProperty> getData() {
            return data;
        }
    }
    @Override
    public void start(Stage primaryStage) {
        if (!connectDB()) {
            showAlert(Alert.AlertType.ERROR, "Database Connection Failed", "Failed to connect to the Oracle database. Please verify the credentials, DB URL, and ensure the database server is running.");
            return; 
        }        
        treeView = new TreeView<>();
        TreeItem<String> root = new TreeItem<>("Operations");
        root.setExpanded(true);        
        String[] ops = {"Create", "Insert", "Update", "Delete", "Drop", "Truncate", "Select"};
        for (String op : ops) {
            root.getChildren().add(new TreeItem<>(op));
        }
        treeView.setRoot(root);
        treeView.setPrefWidth(160);         
        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item); 
                if (!empty && item != null) {                    
                    switch (item) {
                        case "Create":
                            setTextFill(Color.DARKGREEN);
                            break;
                        case "Insert":
                            setTextFill(Color.DARKBLUE);
                            break;
                        case "Update":
                            setTextFill(Color.ORANGE);
                            break;
                        case "Delete":
                            setTextFill(Color.RED);
                            break;
                        case "Drop":
                            setTextFill(Color.DARKRED);
                            break;
                        case "Truncate":
                            setTextFill(Color.PURPLE);
                            break;
                        case "Select":
                            setTextFill(Color.DARKCYAN);
                            break;
                        default:
                            setTextFill(Color.BLACK); 
                    }
                } else {
                    setTextFill(Color.BLACK); 
                }
            }
        });        
        mainPane = new VBox(12); 
        mainPane.setPadding(new Insets(12)); 
        mainPane.getChildren().add(new Label("Select an operation from the left to begin."));        
        HBox rootLayout = new HBox(treeView, mainPane);
        rootLayout.setSpacing(10);
        rootLayout.setPadding(new Insets(10));         
        Scene scene = new Scene(rootLayout, 1000, 650);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Oracle DB CRUD Operations with JavaFX");
        primaryStage.show();
        tableView.setEditable(true);        
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.getValue() == null) {
                return;
            }            
            mainPane.getChildren().clear();
            currentColumns = null;
            currentTable = null;
            tableView.getColumns().clear();
            tableView.getItems().clear();
            tableData.clear();            
            switch (newVal.getValue()) {
                case "Create":
                    showCreateTableUI();
                    break;
                case "Insert":
                    showInsertUI();
                    break;
                case "Update":
                    showUpdateUI();
                    break;
                case "Delete":
                    showDeleteUI();
                    break;
                case "Drop":
                    showDropUI();
                    break;
                case "Truncate":
                    showTruncateUI();
                    break;
                case "Select":
                    showSelectUI();
                    break;
                default:
                    mainPane.getChildren().add(new Label("Select an operation from the left."));
            }
        });
    }
    @Override
    public void stop() {        
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException ex) {
            System.err.println("Error closing database connection: " + ex.getMessage());
        }
    }    
    private boolean connectDB() {
        try {            
            Class.forName("oracle.jdbc.driver.OracleDriver");            
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("Connected to Oracle DB successfully.");
            return true;
        } catch (ClassNotFoundException ex) {
            System.err.println("Oracle JDBC Driver not found. Make sure ojdbcX.jar is in your classpath.");
            ex.printStackTrace();
            return false;
        } catch (SQLException ex) {
            System.err.println("SQL Exception during DB connection: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null); 
        alert.setContentText(message);
        alert.showAndWait(); 
    }    
    private void loadTablesInto(ComboBox<String> comboBox) {
        comboBox.getItems().clear(); 
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT table_name FROM user_tables ORDER BY table_name")) {
            while (rs.next()) {
                comboBox.getItems().add(rs.getString("table_name")); 
            }
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Error Loading Tables", "Failed to load tables from the database: " + ex.getMessage());
        }
    }
    private List<String> getColumnsForTable(String table) {
        List<String> colList = new ArrayList<>();
        String sql = "SELECT column_name FROM user_tab_columns WHERE table_name = ? ORDER BY column_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    colList.add(rs.getString("column_name"));
                }
            }
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Error Getting Columns", "Failed to retrieve columns for table '" + table + "': " + ex.getMessage());
            return null; 
        }
        return colList;
    }
    private void showCreateTableUI() {
        Label title = new Label("Create Table");
        title.setStyle("-fx-font-size:18; -fx-font-weight:bold;");
        TextField tblNameField = new TextField();
        tblNameField.setPromptText("Enter Table Name (e.g., EMPLOYEES)");
        TextArea columnsArea = new TextArea();
        columnsArea.setPromptText("Enter columns, one per line (e.g.,\nID NUMBER PRIMARY KEY,\nNAME VARCHAR2(50) NOT NULL,\nAGE NUMBER)");
        columnsArea.setPrefRowCount(6); 
        Button createBtn = new Button("Create Table");
        createBtn.setOnAction(e -> {
            String tname = tblNameField.getText().trim();
            String cols = columnsArea.getText().trim();
            if (tname.isEmpty() || cols.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Input Error", "Table name and column definitions cannot be empty!");
                return;
            }            
            String sql = "CREATE TABLE " + tname + " (" + cols.replace("\n", ",") + ")";
            System.out.println("Executing SQL: " + sql); 
            try (Statement st = conn.createStatement()) {
                st.execute(sql); 
                showAlert(Alert.AlertType.INFORMATION, "Success", "Table '" + tname + "' created successfully.");
                tblNameField.clear(); 
                columnsArea.clear();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Creation Failed", "Failed to create table: " + ex.getMessage());
            }
        });        
        VBox vbox = new VBox(10, title, new Label("Table Name:"), tblNameField,
                new Label("Columns (one per line):"), columnsArea, createBtn);
        mainPane.getChildren().add(vbox);
    }
    private void showInsertUI() {
        Label title = new Label("Insert into Table");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        ComboBox<String> cbTables = new ComboBox<>();
        cbTables.setPromptText("Select Table");
        loadTablesInto(cbTables); 
        VBox inputsBox = new VBox(5); 
        Button btnInsert = new Button("Insert");
        btnInsert.setDisable(true);         
        cbTables.setOnAction(e -> {
            String selected = cbTables.getSelectionModel().getSelectedItem();
            inputsBox.getChildren().clear(); 
            btnInsert.setDisable(true); 
            if (selected != null) {
                currentTable = selected;
                currentColumns = getColumnsForTable(selected); 
                if (currentColumns == null || currentColumns.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "No Columns Found", "Failed to retrieve columns for table '" + selected + "'. It might be empty or an error occurred.");
                    return;
                }                
                for (String col : currentColumns) {
                    TextField tf = new TextField();
                    tf.setPromptText(col + " (Value)"); 
                    inputsBox.getChildren().add(tf);
                }
                btnInsert.setDisable(false); 
            }
        });        
        btnInsert.setOnAction(e -> {
            if (currentTable == null || currentColumns == null) {
                showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select a table first.");
                return;
            }
            List<String> values = new ArrayList<>();
            for (Node node : inputsBox.getChildren()) {
                if (node instanceof TextField) { 
                    TextField tf = (TextField) node;
                    values.add(tf.getText().trim());
                }
            }            
            String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
            String sql = "INSERT INTO " + currentTable + " (" + String.join(",", currentColumns) + ") VALUES (" + placeholders + ")";
            System.out.println("Executing SQL: " + sql + " with values: " + values); 
            try (PreparedStatement ps = conn.prepareStatement(sql)) {                
                for (int i = 0; i < values.size(); i++) {
                    ps.setString(i + 1, values.get(i)); 
                }
                int inserted = ps.executeUpdate(); 
                if (inserted > 0) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Row inserted successfully into '" + currentTable + "'.");                    
                    inputsBox.getChildren().forEach(node -> {
                        if (node instanceof TextField) {
                            ((TextField) node).clear();
                        }
                    });
                } else {
                    showAlert(Alert.AlertType.WARNING, "No Row Inserted", "Insert operation completed, but no rows were affected. Check your inputs.");
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Insert Failed", "Error inserting row: " + ex.getMessage());
            }
        });        
        VBox vbox = new VBox(10, title, new Label("Select Table:"), cbTables,
                new Label("Enter Values:"), inputsBox, btnInsert);
        mainPane.getChildren().add(vbox);
    }
    private void showSelectUI() {
        Label title = new Label("Select Data");
        title.setStyle("-fx-font-size:18; -fx-font-weight:bold;");
        ComboBox<String> tablesCombo = new ComboBox<>();
        tablesCombo.setPromptText("Select Table");
        loadTablesInto(tablesCombo);
        Button loadBtn = new Button("Load Data");
        loadBtn.setDisable(true);        
        tablesCombo.setOnAction(e -> loadBtn.setDisable(tablesCombo.getSelectionModel().getSelectedItem() == null));
        loadBtn.setOnAction(e -> {
            String selected = tablesCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return; 
            currentTable = selected;
            currentColumns = getColumnsForTable(selected); 
            if (currentColumns == null || currentColumns.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Columns Found", "Failed to retrieve columns for table '" + selected + "'. Cannot load data.");
                return;
            }
            loadTableData(selected);
        });        
        VBox vbox = new VBox(10, title, new Label("Select Table:"), tablesCombo, loadBtn, tableView);
        mainPane.getChildren().add(vbox);
    }
    private void loadTableData(String table) {
        tableView.getColumns().clear(); 
        tableData.clear(); 
        for (int i = 0; i < currentColumns.size(); i++) {
            final int index = i; 
            TableColumn<RowData, String> col = new TableColumn<>(currentColumns.get(i));
            col.setCellValueFactory(cd -> cd.getValue().getData().get(index));
            col.setPrefWidth(150); 
            tableView.getColumns().add(col);
        }
        String sql = "SELECT * FROM " + table;
        System.out.println("Executing SQL: " + sql);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                List<String> rowVals = new ArrayList<>();
                for (String c : currentColumns) {
                    rowVals.add(rs.getString(c));
                }
                tableData.add(new RowData(rowVals)); 
            }
            tableView.setItems(tableData); 
            if (tableData.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Data", "Table '" + table + "' is empty.");
            }
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Data Load Failed", "Failed to load data for table '" + table + "': " + ex.getMessage());
        }
    }    
    private void showUpdateUI() {
        Label title = new Label("Update Rows");
        title.setStyle("-fx-font-size:18; -fx-font-weight:bold;");
        ComboBox<String> tablesCombo = new ComboBox<>();
        tablesCombo.setPromptText("Select Table");
        loadTablesInto(tablesCombo); 
        Button loadBtn = new Button("Load Data");
        loadBtn.setDisable(true);
        Button updateBtn = new Button("Update Selected Row");
        updateBtn.setDisable(true); 
        tablesCombo.setOnAction(e -> loadBtn.setDisable(tablesCombo.getSelectionModel().getSelectedItem() == null));
        loadBtn.setOnAction(e -> {
            String selected = tablesCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            currentTable = selected;
            currentColumns = getColumnsForTable(selected);
            if (currentColumns == null || currentColumns.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Columns Found", "Failed to retrieve columns for table '" + selected + "'. Cannot update.");
                return;
            }
            loadTableData(selected); 
            updateBtn.setDisable(false); 
        });        
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateBtn.setDisable(newVal == null);
        });
        updateBtn.setOnAction(e -> {
            RowData selectedRow = tableView.getSelectionModel().getSelectedItem();
            if (selectedRow == null) {
                showAlert(Alert.AlertType.WARNING, "No Row Selected", "Please select a row to update.");
                return;
            }
            showUpdateDialog(selectedRow); 
        });
        VBox vbox = new VBox(10, title, new Label("Select Table:"), tablesCombo, loadBtn, tableView, updateBtn);
        mainPane.getChildren().add(vbox);
    }
    private void showUpdateDialog(RowData row) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Update Row");
        dialog.setHeaderText("Edit values for the selected row:");
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 150, 10, 10));
        List<TextField> fields = new ArrayList<>();
        for (int i = 0; i < currentColumns.size(); i++) {
            Label lbl = new Label(currentColumns.get(i) + ":");
            TextField tf = new TextField(row.getData().get(i).get());
            grid.add(lbl, 0, i); 
            grid.add(tf, 1, i);
            fields.add(tf); 
        }
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                List<String> vals = new ArrayList<>();
                for (TextField tf : fields) {
                    vals.add(tf.getText().trim());
                }
                return vals;
            }
            return null; 
        });
        Optional<List<String>> result = dialog.showAndWait(); 
        result.ifPresent(vals -> {
            try {
                String pkCol = currentColumns.get(0);
                String pkValue = row.getData().get(0).get(); 
                StringBuilder sql = new StringBuilder("UPDATE " + currentTable + " SET ");                
                for (int i = 0; i < currentColumns.size(); i++) {
                    sql.append(currentColumns.get(i)).append(" = ?");
                    if (i < currentColumns.size() - 1) {
                        sql.append(", ");
                    }
                }
                sql.append(" WHERE ").append(pkCol).append(" = ?"); 
                System.out.println("Executing SQL: " + sql.toString()); 
                System.out.println("With values: " + vals + " and PK: " + pkValue);
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int paramIndex = 1;                    
                    for (String val : vals) {
                        ps.setString(paramIndex++, val);
                    }                    
                    ps.setString(paramIndex, pkValue);
                    int updated = ps.executeUpdate(); 
                    if (updated > 0) {
                        showAlert(Alert.AlertType.INFORMATION, "Success", "Row updated successfully.");
                        loadTableData(currentTable); 
                    } else {
                        showAlert(Alert.AlertType.WARNING, "No Row Updated", "Update operation completed, but no rows were affected. Check if the PK value still exists.");
                    }
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Update Failed", "Error updating row: " + ex.getMessage());
            } catch (IndexOutOfBoundsException ex) {                
                showAlert(Alert.AlertType.ERROR, "Update Error", "Primary key column could not be determined. Please ensure the table has columns and the first column is suitable for identification.");
            }
        });
    }
    private void showDeleteUI() {
        Label title = new Label("Delete Rows");
        title.setStyle("-fx-font-size:18; -fx-font-weight:bold;");
        ComboBox<String> tablesCombo = new ComboBox<>();
        tablesCombo.setPromptText("Select Table");
        loadTablesInto(tablesCombo);
        Button loadBtn = new Button("Load Data");
        loadBtn.setDisable(true);
        Button deleteBtn = new Button("Delete Selected Rows");
        deleteBtn.setDisable(true); 
        tablesCombo.setOnAction(e -> loadBtn.setDisable(tablesCombo.getSelectionModel().getSelectedItem() == null));        
        loadBtn.setOnAction(e -> {
            String selected = tablesCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            currentTable = selected;
            currentColumns = getColumnsForTable(selected);
            if (currentColumns == null || currentColumns.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Columns Found", "Failed to retrieve columns for table '" + selected + "'. Cannot delete.");
                return;
            }
            loadTableDataWithCheckboxes(selected); 
            deleteBtn.setDisable(false); 
        });        
        deleteBtn.setOnAction(e -> {
            List<RowData> selectedRows = new ArrayList<>();            
            for (RowData row : tableData) {
                if (row.isSelected()) {
                    selectedRows.add(row);
                }
            }
            if (selectedRows.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Rows Selected", "No rows are selected for deletion. Please select one or more rows.");
                return;
            }            
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Deletion");
            confirm.setHeaderText("Delete Confirmation");
            confirm.setContentText("Are you sure you want to delete " + selectedRows.size() + " selected row(s)? This action cannot be undone.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {                    
                    String pkCol = currentColumns.get(0);
                    String sql = "DELETE FROM " + currentTable + " WHERE " + pkCol + " = ?";
                    System.out.println("Preparing batch delete SQL: " + sql); 
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (RowData rd : selectedRows) {
                            ps.setString(1, rd.getData().get(0).get());
                            ps.addBatch(); 
                        }
                        int[] results = ps.executeBatch(); 
                        int deletedCount = 0;
                        for (int count : results) {
                            if (count > 0) { 
                                deletedCount += count;
                            }
                        }
                        showAlert(Alert.AlertType.INFORMATION, "Deletion Complete", deletedCount + " row(s) deleted successfully.");
                        loadTableDataWithCheckboxes(currentTable); 
                    }
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Deletion Failed", "Error during deletion: " + ex.getMessage());
                } catch (IndexOutOfBoundsException ex) {
                    showAlert(Alert.AlertType.ERROR, "Deletion Error", "Primary key column could not be determined for deletion.");
                }
            }
        });        
        VBox vbox = new VBox(10, title, new Label("Select Table:"), tablesCombo, loadBtn, tableView, deleteBtn);
        mainPane.getChildren().add(vbox);
    }
    private void loadTableDataWithCheckboxes(String table) {
        tableView.getColumns().clear(); 
        tableData.clear();         
        TableColumn<RowData, Boolean> selectColumn = new TableColumn<>("Select");
        selectColumn.setPrefWidth(60);
        selectColumn.setCellValueFactory(param -> param.getValue().selectedProperty());        
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));        
        CheckBox selectAllCheckbox = new CheckBox();
        selectAllCheckbox.setOnAction(event -> {
            boolean selected = selectAllCheckbox.isSelected();
            tableData.forEach(rowData -> rowData.setSelected(selected));
        });
        selectColumn.setGraphic(selectAllCheckbox); 
        tableView.getColumns().add(selectColumn);         
        for (int i = 0; i < currentColumns.size(); i++) {
            final int index = i;
            TableColumn<RowData, String> col = new TableColumn<>(currentColumns.get(i));
            col.setCellValueFactory(cd -> cd.getValue().getData().get(index));
            col.setPrefWidth(150);
            tableView.getColumns().add(col);
        }
        String sql = "SELECT * FROM " + table;
        System.out.println("Executing SQL (with checkboxes): " + sql); 
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                List<String> rowVals = new ArrayList<>();
                for (String c : currentColumns) {
                    rowVals.add(rs.getString(c)); 
                }
                RowData newRow = new RowData(rowVals);
                newRow.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectAllCheckbox(selectAllCheckbox));
                tableData.add(newRow);
            }
            tableView.setItems(tableData); 
            if (tableData.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Data", "Table '" + table + "' is empty.");
            }            
            updateSelectAllCheckbox(selectAllCheckbox);
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Data Load Failed", "Failed to load data for table '" + table + "': " + ex.getMessage());
        }
    }    
    private void updateSelectAllCheckbox(CheckBox selectAllCheckbox) {
        if (tableData.isEmpty()) {
            selectAllCheckbox.setIndeterminate(false);
            selectAllCheckbox.setSelected(false);
            return;
        }        
        long selectedCount = tableData.stream().filter(RowData::isSelected).count();
        if (selectedCount == tableData.size()) {            
            selectAllCheckbox.setIndeterminate(false);
            selectAllCheckbox.setSelected(true);
        } else if (selectedCount == 0) {            
            selectAllCheckbox.setIndeterminate(false);
            selectAllCheckbox.setSelected(false);
        } else {            
            selectAllCheckbox.setIndeterminate(true);
        }
    }    
    private void showDropUI() {
        Label title = new Label("Drop Table");
        title.setStyle("-fx-font-size:18; -fx-font-weight:bold;");
        ComboBox<String> tablesCombo = new ComboBox<>();
        tablesCombo.setPromptText("Select Table to Drop");
        loadTablesInto(tablesCombo); 
        Button dropBtn = new Button("Drop Table");
        dropBtn.setDisable(true); 
        tablesCombo.setOnAction(e -> dropBtn.setDisable(tablesCombo.getSelectionModel().getSelectedItem() == null));
        dropBtn.setOnAction(e -> {
            String selected = tablesCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return; 
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Drop");
            confirm.setHeaderText("Irreversible Action!");
            confirm.setContentText("Are you absolutely sure you want to DROP table '" + selected + "'? This action will permanently delete the table and all its data, and cannot be undone.");
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                try (Statement st = conn.createStatement()) {                    
                    String sql = "DROP TABLE " + selected + " CASCADE CONSTRAINTS";
                    System.out.println("Executing SQL: " + sql); 
                    st.execute(sql);
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Table '" + selected + "' dropped successfully.");
                    tablesCombo.getItems().remove(selected); 
                    dropBtn.setDisable(true); 
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Drop Failed", "Failed to drop table: " + ex.getMessage());
                }
            }
        });        
        VBox vbox = new VBox(10, title, new Label("Select Table:"), tablesCombo, dropBtn);
        mainPane.getChildren().add(vbox);
    }    
    private void showTruncateUI() {
        Label title = new Label("Truncate Table");
        title.setStyle("-fx-font-size:18; -fx-font-weight:bold;");
        ComboBox<String> tablesCombo = new ComboBox<>();
        tablesCombo.setPromptText("Select Table to Truncate");
        loadTablesInto(tablesCombo); 
        Button truncateBtn = new Button("Truncate Table");
        truncateBtn.setDisable(true); 
        tablesCombo.setOnAction(e -> truncateBtn.setDisable(tablesCombo.getSelectionModel().getSelectedItem() == null));
        truncateBtn.setOnAction(e -> {
            String selected = tablesCombo.getSelectionModel().getSelectedItem();
            if (selected == null) return;   
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Truncate");
            confirm.setHeaderText("Irreversible Action!");
            confirm.setContentText("Are you sure you want to TRUNCATE table '" + selected + "'? This will remove all rows permanently and reset the high-water mark, and cannot be undone.");
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                try (Statement st = conn.createStatement()) {
                    String sql = "TRUNCATE TABLE " + selected;
                    System.out.println("Executing SQL: " + sql); 
                    st.execute(sql);
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Table '" + selected + "' truncated successfully.");                    
                    if (currentTable != null && currentTable.equalsIgnoreCase(selected)) {
                        loadTableDataWithCheckboxes(currentTable); 
                    }
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Truncate Failed", "Failed to truncate table: " + ex.getMessage());
                }
            }
        });        
        VBox vbox = new VBox(10, title, new Label("Select Table:"), tablesCombo, truncateBtn);
        mainPane.getChildren().add(vbox);
    }
    public static void main(String[] args) {
        launch(args);
    }
}