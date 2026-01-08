module com.example.typeshii {
    requires javafx.controls;
    requires javafx.fxml;

    opens typeshi to javafx.fxml;
    exports typeshi;
}