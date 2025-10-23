package org.example; // Déclare le package principal de l'application.

import javafx.application.Application; // Importe la classe de base JavaFX.
import javafx.geometry.Insets; // Importe Insets pour gérer les marges.
import javafx.geometry.Pos; // Importe Pos pour aligner les conteneurs.
import javafx.scene.Scene; // Importe Scene pour créer des scènes JavaFX.
import javafx.scene.canvas.Canvas; // Importe Canvas pour dessiner le jeu.
import javafx.scene.control.*; // Importe l'ensemble des contrôles standards.
import javafx.scene.image.Image; // Importe Image pour charger les ressources.
import javafx.scene.layout.*; // Importe les conteneurs de mise en page.
import javafx.stage.Stage; // Importe Stage pour la fenêtre principale.
import javafx.scene.Group; // Importe Group pour assembler les formes du logo.
import javafx.scene.paint.Color; // Importe Color pour construire le logo vectoriel.
import javafx.scene.shape.Circle; // Importe Circle pour dessiner le logo.
import javafx.scene.shape.Polygon; // Importe Polygon pour dessiner les symboles de cartes.
import javafx.scene.shape.Rectangle; // Importe Rectangle pour simuler les cartes dans le logo.
import javafx.scene.text.Font; // Importe Font pour styliser le monogramme du logo.
import javafx.scene.text.Text; // Importe Text pour dessiner les initiales dans le badge.
import org.example.db.DatabaseService; // Importe le service de base de données.
import org.example.db.DatabaseService.UserCredentials; // Importe le type de résultat de login.
import org.example.game.BlackjackRound; // Importe la logique métier du blackjack.
import org.example.game.RoundOutcome; // Importe l'issue de manche.
import org.example.ui.GameRenderer; // Importe le moteur de rendu du plateau.

import java.util.Objects; // Importe Objects pour valider les ressources.

public class AppFX extends Application { // Classe principale JavaFX.
    private final DatabaseService database = new DatabaseService(); // Service SQL centralisé.
    private final BlackjackRound round = new BlackjackRound(); // État métier d'une manche.

    private Stage stage; // Référence vers la fenêtre principale.
    private Scene loginScene; // Scène pour la connexion.
    private Scene betScene; // Scène pour choisir la mise.
    private Scene gameScene; // Scène principale du jeu.

    private Canvas gameCanvas; // Canvas sur lequel le renderer dessine.
    private GameRenderer renderer; // Responsable du dessin du plateau.

    private Button btnHit; // Bouton pour tirer une carte.
    private Button btnStay; // Bouton pour rester.
    private Button btnNewRound; // Bouton pour relancer une manche.
    private Button btnChangeBet; // Bouton pour modifier la mise.
    private Button btnApplyBet; // Bouton pour appliquer une nouvelle mise.
    private Button btnCloseBet; // Bouton pour fermer l'éditeur de mise.
    private Spinner<Integer> spBet; // Spinner utilisé dans l'éditeur de mise.
    private HBox betEditor; // Conteneur affichant l'éditeur de mise.

    private long userId = -1L; // Identifiant joueur connecté.
    private long sessionId = -1L; // Identifiant de la session en cours.
    private int currentBet = 500; // Mise actuelle sélectionnée.
    private int balanceCached = 0; // Solde mis en cache pour le HUD.
    private String resultMsg = ""; // Message de résultat affiché sur le plateau.

    @Override
    public void start(Stage primaryStage) { // Point d'entrée JavaFX.
        this.stage = primaryStage; // Conserve la référence de la fenêtre.
        stage.setTitle("Black Jack"); // Définit le titre de la fenêtre.
        stage.setMinWidth(720); // Imose une largeur minimale.
        stage.setMinHeight(480); // Imose une hauteur minimale.

        Image tableImage = loadImage("/background/tapis.png"); // Charge le visuel du tapis.
        Image backImage = loadImage("/card/BACK.png"); // Charge l'image du dos de carte.
        renderer = new GameRenderer(tableImage, backImage); // Instancie le renderer avec les ressources.

        loginScene = buildLoginScene(); // Construit la scène de connexion.
        stage.setScene(loginScene); // Affiche la scène initiale.
        stage.setWidth(960); // Positionne une largeur plus généreuse pour révéler le décor.
        stage.setHeight(640); // Ajuste la hauteur pour équilibrer la carte horizontale.
        stage.centerOnScreen(); // Centre la fenêtre.
        stage.show(); // Affiche la fenêtre.
    }

