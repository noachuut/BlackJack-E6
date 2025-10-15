package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;

public class AppFX extends Application {

    // --- État BDD / joueur ---
    private Connection cn;
    private long userId = -1L;
    private long sessionId = -1L;
    private int currentBet = 500;
    private int balanceCached = 0;

    // --- Jeu (logique deck basique, comme ton Swing) ---
    private static class Card {
        final String value; // "A","2"...,"K"
        final String suit;  // "D","H","S","C"
        Card(String v, String s){ value=v; suit=s; }
        int getValue() {
            if ("AJQK".contains(value)) return "A".equals(value) ? 11 : 10;
            return Integer.parseInt(value);
        }
        boolean isAce(){ return "A".equals(value); }
        String path(){ return "/card/" + value + "-" + suit + ".png"; }
        public String toString(){ return value + "-" + suit; }
    }
    private final Random rng = new Random();
    private List<Card> deck;
    private Card hiddenCard;
    private final List<Card> dealer = new ArrayList<>();
    private final List<Card> player = new ArrayList<>();
    private boolean roundSettled = false;
    private String resultMsg = "";

    // --- UI ---
    private Stage stage;
    private Scene loginScene, betScene, gameScene;
    private Canvas gameCanvas;
    private Button btnHit, btnStay, btnNewRound, btnChangeBet;
    private Image tapisImg;

    // rendu cartes
    private static final double CARD_W = 110;
    private static final double CARD_H = 160;
    private static final double CARD_SPACING_FACTOR = 0.35; // 0.35 = cartes bien collées (65% overlap)

    // Éditeur de mise inline
    private HBox betEditor;
    private Spinner<Integer> spBet;
    private Button btnApplyBet, btnCloseBet;


    // ====== LAUNCH ======
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Black Jack");
        stage.setMinWidth(720);
        stage.setMinHeight(480);

        // DB
        openDb();
        ensureSchema();

        // Background image
        try {
            tapisImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/background/tapis.png")));
        } catch (Exception e) {
            tapisImg = null;
        }

        // Scenes
        loginScene = buildLoginScene();
        stage.setScene(loginScene);
        stage.setWidth(600);
        stage.setHeight(600);
        stage.centerOnScreen();
        stage.show();
    }

    // === SCENES ===
    private void drawHand(GraphicsContext g, List<Card> hand, double x0, double y0) {
        double dx = CARD_W * CARD_SPACING_FACTOR;
        // dessiner de la première à la dernière pour que la DERNIÈRE soit “au-dessus”
        for (int i = 0; i < hand.size(); i++) {
            Image im = new Image(Objects.requireNonNull(getClass().getResourceAsStream(hand.get(i).path())));
            double x = x0 + i * dx;
            g.drawImage(im, x, y0, CARD_W, CARD_H);
        }
    }

    private void drawDealer(GraphicsContext g, double x0, double y0, boolean revealed) {
        double dx = CARD_W * CARD_SPACING_FACTOR;
        Image back = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/card/BACK.png")));

        if (!revealed) {
            // 1) cartes visibles du croupier décalées d’un pas
            drawHand(g, dealer, x0 + dx, y0);
            // 2) la carte cachée PAR-DESSUS à gauche
            g.drawImage(back, x0, y0, CARD_W, CARD_H);
        } else {
            // carte cachée révélée + reste des cartes
            Image hid = new Image(Objects.requireNonNull(getClass().getResourceAsStream(hiddenCard.path())));
            g.drawImage(hid, x0, y0, CARD_W, CARD_H);
            drawHand(g, dealer, x0 + dx, y0);
        }
    }

    private Scene buildLoginScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("login-root");

        // ---- HERO (cartes + titre) ----
        VBox hero = new VBox(10);
        hero.setAlignment(Pos.TOP_CENTER);
        hero.setPadding(new Insets(40, 0, 10, 0));

        // Cartes superposées
        StackPane cardsHero = new StackPane();
        cardsHero.setMinHeight(120);
        try {
            ImageView ace = new ImageView(new Image(getClass().getResourceAsStream("/card/A-S.png")));
            ace.setFitHeight(120); ace.setPreserveRatio(true);
            ace.setRotate(-12);
            ace.setTranslateX(-35);
            ace.setEffect(new javafx.scene.effect.DropShadow(20, Color.color(0,0,0,0.4)));

            ImageView jack = new ImageView(new Image(getClass().getResourceAsStream("/card/J-H.png")));
            jack.setFitHeight(130); jack.setPreserveRatio(true);
            jack.setRotate(12);
            jack.setTranslateX(35);
            jack.setEffect(new javafx.scene.effect.DropShadow(20, Color.color(0,0,0,0.45)));

            cardsHero.getChildren().addAll(ace, jack);
        } catch (Exception ignore) { /* si images absentes, on n’affiche pas */ }

        Label title = new Label("BLACK JACK");
        title.getStyleClass().add("login-title");

        hero.getChildren().addAll(cardsHero, title);

        // ---- CARTE FORMULAIRE ----
        VBox card = new VBox(16);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(22, 24, 22, 24));
        card.setMaxWidth(720);
        card.setMinWidth(340);

