package org.example.ui; // Déclare le package dédié à l'affichage.

import javafx.scene.canvas.Canvas; // Importe Canvas pour cibler la zone de dessin.
import javafx.scene.canvas.GraphicsContext; // Importe GraphicsContext pour dessiner.
import javafx.scene.image.Image; // Importe Image pour afficher les cartes et fonds.
import javafx.scene.paint.Color; // Importe Color pour les remplissages.
import javafx.scene.text.Font; // Importe Font pour paramétrer la typographie.
import javafx.scene.text.Text; // Importe Text pour mesurer la largeur de chaîne.
import org.example.game.BlackjackRound; // Importe la logique de manche.
import org.example.game.Card; // Importe la représentation des cartes.
import org.example.game.Hand; // Importe les mains du jeu.

import java.util.Objects; // Importe Objects pour sécuriser les ressources.

public final class GameRenderer { // Classe responsable du rendu du plateau.
    private static final double CARD_W = 110; // Largeur standardisée d'une carte.
    private static final double CARD_H = 160; // Hauteur standardisée d'une carte.
    private static final double CARD_SPACING_FACTOR = 0.35; // Taux de recouvrement des cartes.
    private static final Text PROBE = new Text(); // Objet partagé pour mesurer le texte.

    private final Image tableImage; // Image de fond du tapis.
    private final Image cardBack; // Image du dos de carte.

    public GameRenderer(Image tableImage, Image cardBack) { // Constructeur injectant les ressources graphiques.
        this.tableImage = tableImage; // Stocke l'image du tapis.
        this.cardBack = cardBack; // Stocke l'image du dos de carte.
    }

    public void render(Canvas canvas, BlackjackRound round, int balance, String message) { // Dessine la scène de jeu complète.
        GraphicsContext g = canvas.getGraphicsContext2D(); // Récupère le contexte de dessin 2D.
        double width = canvas.getWidth(); // Récupère la largeur actuelle du canvas.
        double height = canvas.getHeight(); // Récupère la hauteur actuelle du canvas.

        g.clearRect(0, 0, width, height); // Efface le contenu précédent.
        if (tableImage != null) { // Vérifie si un tapis personnalisé est disponible.
            g.drawImage(tableImage, 0, 0, width, height); // Dessine l'image de fond.
        } else { // Sinon, utilise un fond vert par défaut.
            g.setFill(Color.web("#35654d")); // Sélectionne un vert casino.
            g.fillRect(0, 0, width, height); // Remplit toute la surface.
        }

        drawDealer(g, round, 20, 20); // Dessine la main du croupier.
        drawPlayer(g, round.playerHand(), 20, 320); // Dessine la main du joueur.
        drawScores(g, round, 20, 20 + CARD_H + 30, 20, 320 + CARD_H + 30); // Affiche les scores.
        drawBalanceBadge(g, width, height, balance); // Dessine le badge de solde.
        drawResultBanner(g, width, height, round.isSettled(), message); // Affiche le bandeau de résultat.
    }

    private void drawPlayer(GraphicsContext g, Hand hand, double x, double y) { // Dessine les cartes du joueur.
        drawCards(g, hand.cards(), x, y); // Délègue au dessin générique de cartes.
    }

    private void drawDealer(GraphicsContext g, BlackjackRound round, double x, double y) { // Dessine les cartes du croupier.
        double offset = CARD_W * CARD_SPACING_FACTOR; // Calcule l'écart horizontal entre cartes.
        if (!round.isDealerRevealed() && round.hasHiddenCard()) { // Cas où la carte reste cachée.
            drawCards(g, round.dealerHand().cards(), x + offset, y); // Dessine les cartes visibles décalées.
            if (cardBack != null) { // Vérifie que l'image du dos est disponible.
                g.drawImage(cardBack, x, y, CARD_W, CARD_H); // Dessine le dos sur la gauche.
            } else { // Fallback si l'image est absente.
                g.setFill(Color.DARKGREEN); // Choisit une couleur de remplacement.
                g.fillRoundRect(x, y, CARD_W, CARD_H, 12, 12); // Dessine un rectangle symbolique.
            }
        } else { // Cas où toutes les cartes sont visibles.
            if (round.hasHiddenCard()) { // Vérifie qu'une carte cachée existe.
                drawCard(g, round.hiddenCard(), x, y); // Dessine la carte révélée à gauche.
            }
            drawCards(g, round.dealerHand().cards(), x + offset, y); // Dessine le reste des cartes.
        }
    }

    private void drawCards(GraphicsContext g, java.util.List<Card> cards, double x, double y) { // Dessine une liste de cartes.
        double offset = CARD_W * CARD_SPACING_FACTOR; // Calcule le décalage horizontal.
        for (int i = 0; i < cards.size(); i++) { // Parcourt chaque carte.
            drawCard(g, cards.get(i), x + i * offset, y); // Dessine la carte à la position calculée.
        }
    }

    private void drawCard(GraphicsContext g, Card card, double x, double y) { // Dessine une seule carte recto.
        Image img = new Image(Objects.requireNonNull(getClass().getResourceAsStream(card.imagePath()))); // Charge l'image associée.
        g.drawImage(img, x, y, CARD_W, CARD_H); // Dessine l'image aux dimensions standard.
    }

