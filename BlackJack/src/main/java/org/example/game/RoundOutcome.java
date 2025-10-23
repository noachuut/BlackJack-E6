package org.example.game; // Déclare le package métier.

public record RoundOutcome(String result, int payout, String message) { // Record immuable décrivant l'issue d'une manche.
}
