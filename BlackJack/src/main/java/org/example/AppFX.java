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
        stage.setWidth(600); // Positionne une largeur par défaut.
        stage.setHeight(600); // Positionne une hauteur par défaut.
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
        BorderPane root = new BorderPane(); // Crée le conteneur principal.
        root.setPadding(new Insets(40)); // Ajoute un padding généreux.
        root.getStyleClass().add("login-root"); // Applique le fond vert texturé défini dans la feuille de style.

        VBox form = new VBox(12); // Conteneur vertical pour les champs.
        form.setAlignment(Pos.CENTER); // Centre les éléments.
        form.setPadding(new Insets(24, 32, 32, 32)); // Ajoute du padding pour évoquer une carte centrée.
        form.setMaxWidth(360); // Limite la largeur pour conserver la mise en page d'origine.
        form.getStyleClass().add("login-card"); // Applique le style crème arrondi de la carte de connexion.

        Label title = new Label("Connexion"); // Titre de la section.
        title.getStyleClass().add("login-title"); // Utilise la classe dédiée au grand titre crème.

        TextField tfEmail = new TextField(); // Champ email.
        tfEmail.setPromptText("Email"); // Placeholder du champ email.
        tfEmail.getStyleClass().add("input-cream"); // Applique le style crème et arrondi au champ email.

        PasswordField pfPassword = new PasswordField(); // Champ mot de passe masqué.
        pfPassword.setPromptText("Mot de passe"); // Placeholder du champ mot de passe.
        pfPassword.getStyleClass().add("input-cream"); // Applique le style crème au champ mot de passe.

        Label message = new Label(); // Label pour afficher les erreurs.
        message.getStyleClass().add("login-msg"); // Utilise le style rouge doux prévu pour les messages.

        Button btnLogin = new Button("Se connecter"); // Bouton de connexion.
        btnLogin.getStyleClass().add("btn-primary"); // Applique le style principal vert.
        Button btnSignup = new Button("Créer un compte"); // Bouton d'inscription.
        btnSignup.getStyleClass().addAll("btn-primary", "btn-soft"); // Applique la variante douce pour le second bouton.
        HBox actions = new HBox(10, btnLogin, btnSignup); // Regroupe les boutons.
        actions.setAlignment(Pos.CENTER); // Centre la rangée de boutons.

        form.getChildren().addAll(title, tfEmail, pfPassword, actions, message); // Assemble le formulaire.
        root.setCenter(form); // Place le formulaire au centre.

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

        Scene scene = new Scene(root, 600, 600); // Crée la scène JavaFX.
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm()); // Ajoute la feuille de style existante.
        return scene; // Retourne la scène prête.
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
