package org.example.game; // Déclare le package métier du jeu.

import java.util.ArrayList; // Importe ArrayList pour stocker les cartes.
import java.util.Collections; // Importe Collections pour exposer une vue non modifiable.
import java.util.List; // Importe List comme interface générique.

public final class Hand { // Définit la classe Hand pour représenter une main de cartes.
    private final List<Card> cards = new ArrayList<>(); // Initialise la liste interne des cartes.

    public void clear() { // Vide complètement la main.
        cards.clear(); // Supprime toutes les cartes de la liste.
    }

    public void add(Card card) { // Ajoute une carte à la main.
        cards.add(card); // Empile la carte à la fin de la liste.
    }

    public List<Card> cards() { // Expose les cartes actuelles sans permettre la modification.
        return Collections.unmodifiableList(cards); // Retourne une vue non modifiable de la liste.
    }

    public int size() { // Donne le nombre de cartes actuellement dans la main.
        return cards.size(); // Retourne la taille de la liste interne.
    }

    public int total(Card... extras) { // Calcule le total en incluant éventuellement des cartes supplémentaires.
        int sum = 0; // Initialise l'agrégat de points.
        int aces = 0; // Compte les as pour gérer leur valeur flexible.
        for (Card card : cards) { // Parcourt chaque carte de la main.
            sum += card.points(); // Ajoute les points de la carte.
            if (card.isAce()) { // Vérifie si la carte est un as.
                aces++; // Incrémente le compteur d'as.
            }
        }
        for (Card extra : extras) { // Parcourt les cartes additionnelles.
            if (extra == null) { // Ignore explicitement les références nulles.
                continue; // Passe à l'itération suivante.
            }
            sum += extra.points(); // Ajoute les points de la carte additionnelle.
            if (extra.isAce()) { // Vérifie si la carte additionnelle est un as.
                aces++; // Incrémente le compteur d'as.
            }
        }
        while (sum > 21 && aces > 0) { // Ajuste les as si la somme dépasse 21.
            sum -= 10; // Décrémente de 10 pour convertir un as de 11 à 1.
            aces--; // Réduit le compteur d'as restant.
        }
        return sum; // Retourne le total optimisé.
    }
}