// champs
        TextField tfEmail = new TextField();
        tfEmail.setPromptText("Email");
        tfEmail.getStyleClass().add("input-cream");

        PasswordField pf = new PasswordField();
        pf.setPromptText("Mot de passe");
        pf.getStyleClass().add("input-cream");

        TextField tfPwdReveal = new TextField();
        tfPwdReveal.setPromptText("Mot de passe");
        tfPwdReveal.getStyleClass().add("input-cream");
        tfPwdReveal.managedProperty().bind(tfPwdReveal.visibleProperty());
        tfPwdReveal.setVisible(false);
        pf.textProperty().bindBidirectional(tfPwdReveal.textProperty());

// --- œil PLUS GRAND (34x34) ---
        ToggleButton eye = new ToggleButton();
        eye.getStyleClass().add("eye-toggle");
        ImageView eyeIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/eye.png")));
        eyeIcon.setFitHeight(22); eyeIcon.setPreserveRatio(true);   // <- icône plus grande
        eye.setGraphic(eyeIcon);
        eye.setPrefSize(34, 34);                                    // <- bouton plus grand
        eye.setMinSize(34, 34);
        eye.selectedProperty().addListener((o, oldV, on) -> {
            tfPwdReveal.setVisible(on);
            pf.setVisible(!on);
        });

// ligne mot de passe
        StackPane pwdStack = new StackPane(pf, tfPwdReveal);
        HBox pwdRow = new HBox(10, pwdStack, eye);
        HBox.setHgrow(pwdStack, Priority.ALWAYS);

// -> la carte ne contient QUE les champs
        card.getChildren().addAll(tfEmail, pwdRow);

// ---- BOUTONS séparés du formulaire ----
        Button btnSignup = new Button("Créer un compte");
        btnSignup.getStyleClass().addAll("btn-primary","btn-soft");

        Button btnLogin = new Button("Se connecter");
        btnLogin.getStyleClass().add("btn-primary");

        HBox actions = new HBox(18, btnSignup, btnLogin);
        actions.getStyleClass().add("btn-row");           // <- pour marges
        actions.setAlignment(Pos.CENTER);

// ---- Message sous la rangée de boutons ----
        Label msg = new Label();
        msg.getStyleClass().add("login-msg");

