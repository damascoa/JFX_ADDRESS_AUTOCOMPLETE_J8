
package com.components.metre;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AddressAutocompleteTextField extends TextField {

    private final ObservableList<String> suggestions = FXCollections.observableArrayList();
    public final ListView<String> suggestionList = new ListView<>(suggestions);
    private final Popup popup = new Popup();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(400));
    private final Map<String, JSONObject> mapDadosEnderecos = new HashMap<>();
    private boolean suppressListener = false;
    private Consumer<AddressResult> onAddressSelected;

    private String bairro;
    private String cidade;
    private String estado;
    private String enderecoCompleto;
    private double latitude;
    private double longitude;

    public AddressAutocompleteTextField() {
        configurePopup();
        setupEvents();
        this.setPromptText("Digite o endereço...");
    }

    private void configurePopup() {
        suggestionList.setMaxHeight(120);

        popup.getContent().add(suggestionList);
        popup.setAutoHide(true);
    }

    private void setupEvents() {
        this.textProperty().addListener((obs, oldText, newText) -> {
            System.out.println("suppressListener "+suppressListener);
            debounce.setOnFinished(event -> {
                if (newText.length() > 3) {
                    fetchSuggestions(newText);
                } else {
                    suggestions.clear();
                    popup.hide();
                }
            });
            debounce.playFromStart();
        });

        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!popup.isShowing()) return;

            switch (event.getCode()) {
                case DOWN:
                    suggestionList.requestFocus();
                    suggestionList.getSelectionModel().selectFirst();
                    event.consume();
                    break;
                case UP:
                    suggestionList.requestFocus();
                    suggestionList.getSelectionModel().selectLast();
                    event.consume();
                    break;
                case ENTER:
                    if (!suggestionList.getSelectionModel().isEmpty()) {
                        applySelection(suggestionList.getSelectionModel().getSelectedItem());
                        event.consume();
                    }
                    break;
                case ESCAPE:
                    popup.hide();
                    break;
            }
        });

        suggestionList.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    String selected = suggestionList.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        applySelection(selected);
                    }
                    break;
                case ESCAPE:
                    popup.hide();
                    this.requestFocus();
                    break;
            }
        });

        suggestionList.setOnMouseClicked(event -> {
            String selected = suggestionList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                applySelection(selected);
            }
        });

        suggestionList.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-padding: 8px; -fx-border-color: #ddd; -fx-border-width: 0 0 1px 0;");
                }
            }
        });

    }

    private void showPopup() {
        if (this.getScene() == null) return;

        Bounds bounds = this.localToScreen(this.getBoundsInLocal());
        suggestionList.setPrefWidth(this.getWidth());
        popup.show(this, bounds.getMinX(), bounds.getMaxY());
    }

    private void applySelection(String selected) {
        this.setText(selected);
        this.positionCaret(selected.length());
        popup.hide();


        JSONObject dados = mapDadosEnderecos.get(selected);
        if (dados != null) {
            this.bairro = dados.optString("bairro");
            this.cidade = dados.optString("cidade");
            this.estado = dados.optString("estado");
            this.enderecoCompleto = dados.optString("endereco");
            this.latitude = dados.optDouble("latitude");
            this.longitude = dados.optDouble("longitude");
        }
    }

    private void fetchSuggestions(String query) {
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String urlStr = "https://photon.komoot.io/api/?q=" + encoded + "&limit=5";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(result.toString());
                JSONArray features = json.getJSONArray("features");

                ObservableList<String> resultSuggestions = FXCollections.observableArrayList();
                mapDadosEnderecos.clear();

                for (int i = 0; i < features.length(); i++) {
                    JSONObject feature = features.getJSONObject(i);
                    JSONObject prop = feature.getJSONObject("properties");
                    JSONObject geometry = feature.getJSONObject("geometry");

                    String name = prop.optString("name", "");

                    String city = prop.optString("city", prop.optString("town", ""));
                    String suburb = prop.optString("district", "");
                    String state = prop.optString("state", prop.optString("statecode", ""));

                    double lat = geometry.getJSONArray("coordinates").getDouble(1);
                    double lon = geometry.getJSONArray("coordinates").getDouble(0);

                    String fullAddress = String.join(", ", name, suburb, city, state)
                            .replaceAll(", ,", ",")
                            .replaceAll(", $", "");

                    resultSuggestions.add(fullAddress);

                    JSONObject dados = new JSONObject();
                    dados.put("bairro", suburb);
                    dados.put("cidade", city);
                    dados.put("estado", state);
                    dados.put("endereco", name);
                    dados.put("latitude", lat);
                    dados.put("longitude", lon);
                    mapDadosEnderecos.put(fullAddress, dados);
                }

                Platform.runLater(() -> {
                    suggestions.setAll(resultSuggestions);
                    if (!resultSuggestions.isEmpty()) {
                        showPopup();
                    } else {
                        popup.hide();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void setOnAddressSelected(Consumer<AddressResult> listener) {
        this.onAddressSelected = listener;
        if (onAddressSelected != null) {
            onAddressSelected.accept(new AddressResult(
                    this.bairro,
                    this.cidade,
                    this.estado,
                    this.enderecoCompleto,
                    this.latitude,
                    this.longitude
            ));
        }

    }

    public String getBairro() {
        return bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public String getEstado() {
        return estado;
    }

    public String getEnderecoCompleto() {
        return enderecoCompleto;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getSelectedAddress() {
        return this.getText();
    }
}
