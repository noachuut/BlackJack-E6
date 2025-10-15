-- V1__init.sql

PRAGMA foreign_keys = ON;

CREATE TABLE utilisateur (
                             id_utilisateur   INTEGER PRIMARY KEY AUTOINCREMENT,
                             email            TEXT NOT NULL UNIQUE,
                             pseudo           TEXT NOT NULL,
                             hash_mdp         TEXT NOT NULL,
                             date_creation    TEXT NOT NULL DEFAULT (datetime('now')),
                             etat             TEXT NOT NULL DEFAULT 'actif',   -- actif|bloque
                             role             TEXT NOT NULL DEFAULT 'user'      -- user|admin
);

CREATE TABLE wallet (
                        id_utilisateur     INTEGER PRIMARY KEY,
                        solde_actuel       INTEGER NOT NULL,               -- en XPF (entier)
                        last_daily_credit  TEXT NOT NULL,                  -- DATE (YYYY-MM-DD)
                        created_at         TEXT NOT NULL DEFAULT (datetime('now')),
                        updated_at         TEXT NOT NULL DEFAULT (datetime('now')),
                        FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE
);

CREATE TABLE session_jeu (
                             id_session       INTEGER PRIMARY KEY AUTOINCREMENT,
                             id_utilisateur   INTEGER NOT NULL,
                             date_debut       TEXT NOT NULL DEFAULT (datetime('now')),
                             date_fin         TEXT,
                             mise_totale      INTEGER NOT NULL DEFAULT 0,
                             gain_total       INTEGER NOT NULL DEFAULT 0,
                             resultat         TEXT,                             -- WIN|LOSE|PUSH
                             seed_rng         TEXT,
                             FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE
);

CREATE TABLE transaction (
                             id_transaction   INTEGER PRIMARY KEY AUTOINCREMENT,
                             id_utilisateur   INTEGER NOT NULL,
                             id_session       INTEGER,
                             type             TEXT NOT NULL,                    -- INIT|DAILY_CREDIT|BET|PAYOUT|BONUS|ADJUST
                             montant          INTEGER NOT NULL,                 -- XPF, signe +/- (BET négatif)
                             solde_avant      INTEGER NOT NULL,
                             solde_apres      INTEGER NOT NULL,
                             note             TEXT,
                             created_at       TEXT NOT NULL DEFAULT (datetime('now')),
                             FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE,
                             FOREIGN KEY (id_session)     REFERENCES session_jeu(id_session) ON DELETE SET NULL
);

CREATE INDEX idx_tx_user_date ON transaction(id_utilisateur, created_at);
CREATE INDEX idx_sess_user_date ON session_jeu(id_utilisateur, date_debut);