// ---- conteneur central (hero + carte + boutons + msg) ----
        VBox center = new VBox(20, hero, card, actions, msg);
        center.setAlignment(Pos.TOP_CENTER);
        center.setPadding(new Insets(10, 24, 40, 24));
        root.setCenter(center);

        // responsive : largeur de la carte varie entre 360 et 560 px
        root.sceneProperty().addListener((obs, oldS, sc) -> {
            if (sc != null) {
                card.prefWidthProperty().bind(
                        javafx.beans.binding.Bindings.createDoubleBinding(
                                () -> clamp(sc.getWidth() * 0.6, 360, 560), sc.widthProperty()
                        )
                );
            }
        });

        // actions (ta logique existante)
        btnLogin.setOnAction(ev -> {
            String email = tfEmail.getText().trim();
            String pwd = (pf.isVisible() ? pf.getText() : tfPwdReveal.getText());
            var u = findUserByEmail(email);
            if (u == null) { msg.setText("Utilisateur introuvable."); return; }
            if (!SecurityUtil.checkPwd(pwd, u.hash)) { msg.setText("Mot de passe incorrect."); return; }
            userId = u.id;
            applyDailyCredit(userId);
            refreshBalanceUI();
            betScene = buildBetScene();
            switchScene(betScene);
        });
        btnSignup.setOnAction(ev -> {
            Dialog<Long> dlg = buildSignupDialog();
            dlg.showAndWait().ifPresent(id -> {
                userId = id;
                refreshBalanceUI();
                betScene = buildBetScene();
                switchScene(betScene);
            });
        });

        Scene scn = new Scene(root, 980, 700);
        scn.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        return scn;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }



    private Scene buildBetScene() {


        int balance = getBalance(userId);
        int step=50, min=250, max=Math.max(min, balance);
        Spinner<Integer> sp = new Spinner<>(min, max, Math.min(Math.max(currentBet, min), max), step);
        sp.setEditable(false);

        Button btnStart = new Button("Commencer");
        btnStart.getStyleClass().add("btn-primary");
        Label info = new Label("Choisis ta mise (XPF). Solde: " + balance + " XPF");

        VBox root = new VBox(12, info, sp, btnStart);
        root.getStyleClass().add("app-root");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));


        btnStart.setOnAction(ev -> {
            currentBet = sp.getValue();
            // Démarrer round
            startRound(); // crée deck + mains
            sessionId = startSession(userId);
            placeBet(userId, sessionId, currentBet);
            refreshBalanceUI();

            gameScene = buildGameScene();
            switchScene(gameScene);
            redrawGame();
        });

        Scene sc = new Scene(root, 600, 600);
        sc.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        return sc;
    }

    private Scene buildGameScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        // Canvas jeu
        gameCanvas = new Canvas(600, 540); // zone de jeu
        Pane center = new StackPane(gameCanvas);
        center.setStyle("-fx-background-color: transparent;");
        root.setCenter(center);

        // Barre des boutons
        btnHit = new Button("Tirez");
        btnStay = new Button("Rester");
        btnNewRound = new Button("Nouvelle manche");
        btnChangeBet = new Button("Changer mise");

        btnHit.getStyleClass().add("btn-primary");
        btnStay.getStyleClass().add("btn-primary");
        btnNewRound.getStyleClass().add("btn-primary");
        btnChangeBet.getStyleClass().add("btn-primary");

        btnNewRound.setDisable(true);
        btnChangeBet.setDisable(true); // désactivé pendant la manche

        HBox bar = new HBox(10, btnChangeBet, btnHit, btnStay, btnNewRound);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(8));
        root.setBottom(bar);

        spBet = new Spinner<>();
        spBet.setEditable(true);
        spBet.setPrefWidth(120);

// n'accepter que des chiffres dans l'éditeur
        spBet.getEditor().setTextFormatter(new TextFormatter<>(c -> {
            if (c.getText().matches("\\d*")) return c;
            return null;
        }));

        btnApplyBet = new Button("OK");
        btnApplyBet.getStyleClass().add("btn-primary");

        btnCloseBet = new Button("✕");
        btnCloseBet.getStyleClass().add("btn-close-small");

        Label lbl = new Label("Mise (XPF)");
        lbl.setTextFill(Color.WHITE);

        betEditor = new HBox(8, lbl, spBet, btnApplyBet, btnCloseBet);
        betEditor.setAlignment(Pos.CENTER);
        betEditor.getStyleClass().add("bet-editor");
        betEditor.setVisible(false);          // <— caché au départ

