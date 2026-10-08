module panyaza {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires transitive javafx.graphics;

    opens panyaza to javafx.fxml;
    exports panyaza;
}
