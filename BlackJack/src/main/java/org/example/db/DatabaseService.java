package org.example.db; // Déclare le package dédié aux opérations base de données.

import org.example.SecurityUtil; // Importe l'utilitaire de hachage de mot de passe.

import java.nio.file.Files; // Importe Files pour manipuler le système de fichiers.
import java.nio.file.Path; // Importe Path pour représenter les chemins.
import java.sql.*; // Importe les classes JDBC nécessaires.

public final class DatabaseService { // Service regroupant toutes les opérations SQL.
    private final Connection connection; // Connexion JDBC active.

    public DatabaseService() { // Constructeur initialisant la connexion.
        this.connection = openConnection(); // Ouvre la connexion SQLite.
        ensureSchema(); // Crée les tables et triggers si besoin.
    }

    private Connection openConnection() { // Établit la connexion SQLite.
        try { // Tente l'ouverture de la base.
            Class.forName("org.sqlite.JDBC"); // Charge le driver JDBC SQLite.
            String dbPath = resolveDbPath(); // Calcule le chemin du fichier base.
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath + "?foreign_keys=on"); // Retourne la connexion configurée.
        } catch (Exception e) { // Capture toute erreur.
            throw new RuntimeException("DB open", e); // Enveloppe l'exception dans une RuntimeException.
        }
    }

    private String resolveDbPath() { // Détermine le chemin du fichier SQLite.
        String base = System.getenv("APPDATA") + "\\Blackjack"; // Localise le dossier applicatif.
        try { // Tente de créer le dossier s'il n'existe pas.
            Files.createDirectories(Path.of(base)); // Crée la hiérarchie nécessaire.
        } catch (Exception ignore) { // Ignore les erreurs de création.
        }
        return base + "\\blackjack.db"; // Retourne le chemin du fichier base.
    }

    private void ensureSchema() { // Crée les tables et triggers requis.
        String[] ddl = new String[] { // Tableau contenant chaque instruction SQL.
                "PRAGMA foreign_keys = ON", // Active les clés étrangères.
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
        """, // Définition de la table utilisateur.
                """
        CREATE TABLE IF NOT EXISTS wallet (
          id_utilisateur     INTEGER PRIMARY KEY,
          solde_actuel       INTEGER NOT NULL,
          last_daily_credit  TEXT NOT NULL,
          created_at         TEXT NOT NULL DEFAULT (datetime('now')),
          updated_at         TEXT NOT NULL DEFAULT (datetime('now')),
          FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE
        )
        """, // Définition de la table wallet.
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
        """, // Définition de la table des sessions.
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
        """, // Définition de la table des transactions.
                """
        CREATE TRIGGER IF NOT EXISTS trg_user_init_wallet
        AFTER INSERT ON utilisateur
        BEGIN
          INSERT INTO wallet(id_utilisateur, solde_actuel, last_daily_credit, created_at, updated_at)
          VALUES (NEW.id_utilisateur, 10000, date('now'), datetime('now'), datetime('now'));

          INSERT INTO txn(id_utilisateur, id_session, type, montant, solde_avant, solde_apres, note)
          VALUES (NEW.id_utilisateur, NULL, 'INIT', 10000, 0, 10000, 'Solde initial');
        END;
        """, // Trigger initialisant le portefeuille.
                """
        CREATE TRIGGER IF NOT EXISTS trg_tx_commit
        AFTER INSERT ON txn
        BEGIN
          UPDATE wallet
             SET solde_actuel = NEW.solde_apres,
                 updated_at   = datetime('now')
           WHERE id_utilisateur = NEW.id_utilisateur;
        END;
        """ // Trigger maintenant le solde cohérent.
        }; // Termine le tableau DDL.

        try (Statement st = connection.createStatement()) { // Utilise un Statement pour exécuter chaque requête.
            connection.setAutoCommit(false); // Passe la connexion en mode transactionnel.
            for (String sql : ddl) { // Parcourt chaque instruction.
                String query = sql.trim(); // Nettoie les espaces superflus.
                if (!query.isEmpty()) { // Ignore les chaînes vides.
                    st.executeUpdate(query); // Exécute l'instruction SQL.
                }
            }
            connection.commit(); // Valide la transaction.
        } catch (SQLException e) { // Capture les erreurs SQL.
            try { connection.rollback(); } catch (SQLException ignore) { } // Annule en cas d'erreur.
            throw new RuntimeException("schema", e); // Rejette sous forme d'exception runtime.
        } finally { // Bloc exécuté dans tous les cas.
            try { connection.setAutoCommit(true); } catch (SQLException ignore) { } // Restaure l'auto-commit.
        }
    }

    public record UserCredentials(long id, String hash) { } // Record exposant l'identifiant et le hash.

    public UserCredentials findUserByEmail(String email) { // Recherche un utilisateur par email.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id_utilisateur, hash_mdp FROM utilisateur WHERE email=?")) { // Prépare la requête.
            ps.setString(1, email); // Injecte l'email.
            try (ResultSet rs = ps.executeQuery()) { // Exécute la requête.
                if (rs.next()) { // Si une ligne est trouvée.
                    return new UserCredentials(rs.getLong(1), rs.getString(2)); // Retourne les informations utiles.
                }
                return null; // Aucun utilisateur trouvé.
            }
        } catch (SQLException e) { // Capture les erreurs SQL.
            throw new RuntimeException(e); // Propage l'erreur.
        }
    }

    public long createUser(String email, String pseudo, String rawPwd) { // Crée un utilisateur complet.
        String hash = SecurityUtil.hashPwd(rawPwd); // Hache le mot de passe.
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO utilisateur(email,pseudo,hash_mdp) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) { // Prépare l'insertion avec récupération d'identifiant.
            ps.setString(1, email); // Dépose l'email.
            ps.setString(2, pseudo); // Dépose le pseudo.
            ps.setString(3, hash); // Dépose le hash du mot de passe.
            ps.executeUpdate(); // Exécute l'insertion.
            try (ResultSet rs = ps.getGeneratedKeys()) { // Récupère l'identifiant généré.
                if (rs.next()) { // Vérifie la présence de la clé.
                    return rs.getLong(1); // Retourne l'identifiant.
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) { // Repli si le driver ne supporte pas getGeneratedKeys.
                if (rs.next()) { // Vérifie l'existence d'une ligne.
                    return rs.getLong(1); // Retourne l'identifiant récupéré.
                }
            }
            throw new RuntimeException("createUser: no id"); // Signale l'impossibilité de récupérer l'identifiant.
        } catch (SQLException e) { // Capture les erreurs SQL.
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) { // Détecte un doublon.
                throw new RuntimeException("Cet email est déjà utilisé."); // Lève une exception lisible.
            }
            throw new RuntimeException(e); // Propage l'erreur générique.
        }
    }

    public void applyDailyCredit(long userId) { // Crédite le bonus quotidien si nécessaire.
        try { // Enveloppe l'opération dans une transaction.
            boolean previous = connection.getAutoCommit(); // Sauvegarde l'état auto-commit.
            connection.setAutoCommit(false); // Désactive l'auto-commit.
            try (PreparedStatement ps1 = connection.prepareStatement("""
                    WITH w AS (SELECT id_utilisateur, solde_actuel, last_daily_credit FROM wallet WHERE id_utilisateur = ?)
                    INSERT INTO txn (id_utilisateur, id_session, type, montant, solde_avant, solde_apres, note)
                    SELECT ?, NULL, 'DAILY_CREDIT', 1000, w.solde_actuel, w.solde_actuel + 1000, 'Crédit quotidien'
                    FROM w WHERE date(w.last_daily_credit) < date('now');
                """
            );
                 PreparedStatement ps2 = connection.prepareStatement("""
                    UPDATE wallet SET last_daily_credit=date('now'), updated_at=datetime('now')
                    WHERE id_utilisateur=? AND date(last_daily_credit) < date('now');
                """)) { // Prépare les deux requêtes.
                ps1.setLong(1, userId); // Paramètre la première requête (sous-requête).
                ps1.setLong(2, userId); // Paramètre l'insertion.
                ps1.executeUpdate(); // Exécute la tentative de crédit.
                ps2.setLong(1, userId); // Paramètre la mise à jour du portefeuille.
                ps2.executeUpdate(); // Exécute la mise à jour.
                connection.commit(); // Valide la transaction.
            } catch (Exception ex) { // Capture toute erreur durant les requêtes.
                connection.rollback(); // Annule la transaction.
                throw ex; // Re-propage l'exception.
            } finally { // Bloc de sortie.
                connection.setAutoCommit(previous); // Restaure l'état initial.
            }
        } catch (SQLException e) { // Capture les erreurs SQL.
            throw new RuntimeException("daily", e); // Propage l'erreur.
        }
    }

    public int getBalance(long userId) { // Récupère le solde actuel.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT solde_actuel FROM wallet WHERE id_utilisateur=?")) { // Prépare la requête.
            ps.setLong(1, userId); // Paramètre l'identifiant.
            try (ResultSet rs = ps.executeQuery()) { // Exécute la requête.
                return rs.next() ? rs.getInt(1) : 0; // Retourne le solde ou 0 si absent.
            }
        } catch (SQLException e) { // Capture les erreurs SQL.
            throw new RuntimeException(e); // Propage l'erreur.
        }
    }

    public long startSession(long userId) { // Crée une nouvelle session de jeu.
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO session_jeu(id_utilisateur) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) { // Prépare l'insertion.
            ps.setLong(1, userId); // Paramètre l'utilisateur.
            ps.executeUpdate(); // Exécute l'insertion.
            try (ResultSet rs = ps.getGeneratedKeys()) { // Récupère l'identifiant de session.
                if (rs.next()) { // Vérifie la présence d'une clé.
                    return rs.getLong(1); // Retourne l'identifiant.
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) { // Repli.
                if (rs.next()) { // Vérifie la présence d'une ligne.
                    return rs.getLong(1); // Retourne l'identifiant.
                }
            }
            throw new RuntimeException("startSession: no id"); // Signale l'échec.
        } catch (SQLException e) { // Capture les erreurs SQL.
            throw new RuntimeException("startSession", e); // Propage l'erreur annotée.
        }
    }

    public void placeBet(long userId, long sessionId, int amount) { // Débite la mise du joueur.
        if (amount <= 0) { // Valide le montant.
            throw new IllegalArgumentException("amount > 0"); // Refuse les montants invalides.
        }
        try { // Ouvre une transaction.
            int before = getBalance(userId); // Récupère le solde avant mise.
            int after = before - amount; // Calcule le solde après débit.
            if (after < 0) { // Vérifie que la mise est possible.
                throw new RuntimeException("Solde insuffisant (" + before + ")"); // Signale le solde insuffisant.
            }
            boolean previous = connection.getAutoCommit(); // Sauvegarde l'état auto-commit.
            connection.setAutoCommit(false); // Désactive l'auto-commit.
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO txn(id_utilisateur,id_session,type,montant,solde_avant,solde_apres,note) VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement ps2 = connection.prepareStatement(
                         "UPDATE session_jeu SET mise_totale = mise_totale + ? WHERE id_session=?")) { // Prépare les requêtes.
                ps.setLong(1, userId); // Paramètre l'identifiant utilisateur.
                ps.setLong(2, sessionId); // Paramètre la session.
                ps.setString(3, "BET"); // Indique le type de transaction.
                ps.setInt(4, -amount); // Enregistre le montant débité.
                ps.setInt(5, before); // Solde avant.
                ps.setInt(6, after); // Solde après.
                ps.setString(7, "Mise"); // Ajoute une note.
                ps.executeUpdate(); // Insère la transaction.
                ps2.setInt(1, amount); // Paramètre la mise cumulée.
                ps2.setLong(2, sessionId); // Paramètre la session.
                ps2.executeUpdate(); // Met à jour la session.
                connection.commit(); // Valide l'opération.
            } catch (Exception ex) { // Capture toute erreur pendant les requêtes.
                connection.rollback(); // Annule la transaction.
                throw ex; // Re-propage l'exception.
            } finally { // Bloc de sortie.
                connection.setAutoCommit(previous); // Restaure l'auto-commit.
            }
        } catch (SQLException e) { // Capture les erreurs SQL.
            throw new RuntimeException("placeBet", e); // Propage l'erreur annotée.
        }
    }

    public void settle(long userId, long sessionId, int delta, String result) { // Enregistre le résultat financier d'une manche.
        try { // Ouvre une transaction.
            int before = getBalance(userId); // Récupère le solde avant règlement.
            int after = before + delta; // Calcule le solde après règlement.
            boolean previous = connection.getAutoCommit(); // Sauvegarde l'état auto-commit.
            connection.setAutoCommit(false); // Désactive l'auto-commit.
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO txn(id_utilisateur,id_session,type,montant,solde_avant,solde_apres,note) VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement ps2 = connection.prepareStatement(
                         "UPDATE session_jeu SET gain_total=gain_total + ?, resultat=?, date_fin=datetime('now') WHERE id_session=?")) { // Prépare les requêtes.
                ps.setLong(1, userId); // Paramètre l'utilisateur.
                ps.setLong(2, sessionId); // Paramètre la session.
                ps.setString(3, "PAYOUT"); // Spécifie le type de transaction.
                ps.setInt(4, delta); // Enregistre le montant crédité.
                ps.setInt(5, before); // Solde avant règlement.
                ps.setInt(6, after); // Solde après règlement.
                ps.setString(7, "Règlement manche"); // Ajoute la note descriptive.
                ps.executeUpdate(); // Exécute l'insertion transactionnelle.
                ps2.setInt(1, delta); // Paramètre le gain cumulé.
                ps2.setString(2, result); // Paramètre le résultat symbolique.
                ps2.setLong(3, sessionId); // Paramètre la session.
                ps2.executeUpdate(); // Met à jour la session.
                connection.commit(); // Valide la transaction.
            } catch (Exception ex) { // Capture toute erreur.
                connection.rollback(); // Annule les écritures.
                throw ex; // Re-propage l'exception.
            } finally { // Bloc de sortie.
                connection.setAutoCommit(previous); // Restaure l'auto-commit.
            }
        } catch (SQLException e) { // Capture les erreurs SQL.
            throw new RuntimeException("settle", e); // Propage l'erreur annotée.
        }
    }
}