// Empile l'éditeur AU-DESSUS de la barre de boutons
        VBox bottom = new VBox(10, betEditor, new HBox(10, btnChangeBet, btnHit, btnStay, btnNewRound));
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(8));
        root.setBottom(bottom);

        // Actions
        btnHit.setOnAction(e -> {
            // tirer une carte pour joueur
            Card c = deck.remove(deck.size()-1);
            player.add(c);

            // Si le joueur dépasse 21, on clôt immédiatement la manche
            int p = computeTotal(player);
            if (p >= 21) {
                btnHit.setDisable(true);
                btnStay.setDisable(true);
                dealerPlay();       // si 21 après tirage, on passe au croupier
                settleAndFinish();
                redrawGame();
                return;
            }

            redrawGame();
        });

        btnStay.setOnAction(e -> {
            // révéler et tirer croupier
            btnHit.setDisable(true);
            btnStay.setDisable(true);
            dealerPlay();  // tire jusqu'à 17
            settleAndFinish(); // calcule payout (2x/1x/0), règle, fin de manche
            redrawGame();
        });

        btnNewRound.setOnAction(e -> {
            // nouvelle manche : réutilise currentBet, n’ouvre pas d’autre fenêtre
            if (!ensureBetAffordable()) {
                return;
            }
            resultMsg = "";
            roundSettled = false;
            startRound();
            sessionId = startSession(userId);
            placeBet(userId, sessionId, currentBet);
            refreshBalanceUI();

            btnHit.setDisable(false);
            btnStay.setDisable(false);
            btnNewRound.setDisable(true);
            btnChangeBet.setDisable(true);

            redrawGame();
        });

        btnChangeBet.setOnAction(e -> {
            if (betEditor.isVisible()) {
                betEditor.setVisible(false);
                return;
            }
            openBetEditor();  // prépare bornes/min/max et affiche
        });

        Scene sc = new Scene(root, 600, 600);
        sc.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        return sc;
    }

    private void switchScene(Scene sc) {
        stage.setScene(sc);
        stage.centerOnScreen();
    }

    // === LOGIQUE JEU ===
    private static final int BET_STEP = 250;
    private static final int BET_MIN  = 250;

    private void openBetEditor() {
        int balance = getBalance(userId);
        int max = Math.max(BET_MIN, balance);
        int start = Math.min(Math.max(currentBet, BET_MIN), max);

        // value factory avec pas=250
        SpinnerValueFactory.IntegerSpinnerValueFactory vf =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(BET_MIN, max, start, BET_STEP);
        spBet.setValueFactory(vf);
        spBet.getEditor().setText(String.valueOf(start));

        betEditor.setVisible(true);
        spBet.requestFocus();

        // Enter = appliquer
        spBet.getEditor().setOnAction(ev -> applyBetEditor());
        btnApplyBet.setOnAction(ev -> applyBetEditor());
        btnCloseBet.setOnAction(ev -> betEditor.setVisible(false));
    }

    private void applyBetEditor() {
        // Récup texte, clamp et aligne sur le pas de 250
        String raw = spBet.getEditor().getText().replaceAll("\\D", "");
        if (raw.isEmpty()) { return; }
        int val = Integer.parseInt(raw);

        int balance = getBalance(userId);
        int max = Math.max(BET_MIN, balance);

        // aligne sur pas de 250, borne min/max
        val = (val / BET_STEP) * BET_STEP;
        if (val < BET_MIN) val = BET_MIN;
        if (val > max)     val = (max / BET_STEP) * BET_STEP;

        spBet.getValueFactory().setValue(val);
        currentBet = val;

        // petit feedback optionnel via le bandeau résultat
        resultMsg = "Mise fixée à " + currentBet + " XPF";
        roundSettled = false;  // ne bloque pas les boutons
        betEditor.setVisible(false);
        redrawGame();
    }

    private void startRound() {
        roundSettled = false;
        resultMsg = "";
        deck = new ArrayList<>();
        String[] v = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        String[] s = {"D","H","S","C"};
        for (String suit : s) for (String val : v) deck.add(new Card(val, suit));
        // mélange
        for (int i = 0; i < deck.size(); i++){
            int j = rng.nextInt(deck.size());
            Card t = deck.get(i); deck.set(i, deck.get(j)); deck.set(j, t);
        }
        dealer.clear();
        player.clear();

        // initial deal : croupier [hidden] + 1 visible ; joueur 2
        hiddenCard = deck.remove(deck.size()-1);
        dealer.add(deck.remove(deck.size()-1));
        player.add(deck.remove(deck.size()-1));
        player.add(deck.remove(deck.size()-1));

        int pv = computeTotal(player);
        boolean playerBJ = (pv == 21 && player.size() == 2);
        if (playerBJ) {
            // désactiver les actions joueur
            btnHit.setDisable(true);
            btnStay.setDisable(true);   // => ta vue considère "revealed = btnStay.isDisable()"
            // ne pas faire dealerPlay(), on compare juste les 2 mains initiales
            settleAndFinish();          // gère payout 3:2 / push si dealer BJ
            redrawGame();
        }
    }

    private void dealerPlay() {
        // révèle et tire jusqu'à 17
        while (computeDealerTotal(true) < 17) {
            dealer.add(deck.remove(deck.size()-1));
        }
    }

    private int computeTotal(List<Card> hand) {
        int sum = 0, aces = 0;
        for (Card c : hand) { sum += c.getValue(); if (c.isAce()) aces++; }
        while (sum > 21 && aces > 0) { sum -= 10; aces--; }
        return sum;
    }

    private int computeDealerTotal(boolean reveal) {
        int sum = 0, aces = 0;
        for (Card c : dealer) { sum += c.getValue(); if (c.isAce()) aces++; }
        if (reveal && hiddenCard != null) { sum += hiddenCard.getValue(); if (hiddenCard.isAce()) aces++; }
        while (sum > 21 && aces > 0) { sum -= 10; aces--; }
        return sum;
    }

    private void settleAndFinish() {
        if (roundSettled) return;

        int p = computeTotal(player);
        int d = computeDealerTotal(true);

        boolean playerBJ = (p == 21 && player.size() == 2);                   // BJ naturel
        boolean dealerBJ = (d == 21 && hiddenCard != null && dealer.size()==1);// 2 cartes côté croupier

        String result;
        int payout; // crédit final (mise déjà débitée au début)

        if (p > 21) {
            result = "LOSE"; payout = 0;

        } else if (playerBJ && dealerBJ) {
            // Double blackjack = push
            result = "PUSH"; payout = currentBet;

        } else if (playerBJ) {
            // BJ joueur bat n'importe quel 21 non-BJ
            result = "WIN";  payout = (int)Math.round(currentBet * 2.5); // 3:2

        } else if (dealerBJ) {
            // BJ croupier bat 21 du joueur non-BJ
            result = "LOSE"; payout = 0;

        } else if (d > 21) {
            result = "WIN";  payout = currentBet * 2;

        } else if (p == d) {
            // égalité simple (aucun BJ, les BJ ont été traités plus haut)
            result = "PUSH"; payout = currentBet;

        } else if (p > d) {
            result = "WIN";  payout = currentBet * 2;

        } else {
            result = "LOSE"; payout = 0;
        }

        // message
        if ("WIN".equals(result)) {
            resultMsg = playerBJ ? "Blackjack ! Tu as gagné " : "Tu as gagné ";
        } else if ("LOSE".equals(result)) {
            resultMsg = (p > 21) ? "Tu as dépassé 21, tu as perdu " : "Tu as perdu ";
        } else {
            resultMsg = "Égalité ";
        }

        settle(userId, sessionId, payout, result);
        refreshBalanceUI();
        roundSettled = true;

        btnNewRound.setDisable(false);
        btnChangeBet.setDisable(false);
    }


    private void drawResultBanner(GraphicsContext g, double W, double H) {
        if (!roundSettled || resultMsg == null || resultMsg.isEmpty()) return;

        // fond semi-transparent
        g.setFill(Color.rgb(0, 0, 0, 0.55));
        double bw = W * 0.9, bh = 70;
        double bx = (W - bw) / 2, by = H - bh - 12;
        g.fillRoundRect(bx, by, bw, bh, 16, 16);

        // texte centré
        g.setFont(Font.font("Arial", 24));
        g.setFill(Color.WHITE);
        double tw = textWidth(resultMsg, g.getFont());
        double tx = Math.max(bx + (bw - tw) / 2, bx + 16);
        double ty = by + bh/2 + 8;
        g.fillText(resultMsg, tx, ty);
    }

    private void redrawGame() {
        GraphicsContext g = gameCanvas.getGraphicsContext2D();
        double W = gameCanvas.getWidth(), H = gameCanvas.getHeight();

        // fond tapis
        g.clearRect(0,0,W,H);
        if (tapisImg != null) g.drawImage(tapisImg, 0, 0, W, H);
        else {
            g.setFill(Color.web("#35654d"));
            g.fillRect(0,0,W,H);
        }

        // cartes (taille cohérente)
        double cw = 110, ch = 160;
        // croupier
        // dealer
        boolean revealed = btnStay.isDisable(); // comme tu faisais
        drawDealer(g, /*x0*/ 20, /*y0*/ 20, revealed);

// joueur
        drawHand(g, player, 20, 320);            // cartes très proches comme sur ton image

        // points
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Arial", 20));
        g.fillText("Joueur : " + computeTotal(player), 20, 320 + ch + 30);
        if (revealed) g.fillText("Croupier : " + computeDealerTotal(true), 20, 20 + ch + 30);
        else g.fillText("Croupier : " + computeDealerTotal(false) + " + ?", 20, 20 + ch + 30);

        // badge solde en haut droite
        drawBalanceBadgeFX(g, W, H);
        drawResultBanner(g, W, H);
    }
    private static final Text PROBE = new Text(); // réutilisable

    private static double textWidth(String s, Font f) {
        PROBE.setText(s);
        PROBE.setFont(f);
        return PROBE.getLayoutBounds().getWidth();
    }

    private void drawBalanceBadgeFX(GraphicsContext g, double W, double H) {
        String txt = "Solde  " + balanceCached + " XPF";
        g.setFont(Font.font("Arial", 16));
        double textW = textWidth(txt, g.getFont());
        double padX=14, padY=8, r=14;
        double w = textW + padX*2 + 18, h = 30;

        double x = W - w - 12, y = 10;

        g.setFill(Color.rgb(0,0,0,0.45)); g.fillRoundRect(x,y,w,h,r,r);
        g.setStroke(Color.rgb(255,255,255,0.6)); g.setLineWidth(2); g.strokeRoundRect(x,y,w,h,r,r);

        // “jeton or”
        double coinD = h-10, coinX = x+8, coinY = y+(h-coinD)/2;
        g.setFill(Color.web("#D6AA3C")); g.fillOval(coinX, coinY, coinD, coinD);
        g.setStroke(Color.web("#785A1E")); g.strokeOval(coinX, coinY, coinD, coinD);

        g.setFill(Color.web("#F5F2E6"));
        g.fillText(txt, coinX + coinD + 8, y + h/2 + 6);
    }

    private boolean ensureBetAffordable() {
        int balance = getBalance(userId);
        int step=50, min=250;
        if (balance < min) {
            new Alert(Alert.AlertType.WARNING, "Solde insuffisant (min 250 XPF).").showAndWait();
            return false;
        }
        if (currentBet > balance) {
            int adjusted = Math.max(min, (balance/step)*step);
            if (adjusted < min) {
                new Alert(Alert.AlertType.WARNING, "Solde insuffisant pour la mise minimale.").showAndWait();
                return false;
            }
            currentBet = adjusted;
            new Alert(Alert.AlertType.INFORMATION, "Mise ajustée à " + currentBet + " XPF.").showAndWait();
        }
        return true;
    }

    // === DIALOG signup minimal ===
    private Dialog<Long> buildSignupDialog() {
        Dialog<Long> dlg = new Dialog<>();
        dlg.setTitle("Créer un compte");
        TextField tfEmail = new TextField();
        TextField tfPseudo = new TextField();
        PasswordField pf1 = new PasswordField(), pf2 = new PasswordField();
        tfEmail.setPromptText("email"); tfPseudo.setPromptText("pseudo");
        pf1.setPromptText("mot de passe"); pf2.setPromptText("confirmer");

        VBox box = new VBox(10, new Label("Email"), tfEmail, new Label("Pseudo"), tfPseudo,
                new Label("Mot de passe"), pf1, new Label("Confirmer"), pf2);
        box.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String email=tfEmail.getText().trim(), pseudo=tfPseudo.getText().trim();
            String p1=pf1.getText(), p2=pf2.getText();
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { alert("Email invalide"); return null; }
            if (!p1.equals(p2) || p1.length()<12 || !p1.matches(".*[A-Z].*") || !p1.matches(".*[a-z].*")
                    || !p1.matches(".*\\d.*") || !p1.matches(".*[^A-Za-z0-9].*")) { alert("Mot de passe trop faible"); return null; }
            try {
                long id = createUser(email, pseudo, p1);
                return id;
            } catch (Exception ex) {
                alert("Erreur: " + ex.getMessage()); return null;
            }
        });
        return dlg;
    }
    private void alert(String m){ new Alert(Alert.AlertType.INFORMATION, m).showAndWait(); }

    // === BDD (reprend tes méthodes existantes) ===

    private record UserRow(long id, String hash){}

    private void openDb() {
        try {
            Class.forName("org.sqlite.JDBC");
            String dbPath = resolveDbPath().replace('\\','/');
            cn = DriverManager.getConnection("jdbc:sqlite:" + dbPath + "?foreign_keys=on");
        } catch (Exception e) { throw new RuntimeException("DB open", e); }
    }
    private static String resolveDbPath() {
        String base = System.getenv("APPDATA") + "\\Blackjack";
        try { java.nio.file.Files.createDirectories(java.nio.file.Paths.get(base)); } catch (Exception ignore) {}
        return base + "\\blackjack.db";
    }

    private void ensureSchema() {
        String[] ddl = new String[] {
                // 1) Activer les FK (à faire sur CHAQUE connexion)
                "PRAGMA foreign_keys = ON",

                // 2) Tables
                """
        CREATE TABLE IF NOT EXISTS utilisateur (
          id_utilisateur   INTEGER PRIMARY KEY AUTOINCREMENT,
          email            TEXT NOT NULL UNIQUE,
          pseudo           TEXT NOT NULL,
          hash_mdp         TEXT NOT NULL,
          date_creation    TEXT NOT NULL DEFAULT (datetime('now')),
          etat             TEXT NOT NULL DEFAULT 'actif',
          role             TEXT NOT NULL DEFAULT 'user'
        )
        """,
                """
        CREATE TABLE IF NOT EXISTS wallet (
          id_utilisateur     INTEGER PRIMARY KEY,
          solde_actuel       INTEGER NOT NULL,
          last_daily_credit  TEXT NOT NULL,
          created_at         TEXT NOT NULL DEFAULT (datetime('now')),
          updated_at         TEXT NOT NULL DEFAULT (datetime('now')),
          FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE
        )
        """,
                """
        CREATE TABLE IF NOT EXISTS session_jeu (
          id_session       INTEGER PRIMARY KEY AUTOINCREMENT,
          id_utilisateur   INTEGER NOT NULL,
          date_debut       TEXT NOT NULL DEFAULT (datetime('now')),
          date_fin         TEXT,
          mise_totale      INTEGER NOT NULL DEFAULT 0,
          gain_total       INTEGER NOT NULL DEFAULT 0,
          resultat         TEXT,
          seed_rng         TEXT,
          FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE
        )
        """,
                """
        CREATE TABLE IF NOT EXISTS txn (
          id_tx          INTEGER PRIMARY KEY AUTOINCREMENT,
          id_utilisateur INTEGER NOT NULL,
          id_session     INTEGER,
          type           TEXT NOT NULL,
          montant        INTEGER NOT NULL,
          solde_avant    INTEGER NOT NULL,
          solde_apres    INTEGER NOT NULL,
          note           TEXT,
          created_at     TEXT NOT NULL DEFAULT (datetime('now')),
          FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE,
          FOREIGN KEY (id_session)     REFERENCES session_jeu(id_session) ON DELETE SET NULL
        )
        """,

                // 3) Triggers (garder tout le bloc BEGIN…END; dans UNE seule String)
                """
        CREATE TRIGGER IF NOT EXISTS trg_user_init_wallet
        AFTER INSERT ON utilisateur
        BEGIN
          INSERT INTO wallet(id_utilisateur, solde_actuel, last_daily_credit, created_at, updated_at)
          VALUES (NEW.id_utilisateur, 10000, date('now'), datetime('now'), datetime('now'));

          INSERT INTO txn(id_utilisateur, id_session, type, montant, solde_avant, solde_apres, note)
          VALUES (NEW.id_utilisateur, NULL, 'INIT', 10000, 0, 10000, 'Solde initial');
        END;
        """,
                """
        CREATE TRIGGER IF NOT EXISTS trg_tx_commit
        AFTER INSERT ON txn
        BEGIN
          UPDATE wallet
             SET solde_actuel = NEW.solde_apres,
                 updated_at   = datetime('now')
           WHERE id_utilisateur = NEW.id_utilisateur;
        END;
        """
        };

        try (Statement st = cn.createStatement()) {
            cn.setAutoCommit(false);
            for (String sql : ddl) {
                String q = sql.trim();
                if (!q.isEmpty()) {
                    st.executeUpdate(q); // une requête complète à la fois
                }
            }
            cn.commit();
        } catch (SQLException e) {
            try { cn.rollback(); } catch (SQLException ignore) {}
            throw new RuntimeException("schema", e);
        } finally {
            try { cn.setAutoCommit(true); } catch (SQLException ignore) {}
        }
    }


    private UserRow findUserByEmail(String email) {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT id_utilisateur, hash_mdp FROM utilisateur WHERE email=?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new UserRow(rs.getLong(1), rs.getString(2));
                return null;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private long createUser(String email, String pseudo, String rawPwd) {
        String hash = SecurityUtil.hashPwd(rawPwd);
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO utilisateur(email,pseudo,hash_mdp) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, pseudo);
            ps.setString(3, hash);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
            try (Statement st = cn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new RuntimeException("createUser: no id");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique"))
                throw new RuntimeException("Cet email est déjà utilisé.");
            throw new RuntimeException(e);
        }
    }

    private void applyDailyCredit(long uid) {
        try {
            boolean old = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try (PreparedStatement ps1 = cn.prepareStatement("""
                    WITH w AS (SELECT id_utilisateur, solde_actuel, last_daily_credit FROM wallet WHERE id_utilisateur = ?)
                    INSERT INTO txn (id_utilisateur, id_session, type, montant, solde_avant, solde_apres, note)
                    SELECT ?, NULL, 'DAILY_CREDIT', 1000, w.solde_actuel, w.solde_actuel + 1000, 'Crédit quotidien'
                    FROM w WHERE date(w.last_daily_credit) < date('now');
                """);
                 PreparedStatement ps2 = cn.prepareStatement("""
                    UPDATE wallet SET last_daily_credit=date('now'), updated_at=datetime('now')
                    WHERE id_utilisateur=? AND date(last_daily_credit) < date('now');
                """)) {
                ps1.setLong(1, uid); ps1.setLong(2, uid); ps1.executeUpdate();
                ps2.setLong(1, uid); ps2.executeUpdate();
                cn.commit();
            } catch (Exception ex) { cn.rollback(); throw ex; }
            finally { cn.setAutoCommit(old); }
        } catch (SQLException e) { throw new RuntimeException("daily", e); }
    }

    private int getBalance(long uid) {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT solde_actuel FROM wallet WHERE id_utilisateur=?")) {
            ps.setLong(1, uid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private long startSession(long uid) {
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO session_jeu(id_utilisateur) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, uid); ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
            try (Statement st = cn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new RuntimeException("startSession: no id");
        } catch (SQLException e) { throw new RuntimeException("startSession", e); }
    }

    private void placeBet(long uid, long sessionId, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount > 0");
        try {
            int before = getBalance(uid), after = before - amount;
            if (after < 0) throw new RuntimeException("Solde insuffisant (" + before + ")");
            boolean old = cn.getAutoCommit(); cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO txn(id_utilisateur,id_session,type,montant,solde_avant,solde_apres,note) VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement ps2 = cn.prepareStatement(
                         "UPDATE session_jeu SET mise_totale = mise_totale + ? WHERE id_session=?")) {
                ps.setLong(1, uid); ps.setLong(2, sessionId); ps.setString(3, "BET");
                ps.setInt(4, -amount); ps.setInt(5, before); ps.setInt(6, after); ps.setString(7, "Mise"); ps.executeUpdate();
                ps2.setInt(1, amount); ps2.setLong(2, sessionId); ps2.executeUpdate();
                cn.commit();
            } catch (Exception ex){ cn.rollback(); throw ex; }
            finally { cn.setAutoCommit(old); }
        } catch (SQLException e) { throw new RuntimeException("placeBet", e); }
    }

    private void settle(long uid, long sessionId, int delta, String resultat) {
        try {
            int before = getBalance(uid), after = before + delta;
            boolean old = cn.getAutoCommit(); cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO txn(id_utilisateur,id_session,type,montant,solde_avant,solde_apres,note) VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement ps2 = cn.prepareStatement(
                         "UPDATE session_jeu SET gain_total=gain_total + ?, resultat=?, date_fin=datetime('now') WHERE id_session=?")) {
                ps.setLong(1, uid); ps.setLong(2, sessionId); ps.setString(3, "PAYOUT");
                ps.setInt(4, delta); ps.setInt(5, before); ps.setInt(6, after); ps.setString(7, "Règlement manche"); ps.executeUpdate();
                ps2.setInt(1, delta); ps2.setString(2, resultat); ps2.setLong(3, sessionId); ps2.executeUpdate();
                cn.commit();
            } catch (Exception ex){ cn.rollback(); throw ex; }
            finally { cn.setAutoCommit(old); }
        } catch (SQLException e) { throw new RuntimeException("settle", e); }
    }

    private void refreshBalanceUI() {
        balanceCached = getBalance(userId);
    }
}
