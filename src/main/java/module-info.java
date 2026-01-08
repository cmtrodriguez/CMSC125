module com.example.typeshii {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.prefs;

    opens typeshi to javafx.fxml;
    exports typeshi;
}