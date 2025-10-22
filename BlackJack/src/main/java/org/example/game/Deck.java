package org.example.game; // Déclare le package des classes métiers liées au jeu.

import java.security.SecureRandom; // Utilise SecureRandom pour un mélange robuste.
import java.util.ArrayList; // Importe ArrayList pour stocker les cartes.
import java.util.Collections; // Importe Collections pour mélanger la pioche.
import java.util.List; // Importe List pour le type de collection générique.

public final class Deck { // Définit la classe Deck comme finale pour représenter un paquet de cartes.
    private static final String[] VALUES = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"}; // Tableau des valeurs possibles.
    private static final String[] SUITS = {"D","H","S","C"}; // Tableau des couleurs possibles.
    private final List<Card> cards; // Stocke les cartes disponibles dans la pioche.

    private Deck(List<Card> cards) { // Constructeur privé pour imposer l'utilisation des fabriques.
        this.cards = cards; // Affecte la liste de cartes au paquet.
    }

    public static Deck shuffled() { // Fabrique statique créant un paquet mélangé complet.
        List<Card> all = new ArrayList<>(52); // Prépare la liste qui contiendra les 52 cartes.
        for (String suit : SUITS) { // Parcourt chaque couleur.
            for (String value : VALUES) { // Parcourt chaque valeur.
                all.add(new Card(value, suit)); // Ajoute la combinaison valeur/couleur à la liste.
            }
        }
        Collections.shuffle(all, new SecureRandom()); // Mélange la liste avec un générateur sécurisé.
        return new Deck(all); // Retourne une nouvelle instance du paquet mélangé.
    }

    public Card draw() { // Retire et retourne la dernière carte disponible.
        if (cards.isEmpty()) { // Vérifie qu'il reste des cartes.
            throw new IllegalStateException("Deck vide"); // Signale l'erreur si la pioche est vide.
        }
        return cards.remove(cards.size() - 1); // Supprime et retourne la carte au sommet de la pioche.
    }

    public int remaining() { // Indique le nombre de cartes restantes.
        return cards.size(); // Retourne la taille actuelle de la liste.
    }
}
