package org.example.game; // Déclare le package métier pour les éléments du jeu.

import java.util.Objects; // Importe Objects pour sécuriser les arguments non nuls.

public final class Card { // Définit la classe Card comme immuable pour représenter une carte.
    private final String value; // Stocke la valeur de la carte (A, 2..10, J, Q, K).
    private final String suit; // Stocke la couleur de la carte (D, H, S, C).

    public Card(String value, String suit) { // Constructeur initialisant la carte avec valeur et couleur.
        this.value = Objects.requireNonNull(value, "value"); // Vérifie et affecte la valeur.
        this.suit = Objects.requireNonNull(suit, "suit"); // Vérifie et affecte la couleur.
    }

    public String value() { // Accesseur pour lire la valeur brute.
        return value; // Retourne la valeur de la carte.
    }

    public String suit() { // Accesseur pour lire la couleur brute.
        return suit; // Retourne la couleur de la carte.
    }

    public int points() { // Calcule la valeur en points de la carte selon les règles du blackjack.
        if ("AJQK".contains(value)) { // Détermine si la carte est une figure ou un as.
            return "A".equals(value) ? 11 : 10; // Retourne 11 pour l'as, 10 pour les figures.
        } // Termine la condition sur les figures.
        return Integer.parseInt(value); // Convertit les valeurs numériques en entiers.
    }

    public boolean isAce() { // Indique si la carte est un as.
        return "A".equals(value); // Vérifie l'égalité avec "A".
    }

    public String imagePath() { // Construit le chemin de ressource de l'image PNG de la carte.
        return "/card/" + value + "-" + suit + ".png"; // Assemble le chemin standardisé.
    }

    @Override
    public String toString() { // Fournit une représentation texte utile pour le debug.
        return value + "-" + suit; // Concatène valeur et couleur avec un tiret.
    }
}