    private void drawScores(GraphicsContext g, BlackjackRound round, double dealerX, double dealerY, double playerX, double playerY) { // Dessine les totaux texte.
        g.setFill(Color.WHITE); // Utilise une couleur blanche lisible.
        g.setFont(Font.font("Arial", 20)); // Définit la police et la taille.
        String playerText = "Joueur : " + round.playerTotal(); // Prépare le texte du joueur.
        g.fillText(playerText, playerX, playerY); // Dessine le texte du joueur.
        if (round.isDealerRevealed()) { // Si le croupier est révélé.
            String dealerText = "Croupier : " + round.dealerTotal(); // Affiche le total complet.
            g.fillText(dealerText, dealerX, dealerY); // Dessine le texte du croupier.
        } else { // Sinon, masque la carte cachée.
            String dealerText = "Croupier : ? + " + round.dealerVisibleTotal(); // Affiche la forme ? + total visible.
            g.fillText(dealerText, dealerX, dealerY); // Dessine le texte masqué.
        }
    }

    private void drawBalanceBadge(GraphicsContext g, double width, double height, int balance) { // Dessine le badge de solde.
        String text = "Solde  " + balance + " XPF"; // Compose le texte à afficher.
        g.setFont(Font.font("Arial", 16)); // Configure la police.
        double textWidth = measure(text, g.getFont()); // Mesure la largeur du texte.
        double paddingX = 14; // Définit le padding horizontal.
        double radius = 14; // Définit le rayon des coins arrondis.
        double badgeWidth = textWidth + paddingX * 2 + 18; // Calcule la largeur du badge.
        double badgeHeight = 30; // Fixe la hauteur du badge.
        double x = width - badgeWidth - 12; // Position horizontale dans le coin droit.
        double y = 10; // Position verticale près du bord supérieur.

        g.setFill(Color.rgb(0, 0, 0, 0.45)); // Définit le fond semi-transparent.
        g.fillRoundRect(x, y, badgeWidth, badgeHeight, radius, radius); // Dessine le fond arrondi.
        g.setStroke(Color.rgb(255, 255, 255, 0.6)); // Configure le trait clair.
        g.setLineWidth(2); // Définit l'épaisseur du trait.
        g.strokeRoundRect(x, y, badgeWidth, badgeHeight, radius, radius); // Dessine le contour.

        double coinDiameter = badgeHeight - 10; // Calcule le diamètre du jeton décoratif.
        double coinX = x + 8; // Calcule la position horizontale du jeton.
        double coinY = y + (badgeHeight - coinDiameter) / 2; // Calcule la position verticale du jeton.
        g.setFill(Color.web("#D6AA3C")); // Choisit une teinte dorée.
        g.fillOval(coinX, coinY, coinDiameter, coinDiameter); // Dessine le disque doré.
        g.setStroke(Color.web("#785A1E")); // Choisit la couleur du contour du jeton.
        g.strokeOval(coinX, coinY, coinDiameter, coinDiameter); // Dessine le contour du disque.

        g.setFill(Color.web("#F5F2E6")); // Choisit une couleur claire pour le texte.
        g.fillText(text, coinX + coinDiameter + 8, y + badgeHeight / 2 + 6); // Dessine le texte aligné.
    }

    private void drawResultBanner(GraphicsContext g, double width, double height, boolean settled, String message) { // Dessine le bandeau de résultat.
        if (!settled || message == null || message.isBlank()) { // Vérifie qu'il y a un message pertinent.
            return; // Abandonne si rien n'est à afficher.
        }
        g.setFill(Color.rgb(0, 0, 0, 0.55)); // Définit le fond sombre translucide.
        double bannerWidth = width * 0.9; // Calcule la largeur du bandeau.
        double bannerHeight = 70; // Définit la hauteur du bandeau.
        double bx = (width - bannerWidth) / 2; // Centre le bandeau horizontalement.
        double by = height - bannerHeight - 12; // Positionne le bandeau au bas de l'écran.
        g.fillRoundRect(bx, by, bannerWidth, bannerHeight, 16, 16); // Dessine le rectangle arrondi.

        g.setFont(Font.font("Arial", 24)); // Définit la police du texte.
        g.setFill(Color.WHITE); // Définit la couleur du texte.
        double textWidth = measure(message, g.getFont()); // Mesure la largeur du message.
        double tx = Math.max(bx + (bannerWidth - textWidth) / 2, bx + 16); // Calcule la position horizontale.
        double ty = by + bannerHeight / 2 + 8; // Calcule la position verticale.
        g.fillText(message, tx, ty); // Dessine le message.
    }

    private double measure(String text, Font font) { // Mesure la largeur d'une chaîne avec une police donnée.
        PROBE.setText(text); // Affecte le texte au probe.
        PROBE.setFont(font); // Affecte la police au probe.
        return PROBE.getLayoutBounds().getWidth(); // Retourne la largeur mesurée.
    }
}
