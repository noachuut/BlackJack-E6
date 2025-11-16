# BlackjackNC

![Aperçu du jeu](screenshot.png)

## Présentation du projet

BlackjackNC est une application JavaFX proposant une version moderne et interactive du jeu de Blackjack.  
Elle intègre une authentification, une gestion des sessions, un suivi de la balance du joueur et une interface graphique complète.

Ce projet a été réalisé dans le cadre du BTS SIO SLAM afin de démontrer la conception d’une application Java avec interface graphique, logique métier et gestion d’une base de données locale.

## Fonctionnalités principales

### Interface utilisateur
- Interface entièrement réalisée en JavaFX.
- Affichage dynamique des cartes et des actions.
- Affichage.
- Fenêtre unique regroupant authentification, mise et jeu.

### Authentification et sessions
- Formulaire de connexion JavaFX.
- Vérification des identifiants via une base SQLite.
- Création et gestion de sessions individuelles.
- Sauvegarde automatique de la balance du joueur.

### Gestion du jeu de Blackjack
- Distribution automatique des cartes.
- Révélation de la main du croupier uniquement à la fin.
- Gestion complète des issues : victoire, défaite, égalité.
- Mise minimale de 250 crédits

### Base de données
- SQLite embarqué.
- Table des utilisateurs.
- Table des sessions et historique.
- Sauvegarde des soldes.

### Build et exécutable
- Projet packagé en .msi.
- Compatibilité Windows.
- Toutes les dépendances JavaFX intégrées.

## Technologies utilisées

- Java 21  
- JavaFX  
- SQLite  
- JDBC  
- Maven  
- jpackage pour l’installeur Windows
