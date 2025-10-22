package org.example.game; // Déclare le package métier du blackjack.

public final class BlackjackRound { // Classe centrale gérant une manche complète.
    private Deck deck; // Pioche utilisée pour la manche en cours.
    private final Hand dealer = new Hand(); // Main visible du croupier.
    private final Hand player = new Hand(); // Main du joueur.
    private Card hiddenCard; // Carte face cachée du croupier.
    private boolean dealerRevealed; // Indique si la carte cachée est révélée.
    private boolean settled; // Indique si la manche est terminée.

    public void start() { // Initialise une nouvelle manche.
        deck = Deck.shuffled(); // Construit et mélange un nouveau paquet.
        dealer.clear(); // Vide la main du croupier.
        player.clear(); // Vide la main du joueur.
        hiddenCard = deck.draw(); // Tire la carte face cachée du croupier.
        dealer.add(deck.draw()); // Ajoute la carte visible du croupier.
        player.add(deck.draw()); // Ajoute la première carte du joueur.
        player.add(deck.draw()); // Ajoute la deuxième carte du joueur.
        dealerRevealed = false; // Masque la carte cachée au début de manche.
        settled = false; // Marque la manche comme non terminée.
    }

    public Hand dealerHand() { // Fournit la main visible du croupier.
        return dealer; // Retourne l'objet Hand pour consultation externe.
    }

    public Hand playerHand() { // Fournit la main du joueur.
        return player; // Retourne l'objet Hand correspondant au joueur.
    }

    public Card hiddenCard() { // Accède à la carte cachée actuelle.
        return hiddenCard; // Retourne la référence de la carte cachée.
    }

    public boolean hasHiddenCard() { // Indique si une carte cachée existe.
        return hiddenCard != null; // Vérifie la présence de la carte cachée.
    }

    public boolean isDealerRevealed() { // Informe si la carte cachée est visible.
        return dealerRevealed; // Retourne l'état d'affichage du croupier.
    }

    public boolean isSettled() { // Informe si la manche est terminée.
        return settled; // Retourne l'état courant de résolution.
    }

    public Card playerHit() { // Permet au joueur de tirer une carte.
        ensureOngoing(); // Vérifie que la manche n'est pas terminée.
        Card drawn = deck.draw(); // Retire une carte du paquet.
        player.add(drawn); // Ajoute la carte à la main du joueur.
        return drawn; // Retourne la carte tirée pour information.
    }

    public void playerStand() { // Gère l'action de rester du joueur.
        ensureOngoing(); // S'assure que la manche est active.
        playDealerTurn(); // Lance le tour du croupier.
    }

    public void playDealerTurn() { // Fait jouer le croupier jusqu'à 17.
        revealDealer(); // Retourne la carte cachée.
        while (dealer.total(hiddenCard) < 17) { // Tant que le total du croupier est inférieur à 17.
            dealer.add(deck.draw()); // Le croupier tire une carte supplémentaire.
        }
    }

    public void revealDealer() { // Révèle explicitement la carte cachée.
        dealerRevealed = true; // Met à jour le drapeau d'affichage.
    }

    public int playerTotal() { // Calcule le total actuel du joueur.
        return player.total(); // Utilise Hand pour évaluer la main du joueur.
    }

    public int dealerVisibleTotal() { // Calcule le total des cartes visibles du croupier.
        return dealer.total(); // Additionne uniquement les cartes visibles.
    }

    public int dealerTotal() { // Calcule le total complet du croupier.
        return dealer.total(hiddenCard); // Inclut la carte cachée si elle existe.
    }

    public boolean isPlayerNaturalBlackjack() { // Détecte un blackjack naturel côté joueur.
        return player.size() == 2 && player.total() == 21; // Vérifie deux cartes et un total de 21.
    }

    public boolean isDealerNaturalBlackjack() { // Détecte un blackjack naturel côté croupier.
        return hiddenCard != null && dealer.size() == 1 && dealer.total(hiddenCard) == 21; // Vérifie la configuration initiale.
    }

    public RoundOutcome settle(int bet) { // Termine la manche et calcule le résultat financier.
        ensureOngoing(); // Empêche une double résolution.
        revealDealer(); // Affiche la carte du croupier pour la fin de manche.
        int playerScore = player.total(); // Calcule le score du joueur.
        int dealerScore = dealer.total(hiddenCard); // Calcule le score complet du croupier.
        boolean playerBJ = isPlayerNaturalBlackjack(); // Vérifie un blackjack naturel joueur.
        boolean dealerBJ = isDealerNaturalBlackjack(); // Vérifie un blackjack naturel croupier.

        String result; // Prépare l'étiquette de résultat.
        int payout; // Prépare le montant à créditer.
        String message; // Prépare le message utilisateur.

        if (playerScore > 21) { // Cas où le joueur dépasse 21.
            result = "LOSE"; // Le joueur perd.
            payout = 0; // Aucun gain n'est versé.
            message = "Tu as dépassé 21, tu as perdu"; // Message spécifique à la défaite par dépassement.
        } else if (playerBJ && dealerBJ) { // Cas de double blackjack.
            result = "PUSH"; // Manche nulle.
            payout = bet; // Le joueur récupère sa mise.
            message = "Égalité"; // Message neutre.
        } else if (playerBJ) { // Blackjack joueur uniquement.
            result = "WIN"; // Le joueur gagne.
            payout = (int) Math.round(bet * 2.5); // Gain 3:2.
            message = "Blackjack ! Tu as gagné"; // Message enthousiaste.
        } else if (dealerBJ) { // Blackjack croupier uniquement.
            result = "LOSE"; // Le joueur perd.
            payout = 0; // Aucun gain.
            message = "Le croupier a un blackjack, tu as perdu"; // Message explicite.
        } else if (dealerScore > 21) { // Croupier dépasse 21.
            result = "WIN"; // Le joueur gagne.
            payout = bet * 2; // Gain standard.
            message = "Tu as gagné"; // Message positif générique.
        } else if (playerScore == dealerScore) { // Totaux identiques.
            result = "PUSH"; // Manche nulle.
            payout = bet; // Retour de la mise.
            message = "Égalité"; // Message neutre.
        } else if (playerScore > dealerScore) { // Joueur supérieur au croupier.
            result = "WIN"; // Le joueur gagne.
            payout = bet * 2; // Gain standard.
            message = "Tu as gagné"; // Message positif.
        } else { // Tous les autres cas.
            result = "LOSE"; // Le joueur perd.
            payout = 0; // Aucun gain.
            message = "Tu as perdu"; // Message négatif générique.
        }

        settled = true; // Marque la manche comme terminée.
        return new RoundOutcome(result, payout, message); // Retourne l'issue de la manche.
    }

    private void ensureOngoing() { // Vérifie que la manche est encore active.
        if (settled) { // Teste l'état de résolution.
            throw new IllegalStateException("Manche déjà terminée"); // Signale la double invocation.
        }
    }
}