    private Image loadImage(String path) { // Charge une image en gérant les erreurs.
        try { // Tente l'ouverture de la ressource.
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(path))); // Retourne l'image chargée.
        } catch (Exception ex) { // Capture une éventuelle absence.
            return null; // Retourne null si la ressource est manquante.
        }
    }

    private Scene buildLoginScene() { // Construit la scène de connexion.
        StackPane root = new StackPane(); // Crée un conteneur centré pour exposer la carte.
        root.setPadding(new Insets(32, 0, 32, 0)); // Ajoute un espace vertical pour voir le fond vert.
        root.getStyleClass().add("login-root"); // Applique le fond vert texturé défini dans la feuille de style.

        HBox card = new HBox(36); // Crée une carte horizontale composée de deux colonnes avec un écart réduit.
        card.setAlignment(Pos.CENTER_LEFT); // Aligne le contenu vers la gauche pour accentuer la largeur.
        card.setPadding(new Insets(30, 40, 30, 40)); // Ajoute un padding interne mesuré pour alléger la carte.
        card.setMinHeight(260); // Garantit une silhouette horizontale mais plus compacte.
        card.setPrefHeight(300); // Fixe une hauteur préférée équilibrée pour l'œil.
        card.setMaxHeight(340); // Empêche le panneau de s'étirer verticalement en plein écran.
        card.setMinWidth(520); // Définit une largeur minimale pour conserver la lisibilité.
        card.setPrefWidth(620); // Spécifie la largeur idéale recherchée pour la carte.
        card.setMaxWidth(640); // Limite l'expansion afin de rester modeste sur écran large.
        card.setFillHeight(false); // Désactive l'étirement vertical automatique des colonnes.
        card.getStyleClass().add("login-card"); // Applique le style crème arrondi de la carte de connexion.

        VBox branding = new VBox(14); // Crée la colonne de gauche dédiée à l'identité visuelle.
        branding.setAlignment(Pos.CENTER); // Centre le logo et les textes.
        branding.setMinWidth(220); // Bloque une largeur fixe afin d'éviter les étirements.
        branding.setPrefWidth(220); // Harmonise la largeur préférée avec les contraintes de la carte.
        branding.setMaxWidth(220); // Verrouille la largeur maximale pour garder un gabarit stable.

        StackPane logoBadge = buildLogoBadge(); // Construit le logo vectoriel de l'application.
        Label brandTitle = new Label("BlackJack NC"); // Crée le titre de marque.
        brandTitle.getStyleClass().add("login-brand-title"); // Applique le style typographique principal.
        Label brandSubtitle = new Label("Entre dans le casino virtuel"); // Crée le slogan d'accroche.
        brandSubtitle.getStyleClass().add("login-brand-subtitle"); // Applique le style secondaire.
        brandSubtitle.setWrapText(true); // Autorise le passage à la ligne dans la colonne.
        brandSubtitle.setMaxWidth(220); // Limite la largeur pour garder un aspect compact.
        branding.getChildren().addAll(logoBadge, brandTitle, brandSubtitle); // Assemble la colonne de marque.
        HBox.setHgrow(branding, Priority.NEVER); // Empêche l'agrandissement horizontal involontaire du bloc logo.

        VBox form = new VBox(14); // Crée la colonne de droite pour le formulaire avec un rythme plus serré.
        form.setAlignment(Pos.CENTER_LEFT); // Aligne les champs vers la gauche pour faciliter la lecture.
        form.setMinWidth(260); // Fixe la largeur minimale pour les champs.
        form.setPrefWidth(260); // Stabilise la largeur préférée du formulaire.
        form.setMaxWidth(280); // Limite la largeur pour conserver l'esthétique de carte.
        HBox.setHgrow(form, Priority.NEVER); // Empêche le formulaire de s'élargir lorsqu'on passe en plein écran.

        Label title = new Label("Connexion"); // Titre de la section.
        title.getStyleClass().add("login-title"); // Utilise la classe dédiée au grand titre.

        TextField tfEmail = new TextField(); // Champ email.
        tfEmail.setPromptText("Email"); // Placeholder du champ email.
        tfEmail.getStyleClass().add("input-cream"); // Applique le style crème et arrondi au champ email.
        tfEmail.setPrefWidth(260); // Calibre la largeur pour épouser la carte horizontale.

        PasswordField pfPassword = new PasswordField(); // Champ mot de passe masqué.
        pfPassword.setPromptText("Mot de passe"); // Placeholder du champ mot de passe.
        pfPassword.getStyleClass().add("input-cream"); // Applique le style crème au champ mot de passe.
        pfPassword.setPrefWidth(260); // Harmonise la largeur avec le champ email.

        Label message = new Label(); // Label pour afficher les erreurs.
        message.getStyleClass().add("login-msg"); // Utilise le style rouge doux prévu pour les messages.
        message.setWrapText(true); // Autorise le retour à la ligne dans l'encart horizontal.
        message.setMaxWidth(280); // Limite la largeur pour rester dans la colonne du formulaire.

        Button btnLogin = new Button("Se connecter"); // Bouton de connexion.
        btnLogin.getStyleClass().add("btn-primary"); // Applique le style principal vert.
        btnLogin.setPrefWidth(140); // Calibre la largeur pour l'esthétique horizontale.
        Button btnSignup = new Button("Créer un compte"); // Bouton d'inscription.
        btnSignup.getStyleClass().addAll("btn-primary", "btn-soft"); // Applique la variante douce pour le second bouton.
        btnSignup.setPrefWidth(160); // Harmonise la largeur du second bouton.
        HBox actions = new HBox(12, btnLogin, btnSignup); // Regroupe les boutons.
        actions.setAlignment(Pos.CENTER_LEFT); // Aligne les actions sur la gauche du formulaire.

        form.getChildren().addAll(title, tfEmail, pfPassword, actions, message); // Assemble le formulaire.
        card.getChildren().addAll(branding, form); // Place les deux colonnes dans la carte horizontale.
        root.widthProperty().addListener((obs, oldVal, newVal) -> { // Observe l'évolution de la largeur de la scène.
            double targetWidth = Math.min(620, newVal.doubleValue() - 160); // Calcule une largeur idéale bornée par l'écran.
            double clamped = Math.max(520, targetWidth); // Empêche la carte de devenir trop petite.
            card.setPrefWidth(clamped); // Ajuste la largeur préférée dynamiquement.
        });
        root.getChildren().add(card); // Centre la carte dans la scène.

        btnLogin.setOnAction(e -> { // Déclare l'action de connexion.
            String email = tfEmail.getText().trim(); // Récupère l'email saisi.
            String rawPwd = pfPassword.getText(); // Récupère le mot de passe.
            if (email.isEmpty() || rawPwd.isEmpty()) { // Vérifie les champs vides.
                message.setText("Veuillez remplir les deux champs."); // Affiche une erreur.
                return; // Annule la connexion.
            }
            UserCredentials creds = database.findUserByEmail(email); // Cherche l'utilisateur.
            if (creds == null) { // Aucun utilisateur correspondant.
                message.setText("Utilisateur introuvable."); // Affiche une erreur.
                return; // Stoppe la procédure.
            }
            if (!SecurityUtil.checkPwd(rawPwd, creds.hash())) { // Vérifie le mot de passe.
                message.setText("Mot de passe incorrect."); // Informe de l'échec.
                return; // Stoppe la procédure.
            }
            userId = creds.id(); // Mémorise l'identifiant.
            database.applyDailyCredit(userId); // Déclenche le crédit quotidien.
            refreshBalance(); // Met à jour le solde local.
            betScene = buildBetScene(); // Construit la scène de mise.
            switchScene(betScene); // Affiche la scène suivante.
        });

        btnSignup.setOnAction(e -> { // Déclare l'action d'inscription.
            Dialog<Long> dialog = buildSignupDialog(); // Crée la boîte d'inscription.
            dialog.showAndWait().ifPresent(id -> { // Attend une réponse.
                userId = id; // Stocke l'identifiant nouvellement créé.
                refreshBalance(); // Met à jour le solde initial.
                betScene = buildBetScene(); // Prépare la scène de mise.
                switchScene(betScene); // Passe à l'étape suivante.
            });
        });

        Scene scene = new Scene(root, 960, 640); // Crée la scène JavaFX adaptée à la nouvelle carte.
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm()); // Ajoute la feuille de style existante.
        return scene; // Retourne la scène prête.
    }

    private StackPane buildLogoBadge() { // Construit le logo vectoriel inspiré du visuel fourni.
        StackPane badge = new StackPane(); // Crée le conteneur central.
        badge.setMinSize(220, 220); // Garantit une surface suffisante pour tous les éléments.
        badge.setPrefSize(220, 220); // Conserve une taille carrée harmonieuse.
        badge.getStyleClass().add("login-logo"); // Applique le style spécifique du logo.

        Group art = new Group(); // Prépare un groupe pour superposer les formes.

        Circle outerBase = new Circle(0, 0, 104); // Crée le disque de fond vert foncé.
        outerBase.setFill(Color.web("#0d6c34")); // Applique la teinte principale du cercle extérieur.

        Circle outerRim = new Circle(0, 0, 104); // Crée un contour foncé autour du badge.
        outerRim.setStroke(Color.web("#093f23")); // Définit la couleur du trait extérieur.
        outerRim.setStrokeWidth(6); // Épaissit le contour pour rappeler la bordure du logo.
        outerRim.setFill(Color.TRANSPARENT); // Rend le contour creux pour ne conserver que le trait.

        Circle yellowBand = new Circle(0, 0, 94); // Crée l'anneau jaune interne.
        yellowBand.setFill(Color.TRANSPARENT); // Rend l'intérieur du cercle transparent.
        yellowBand.setStroke(Color.web("#f6d047")); // Choisit un jaune doré proche du logo fourni.
        yellowBand.setStrokeWidth(22); // Définit l'épaisseur de la bande jaune.
        yellowBand.setTranslateX(6); // Décale légèrement la bande pour l'asymétrie caractéristique.

        Circle innerGreen = new Circle(0, 0, 78); // Crée le disque vert clair central.
        innerGreen.setFill(Color.web("#1fb665")); // Applique un vert saturé similaire à l'original.
        innerGreen.setStroke(Color.web("#0f5e32")); // Ajoute un contour sombre pour délimiter le disque.
        innerGreen.setStrokeWidth(4); // Définit l'épaisseur du contour intérieur.

        Circle yellowAccent = new Circle(24, -18, 64); // Crée la zone jaune décalée visible sur le logo.
        yellowAccent.setFill(Color.web("#f7d84f")); // Applique la couleur dorée de l'accent.
        yellowAccent.setOpacity(0.7); // Rend l'accent légèrement translucide pour fondre avec le fond.

        Rectangle cardShadow = new Rectangle(142, 188); // Crée l'ombre portée de la carte.
        cardShadow.setArcWidth(34); // Arrondit les coins de l'ombre pour suivre la carte.
        cardShadow.setArcHeight(34); // Arrondit également la hauteur de l'ombre.
        cardShadow.setFill(Color.rgb(0, 0, 0, 0.18)); // Applique une ombre douce semi-transparente.
        cardShadow.setRotate(-12); // Aligne l'ombre avec la rotation de la carte.
        cardShadow.setTranslateX(16); // Décale légèrement l'ombre vers la droite.
        cardShadow.setTranslateY(8); // Descend l'ombre pour simuler la profondeur.

        Rectangle card = new Rectangle(138, 184); // Crée la carte blanche centrale.
        card.setArcWidth(32); // Arrondit les coins pour correspondre au logo.
        card.setArcHeight(32); // Applique le même arrondi verticalement.
        card.setFill(Color.web("#fff7db")); // Utilise un blanc crème proche du visuel.
        card.setStroke(Color.web("#0a6b3a")); // Ajoute un filet vert foncé autour de la carte.
        card.setStrokeWidth(4); // Définit l'épaisseur du filet de la carte.
        card.setRotate(-12); // Oriente la carte pour rappeler la perspective du logo.
        card.setTranslateX(8); // Décale légèrement la carte vers la droite.
        card.setTranslateY(-6); // Remonte la carte pour équilibrer la composition.

        Polygon diamond = new Polygon(0.0, -26.0, 18.0, 0.0, 0.0, 26.0, -18.0, 0.0); // Crée le symbole de carre.
        diamond.setFill(Color.web("#ff3c3c")); // Applique le rouge vif du carre.
        diamond.setStroke(Color.web("#b02424")); // Ajoute un contour plus sombre autour du symbole.
        diamond.setStrokeWidth(3); // Définit l'épaisseur du contour du carre.
        diamond.setTranslateX(28); // Place le carre vers le coin supérieur droit de la carte.
        diamond.setTranslateY(-46); // Positionne le carre dans la partie haute de la carte.
        diamond.setRotate(-10); // Incline légèrement le carre comme sur le logo.

        Circle clubTop = new Circle(0, -18, 14); // Crée le lobe supérieur du trèfle.
        clubTop.setFill(Color.web("#0e743d")); // Applique la couleur verte du symbole.
        Circle clubLeft = new Circle(-12, -2, 14); // Crée le lobe gauche du trèfle.
        clubLeft.setFill(Color.web("#0e743d")); // Utilise la même teinte verte pour l'uniformité.
        Circle clubRight = new Circle(12, -2, 14); // Crée le lobe droit du trèfle.
        clubRight.setFill(Color.web("#0e743d")); // Applique la même couleur.
        Rectangle clubStem = new Rectangle(-4, 10, 8, 22); // Crée la tige du trèfle.
        clubStem.setArcWidth(6); // Arrondit légèrement la tige pour la douceur visuelle.
        clubStem.setArcHeight(6); // Arrondit aussi la hauteur de la tige.
        clubStem.setFill(Color.web("#0e743d")); // Applique la couleur verte au pied du trèfle.
        Group club = new Group(clubTop, clubLeft, clubRight, clubStem); // Assemble les éléments du trèfle.
        club.setTranslateX(-32); // Positionne le trèfle sur la carte.
        club.setTranslateY(-34); // Remonte le trèfle vers le haut de la carte.
        club.setRotate(-12); // Aligne le trèfle avec l'inclinaison générale.

        Circle spadeTop = new Circle(0, -12, 12); // Crée la tête du pique.
        spadeTop.setFill(Color.web("#0a5a32")); // Applique un vert sombre pour rappeler l'illustration.
        Circle spadeLeft = new Circle(-10, 0, 10); // Crée le lobe gauche du pique.
        spadeLeft.setFill(Color.web("#0a5a32")); // Utilise la même teinte sombre.
        Circle spadeRight = new Circle(10, 0, 10); // Crée le lobe droit du pique.
        spadeRight.setFill(Color.web("#0a5a32")); // Applique la même couleur sombre.
        Polygon spadeStem = new Polygon(-7.0, 8.0, 7.0, 8.0, 0.0, 28.0); // Crée la tige triangulaire du pique.
        spadeStem.setFill(Color.web("#0a5a32")); // Applique la teinte sombre à la tige.
        Group spade = new Group(spadeTop, spadeLeft, spadeRight, spadeStem); // Assemble les éléments du pique.
        spade.setTranslateX(18); // Positionne le pique vers le bas de la carte.
        spade.setTranslateY(44); // Abaisse le symbole pour reproduire le visuel.
        spade.setScaleX(0.92); // Réduit légèrement le pique horizontalement.
        spade.setScaleY(0.92); // Réduit légèrement le pique verticalement.
        spade.setRotate(-12); // Aligne le symbole sur la rotation générale de la carte.

        Text wordBlack = new Text("BLACK"); // Crée la première ligne du titre.
        wordBlack.setFont(Font.font("Arial Black", 30)); // Utilise une police épaisse proche de l'original.
        wordBlack.setFill(Color.web("#fffbea")); // Applique un blanc cassé au texte.
        wordBlack.setStroke(Color.web("#0c6b39")); // Ajoute un contour vert foncé autour des lettres.
        wordBlack.setStrokeWidth(4); // Épaissit le contour pour rappeler l'effet outline.

        Text wordJack = new Text("JACK"); // Crée la seconde ligne du titre.
        wordJack.setFont(Font.font("Arial Black", 30)); // Utilise la même police pour la cohérence.
        wordJack.setFill(Color.web("#fffbea")); // Applique la même couleur claire.
        wordJack.setStroke(Color.web("#0c6b39")); // Ajoute le contour vert foncé.
        wordJack.setStrokeWidth(4); // Définit l'épaisseur du contour du mot "JACK".
        wordJack.setTranslateY(34); // Abaisse la seconde ligne pour créer la pile de texte.

        Group title = new Group(wordBlack, wordJack); // Regroupe les deux mots pour les manipuler ensemble.
        title.setTranslateX(-38); // Place le texte au centre de la carte.
        title.setTranslateY(20); // Ajuste la hauteur du texte sur la carte.
        title.setRotate(-12); // Oriente le texte pour suivre la carte inclinée.

        Polygon star = new Polygon(0.0, -24.0, 6.4, -7.0, 22.0, -7.0, 9.0, 2.4, 14.4, 18.0, 0.0, 9.6, -14.4, 18.0, -9.0, 2.4, -22.0, -7.0, -6.4, -7.0); // Crée l'étoile jaune.
        star.setFill(Color.web("#ffd84d")); // Applique la couleur dorée de l'étoile.
        star.setStroke(Color.web("#0a6b3a")); // Ajoute un contour vert foncé comme sur le logo.
        star.setStrokeWidth(3); // Définit l'épaisseur du contour de l'étoile.
        star.setTranslateX(-72); // Place l'étoile vers le bord supérieur gauche.
        star.setTranslateY(-100); // Remonte l'étoile au-dessus de la carte.
        star.setRotate(-12); // Oriente légèrement l'étoile.

        Circle chipOuter = new Circle(0, 0, 34); // Crée le disque extérieur du jeton.
        chipOuter.setFill(Color.web("#ffd852")); // Applique la couleur jaune du jeton.
        chipOuter.setStroke(Color.web("#0c6d39")); // Ajoute un contour vert autour du jeton.
        chipOuter.setStrokeWidth(4); // Définit l'épaisseur du contour du jeton.

        Circle chipInnerRing = new Circle(0, 0, 26); // Crée l'anneau vert interne du jeton.
        chipInnerRing.setFill(Color.web("#0f773a")); // Applique un vert foncé à l'anneau.

        Rectangle chipStripeHorizontal = new Rectangle(-30, -5, 60, 10); // Crée la bande horizontale claire du jeton.
        chipStripeHorizontal.setArcWidth(10); // Adoucit les angles de la bande horizontale.
        chipStripeHorizontal.setArcHeight(10); // Adoucit également la hauteur de la bande.
        chipStripeHorizontal.setFill(Color.web("#fffbe5")); // Applique une teinte crème claire.

        Rectangle chipStripeVertical = new Rectangle(-5, -30, 10, 60); // Crée la bande verticale claire du jeton.
        chipStripeVertical.setArcWidth(10); // Arrondit les angles de la bande verticale.
        chipStripeVertical.setArcHeight(10); // Arrondit aussi la hauteur.
        chipStripeVertical.setFill(Color.web("#fffbe5")); // Utilise la même teinte claire.

        Circle chipCore = new Circle(0, 0, 14); // Crée le centre clair du jeton.
        chipCore.setFill(Color.web("#ffe99b")); // Applique un jaune pâle au cœur du jeton.

        Group chip = new Group(chipOuter, chipInnerRing, chipStripeHorizontal, chipStripeVertical, chipCore); // Assemble le jeton décoratif.
        chip.setTranslateX(84); // Positionne le jeton dans le coin inférieur droit.
        chip.setTranslateY(70); // Abaisse légèrement le jeton pour équilibrer le badge.
        chip.setRotate(-6); // Incline légèrement le jeton comme sur le visuel.

        Circle highlight = new Circle(-34, -52, 38); // Crée un éclat clair sur le fond.
        highlight.setFill(Color.rgb(255, 255, 255, 0.28)); // Applique une transparence pour simuler la lumière.
        highlight.setRotate(-12); // Oriente l'éclat pour suivre la carte.

        art.getChildren().addAll(outerBase, yellowBand, innerGreen, yellowAccent, highlight, cardShadow, card, club, diamond, spade, title, star, chip, outerRim); // Assemble toutes les formes dans l'ordre souhaité.
        badge.getChildren().add(art); // Ajoute le groupe graphique dans le conteneur principal.
        return badge; // Retourne le logo complet.
    }


    private Scene buildBetScene() { // Construit la scène de sélection de mise.
        VBox root = new VBox(16); // Conteneur vertical principal.
        root.setAlignment(Pos.CENTER); // Centre les éléments.
        root.setPadding(new Insets(40)); // Ajoute un padding confortable.
        root.getStyleClass().add("app-root"); // Applique le fond vert commun aux écrans de jeu.

        Label info = new Label(); // Label informatif sur le solde.
        updateBetInfo(info); // Initialise le texte avec le solde actuel.

        int balance = balanceCached; // Récupère le solde courant.
        int minBet = 250; // Déclare la mise minimale.
        int maxBet = Math.max(minBet, balance); // Calcule la mise maximale autorisée.
        Spinner<Integer> spinner = new Spinner<>(minBet, maxBet, Math.min(Math.max(currentBet, minBet), maxBet), 50); // Crée un spinner borné.
        spinner.setEditable(false); // Rend le spinner non éditable.

        Button btnStart = new Button("Commencer"); // Bouton pour lancer la manche.
        btnStart.getStyleClass().add("btn-primary"); // Applique le style vert principal au bouton de démarrage.

        root.getChildren().addAll(info, spinner, btnStart); // Assemble la scène.

        btnStart.setOnAction(e -> { // Action de démarrage.
            currentBet = spinner.getValue(); // Enregistre la mise choisie.
            if (!ensureBetAffordable()) { // Vérifie que la mise est viable.
                return; // Annule en cas d'impossibilité.
            }
            startNewRound(); // Initialise une nouvelle manche.
            gameScene = buildGameScene(); // Construit la scène de jeu.
            switchScene(gameScene); // Affiche la scène.
            redrawGame(); // Dessine l'état initial.
            if (round.isPlayerNaturalBlackjack()) { // Vérifie un blackjack naturel.
                finishRound(round.settle(currentBet)); // Termine immédiatement la manche.
                redrawGame(); // Met à jour l'affichage.
            }
            syncButtons(); // Ajuste l'état des boutons.
        });

        Scene scene = new Scene(root, 600, 600); // Crée la scène JavaFX.
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm()); // Applique la feuille de style.
        return scene; // Retourne la scène préparée.
    }

    private void updateBetInfo(Label label) { // Met à jour le label de solde.
        label.setText("Solde: " + balanceCached + " XPF — Choisis ta mise"); // Compose le texte informatif.
    }

    private Scene buildGameScene() { // Construit la scène de jeu principale.
        BorderPane root = new BorderPane(); // Conteneur principal.
        root.setPadding(new Insets(12)); // Ajoute un padding léger.
        root.getStyleClass().add("app-root"); // Applique le fond dégradé vert au plateau.

        gameCanvas = new Canvas(640, 520); // Crée le canvas de dessin.
        root.setCenter(new StackPane(gameCanvas)); // Place le canvas au centre.

        btnHit = new Button("Tirer"); // Bouton tirer.
        btnHit.getStyleClass().add("btn-primary"); // Applique le style vert sur le bouton tirer.
        btnStay = new Button("Rester"); // Bouton rester.
        btnStay.getStyleClass().add("btn-primary"); // Applique le style vert sur le bouton rester.
        btnNewRound = new Button("Nouvelle manche"); // Bouton pour recommencer.
        btnNewRound.getStyleClass().add("btn-primary"); // Applique le style vert sur le bouton nouvelle manche.
        btnChangeBet = new Button("Changer mise"); // Bouton pour ouvrir l'éditeur de mise.
        btnChangeBet.getStyleClass().add("btn-primary"); // Applique le style vert sur le bouton de changement de mise.

        HBox actions = new HBox(10, btnChangeBet, btnHit, btnStay, btnNewRound); // Regroupe les boutons d'action.
        actions.setAlignment(Pos.CENTER); // Centre la rangée.
        actions.setPadding(new Insets(10)); // Ajoute un espace interne.

        spBet = new Spinner<>(); // Crée le spinner d'édition de mise.
        spBet.setEditable(true); // Autorise la saisie clavier.
        spBet.setPrefWidth(120); // Définit une largeur lisible.
        spBet.getEditor().setTextFormatter(new TextFormatter<Integer>(change -> { // Ajoute un filtre pour les chiffres.
            return change.getText().matches("\\d*") ? change : null; // N'accepte que les chiffres.
        }));

        btnApplyBet = new Button("OK"); // Bouton pour confirmer la nouvelle mise.
        btnCloseBet = new Button("Fermer"); // Bouton pour fermer l'éditeur sans appliquer.
        Label lblBet = new Label("Mise (XPF)"); // Label descriptif du spinner.
        betEditor = new HBox(10, lblBet, spBet, btnApplyBet, btnCloseBet); // Assemble l'éditeur de mise.
        betEditor.setAlignment(Pos.CENTER); // Centre le contenu.
        betEditor.setPadding(new Insets(10)); // Ajoute un padding.
        betEditor.setVisible(false); // Cache l'éditeur par défaut.
        betEditor.getStyleClass().add("bet-editor"); // Applique le style semi-transparent pour l'encart d'édition.

        btnApplyBet.getStyleClass().add("btn-primary"); // Applique le style vert sur la validation de mise.
        btnCloseBet.getStyleClass().add("btn-close-small"); // Applique le style clair sur le bouton de fermeture.

        VBox bottom = new VBox(8, betEditor, actions); // Empile l'éditeur au-dessus des actions.
        bottom.setAlignment(Pos.CENTER); // Centre l'ensemble.
        root.setBottom(bottom); // Place le tout en bas de la scène.

        btnHit.setOnAction(e -> handlePlayerHit()); // Connecte l'action tirer.
        btnStay.setOnAction(e -> handlePlayerStand()); // Connecte l'action rester.
        btnNewRound.setOnAction(e -> { // Déclare la création d'une nouvelle manche.
            if (!ensureBetAffordable()) { // Vérifie le solde.
                return; // Stoppe si insuffisant.
            }
            startNewRound(); // Relance une manche.
            redrawGame(); // Redessine l'état initial.
            if (round.isPlayerNaturalBlackjack()) { // Vérifie le blackjack naturel.
                finishRound(round.settle(currentBet)); // Clôture instantanément.
                redrawGame(); // Actualise l'affichage.
            }
            syncButtons(); // Met à jour l'état des boutons.
        });
        btnChangeBet.setOnAction(e -> openBetEditor()); // Affiche l'éditeur de mise.
        btnApplyBet.setOnAction(e -> applyBetEditor()); // Applique une nouvelle mise.
        btnCloseBet.setOnAction(e -> betEditor.setVisible(false)); // Ferme l'éditeur sans changement.
        spBet.getEditor().setOnAction(e -> applyBetEditor()); // Valide la mise avec Entrée.

        syncButtons(); // Initialise l'état des boutons.
        Scene scene = new Scene(root, 720, 640); // Crée la scène JavaFX.
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm()); // Applique la feuille de style.
        return scene; // Retourne la scène prête.
    }

    private void startNewRound() { // Prépare une nouvelle manche.
        resultMsg = ""; // Réinitialise le message affiché.
        round.start(); // Relance la logique de jeu.
        sessionId = database.startSession(userId); // Ouvre une session en base.
        database.placeBet(userId, sessionId, currentBet); // Débite la mise.
        refreshBalance(); // Actualise le solde après débit.
        syncButtons(); // Met à jour les boutons.
    }

    private void handlePlayerHit() { // Logique du bouton "Tirer".
        if (round.isSettled()) { // Vérifie que la manche est active.
            return; // Ignore si déjà terminée.
        }
        round.playerHit(); // Ajoute une carte à la main du joueur.
        if (round.playerTotal() >= 21) { // Si le joueur atteint ou dépasse 21.
            round.playDealerTurn(); // Laisse le croupier jouer pour révéler les cartes.
            finishRound(round.settle(currentBet)); // Termine la manche et crédite les gains.
        }
        redrawGame(); // Actualise le rendu.
        syncButtons(); // Ajuste les boutons.
    }

    private void handlePlayerStand() { // Logique du bouton "Rester".
        if (round.isSettled()) { // Vérifie que la manche est active.
            return; // Ignore si déjà terminée.
        }
        round.playerStand(); // Laisse le croupier jouer jusqu'à 17.
        finishRound(round.settle(currentBet)); // Calcule et applique le résultat.
        redrawGame(); // Actualise le rendu.
        syncButtons(); // Ajuste les boutons.
    }

    private void finishRound(RoundOutcome outcome) { // Applique le règlement de la manche.
        database.settle(userId, sessionId, outcome.payout(), outcome.result()); // Synchronise les finances en base.
        refreshBalance(); // Met à jour le solde pour le HUD.
        switch (outcome.result()) { // Adapte le message affiché.
            case "WIN" -> resultMsg = outcome.message(); // Message positif.
            case "LOSE" -> resultMsg = outcome.message(); // Message négatif.
            case "PUSH" -> resultMsg = outcome.message(); // Message neutre.
            default -> resultMsg = ""; // Aucun message dans les autres cas.
        }
    }

    private void syncButtons() { // Ajuste l'état des boutons en fonction de la manche.
        boolean settled = round.isSettled(); // Lit l'état courant.
        if (btnHit != null) { // Vérifie que les contrôles sont initialisés.
            btnHit.setDisable(settled); // Désactive Tirer si la manche est finie.
            btnStay.setDisable(settled); // Désactive Rester si la manche est finie.
            btnNewRound.setDisable(!settled); // Active Nouvelle manche uniquement après règlement.
            btnChangeBet.setDisable(!settled); // Autorise le changement de mise seulement après règlement.
        }
    }

    private void redrawGame() { // Redessine le plateau de jeu.
        if (gameCanvas != null && renderer != null) { // Vérifie que les composants sont disponibles.
            renderer.render(gameCanvas, round, balanceCached, resultMsg); // Confie le dessin au renderer.
        }
    }

    private void openBetEditor() { // Prépare l'éditeur de mise.
        int balance = balanceCached; // Récupère le solde actuel.
        int minBet = 250; // Mise minimale autorisée.
        int maxBet = Math.max(minBet, balance); // Calcule la mise maximale possible.
        SpinnerValueFactory<Integer> factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(minBet, maxBet, Math.min(Math.max(currentBet, minBet), maxBet), 50); // Crée la factory avec pas de 50.
        spBet.setValueFactory(factory); // Affecte la factory au spinner.
        spBet.getEditor().setText(String.valueOf(currentBet)); // Pré-remplit le champ texte.
        betEditor.setVisible(true); // Affiche l'éditeur.
        spBet.requestFocus(); // Positionne le focus sur le champ.
    }

    private void applyBetEditor() { // Applique la nouvelle mise saisie.
        String raw = spBet.getEditor().getText().replaceAll("\\D", ""); // Extrait uniquement les chiffres.
        if (raw.isEmpty()) { // Vérifie qu'une valeur est présente.
            return; // Ignore si la saisie est vide.
        }
        int value = Integer.parseInt(raw); // Convertit la chaîne en entier.
        int minBet = 250; // Mise minimale autorisée.
        int step = 50; // Pas d'incrément.
        int balance = balanceCached; // Solde actuel.
        int maxBet = Math.max(minBet, balance); // Mise maximale possible.
        value = Math.max(minBet, Math.min(maxBet, (value / step) * step)); // Aligne la valeur sur le pas et la borne.
        currentBet = value; // Enregistre la nouvelle mise.
        resultMsg = "Mise fixée à " + currentBet + " XPF"; // Informe le joueur de la modification.
        betEditor.setVisible(false); // Ferme l'éditeur.
        redrawGame(); // Met à jour l'affichage.
    }

    private boolean ensureBetAffordable() { // Vérifie que la mise actuelle est compatible avec le solde.
        int balance = balanceCached; // Récupère le solde.
        int minBet = 250; // Mise minimale.
        if (balance < minBet) { // Vérifie que le solde atteint le minimum.
            alert("Solde insuffisant (min 250 XPF)."); // Affiche une alerte.
            return false; // Interrompt le processus.
        }
        if (currentBet > balance) { // Ajuste la mise si elle dépasse le solde.
            currentBet = Math.max(minBet, (balance / 50) * 50); // Ajuste la mise au pas de 50.
            alert("Mise ajustée à " + currentBet + " XPF."); // Informe le joueur.
        }
        return true; // La mise est acceptable.
    }

    private Dialog<Long> buildSignupDialog() { // Construit la boîte de dialogue d'inscription.
        Dialog<Long> dialog = new Dialog<>(); // Crée la boîte de dialogue.
        dialog.setTitle("Créer un compte"); // Définit le titre.
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); // Ajoute les boutons standard.

        TextField tfEmail = new TextField(); // Champ email.
        TextField tfPseudo = new TextField(); // Champ pseudo.
        PasswordField pf1 = new PasswordField(); // Champ mot de passe.
        PasswordField pf2 = new PasswordField(); // Champ confirmation.
        tfEmail.setPromptText("Email"); // Placeholder email.
        tfPseudo.setPromptText("Pseudo"); // Placeholder pseudo.
        pf1.setPromptText("Mot de passe"); // Placeholder mot de passe.
        pf2.setPromptText("Confirmer"); // Placeholder confirmation.

        VBox content = new VBox(8, new Label("Email"), tfEmail, new Label("Pseudo"), tfPseudo, new Label("Mot de passe"), pf1, new Label("Confirmer"), pf2); // Assemble les champs.
        content.setPadding(new Insets(20)); // Ajoute du padding.
        dialog.getDialogPane().setContent(content); // Place le contenu dans la boîte.

        dialog.setResultConverter(button -> { // Définit la conversion du résultat.
            if (button != ButtonType.OK) { // Si l'utilisateur annule.
                return null; // Retourne null pour ignorer.
            }
            String email = tfEmail.getText().trim(); // Récupère l'email.
            String pseudo = tfPseudo.getText().trim(); // Récupère le pseudo.
            String p1 = pf1.getText(); // Récupère le mot de passe.
            String p2 = pf2.getText(); // Récupère la confirmation.
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { // Valide l'email.
                alert("Email invalide"); // Informe l'utilisateur.
                return null; // Annule la création.
            }
            if (!p1.equals(p2) || p1.length() < 12 || !p1.matches(".*[A-Z].*") || !p1.matches(".*[a-z].*") || !p1.matches(".*\\d.*") || !p1.matches(".*[^A-Za-z0-9].*")) { // Vérifie la robustesse du mot de passe.
                alert("Mot de passe trop faible"); // Informe l'utilisateur.
                return null; // Annule la création.
            }
            try { // Tente la création du compte.
                return database.createUser(email, pseudo, p1); // Retourne l'identifiant créé.
            } catch (RuntimeException ex) { // Capture les erreurs métier.
                alert("Erreur: " + ex.getMessage()); // Affiche le message d'erreur.
                return null; // Annule le résultat.
            }
        });
        return dialog; // Retourne la boîte prête.
    }

    private void refreshBalance() { // Met à jour le solde en cache.
        balanceCached = database.getBalance(userId); // Interroge la base.
    }

    private void switchScene(Scene scene) { // Change la scène affichée.
        stage.setScene(scene); // Applique la nouvelle scène.
        stage.centerOnScreen(); // Centre la fenêtre sur l'écran.
    }

    private void alert(String message) { // Affiche une boîte d'information.
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait(); // Affiche l'alerte et attend la fermeture.
    }

    public static void main(String[] args) { // Point d'entrée standard.
        launch(args); // Démarre l'application JavaFX.
    }
}
