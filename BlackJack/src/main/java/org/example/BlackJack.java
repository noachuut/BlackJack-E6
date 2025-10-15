package org.example;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;
import java.sql.*;            // DriverManager, Connection, PreparedStatement, etc.
import java.nio.file.*;       // Files, Paths (pour créer le dossier)


import static org.example.SecurityUtil.checkPwd;
import static org.example.SecurityUtil.hashPwd;

public class BlackJack {

    private record UserRow(long id, String hash){}
    private Connection cn;        // connexion



    int balanceCached = 0;

    private void refreshBalanceUI() {
        balanceCached = getBalance(userId);
        gamePanel.repaint();
    }

    private static String resolveDbPath() {
        String base = System.getenv("APPDATA") + "\\Blackjack"; // Windows
        try { Files.createDirectories(Paths.get(base)); } catch (Exception ignore) {}
        return base + "\\blackjack.db";
    }

    private void openDb() {
        try {
            String dbPath = resolveDbPath();
            cn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            // noinspection SqlResolve
            try (Statement st = cn.createStatement()) { st.execute("PRAGMA foreign_keys = ON"); }
        } catch (SQLException e) {
            throw new RuntimeException("Impossible d’ouvrir la DB SQLite", e);
        }
    }


    private UserRow findUserByEmail(String email) {
        openDb();
        try (var ps = cn.prepareStatement(
                "SELECT id_utilisateur, hash_mdp FROM utilisateur WHERE email=?")) {
            ps.setString(1, email);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return new UserRow(rs.getLong(1), rs.getString(2));
                return null;
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private int getBalance(long uid) {
        try (var ps = cn.prepareStatement(
                "SELECT solde_actuel FROM wallet WHERE id_utilisateur = ?")) {
            ps.setLong(1, uid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Erreur getBalance", e);
        }
    }

    // +1000 XPF si on a changé de jour (idempotent)
    private void applyDailyCredit(long uid) {
        try {
            boolean old = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try (PreparedStatement ps1 = cn.prepareStatement(
                    """
                    WITH w AS (
                      SELECT id_utilisateur, solde_actuel, last_daily_credit
                      FROM wallet WHERE id_utilisateur = ?
                    )
                    INSERT INTO txn (id_utilisateur, id_session, type, montant, solde_avant, solde_apres, note)
                    SELECT ?, NULL, 'DAILY_CREDIT',
                           1000,
                           w.solde_actuel,
                           w.solde_actuel + 1000,
                           'Crédit quotidien'
                    FROM w
                    WHERE date(w.last_daily_credit) < date('now');
                    """);
                 PreparedStatement ps2 = cn.prepareStatement(
                         """
                         UPDATE wallet
                            SET last_daily_credit = date('now'),
                                updated_at = datetime('now')
                          WHERE id_utilisateur = ?
                            AND date(last_daily_credit) < date('now');
                         """)) {

                ps1.setLong(1, uid);
                ps1.setLong(2, uid);
                ps1.executeUpdate();

                ps2.setLong(1, uid);
                ps2.executeUpdate();

                cn.commit();
            } catch (Exception ex) {
                cn.rollback(); throw ex;
            } finally {
                cn.setAutoCommit(old);
            }
        } catch (Exception e) {
            throw new RuntimeException("applyDailyCredit", e);
        }
    }

    // Crée une ligne session_jeu et retourne son id
    private long startSession(long uid) {
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO session_jeu(id_utilisateur) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, uid);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            // fallback SQLite si besoin
            try (Statement st = cn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new RuntimeException("startSession: pas d'id généré");
        } catch (SQLException e) {
            throw new RuntimeException("startSession", e);
        }
    }

    // Enregistre une mise (BET). amount = montant positif de la mise.
// Insère une txn négative et met à jour la session (mise_totale).
    private void placeBet(long uid, long sessionId, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount doit être > 0");
        try {
            int before = getBalance(uid);
            int after  = before - amount;
            if (after < 0) {
                throw new RuntimeException("Solde insuffisant pour miser " + amount + " XPF (solde=" + before + ")");
            }

            boolean old = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO txn(id_utilisateur,id_session,type,montant,solde_avant,solde_apres,note) " +
                            "VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement ps2 = cn.prepareStatement(
                         "UPDATE session_jeu SET mise_totale = mise_totale + ? WHERE id_session = ?")) {

                // txn BET (montant négatif)
                ps.setLong(1, uid);
                ps.setLong(2, sessionId);
                ps.setString(3, "BET");
                ps.setInt(4, -amount);
                ps.setInt(5, before);
                ps.setInt(6, after);
                ps.setString(7, "Mise");
                ps.executeUpdate();

                // cumul sur la session
                ps2.setInt(1, amount);
                ps2.setLong(2, sessionId);
                ps2.executeUpdate();

                cn.commit();
            } catch (Exception ex) {
                cn.rollback(); throw ex;
            } finally {
                cn.setAutoCommit(old);
            }
        } catch (SQLException e) {
            throw new RuntimeException("placeBet", e);
        }
    }

    // delta > 0 = gain ; delta < 0 = perte
    private void settle(long uid, long sessionId, int delta, String resultat) {
        try {
            int before = getBalance(uid);
            int after  = before + delta;

            boolean old = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(
                    "INSERT INTO txn(id_utilisateur,id_session,type,montant,solde_avant,solde_apres,note) " +
                            "VALUES (?,?,?,?,?,?,?)");
                 PreparedStatement ps2 = cn.prepareStatement(
                         "UPDATE session_jeu SET gain_total = gain_total + ?, resultat = ?, date_fin = datetime('now') " +
                                 "WHERE id_session = ?")) {

                ps.setLong(1, uid);
                ps.setLong(2, sessionId);
                ps.setString(3, "PAYOUT");
                ps.setInt(4, delta);
                ps.setInt(5, before);
                ps.setInt(6, after);
                ps.setString(7, "Règlement manche");
                ps.executeUpdate();

                ps2.setInt(1, delta);
                ps2.setString(2, resultat);
                ps2.setLong(3, sessionId);
                ps2.executeUpdate();

                cn.commit();
            } catch (Exception ex) {
                cn.rollback(); throw ex;
            } finally {
                cn.setAutoCommit(old);
            }
        } catch (SQLException e) {
            throw new RuntimeException("settle", e);
        }
    }





    // CRÉE un user (déclenche le trigger: wallet=10 000 + TX INIT)
    private long createUser(String email, String pseudo, String rawPwd) {
        String hash = hashPwd(rawPwd);
        try (var ps = cn.prepareStatement(
                "INSERT INTO utilisateur(email,pseudo,hash_mdp) VALUES (?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, pseudo);
            ps.setString(3, hash);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
            throw new RuntimeException("Échec création utilisateur");
        } catch (java.sql.SQLException ex) {
            if (ex.getMessage()!=null && ex.getMessage().toLowerCase().contains("unique"))
                throw new RuntimeException("Cet email est déjà utilisé.");
            throw new RuntimeException(ex);
        }
    }
    private class Card {
        String value;
        String type;

        Card(String value, String type) {
            this.value = value;
            this.type = type;
        }

        public int getValue() {
            if("AJQK".contains(value)){
                if (value == "A"){
                    return 11;
                }
                return 10;
            }
            return Integer.parseInt(value); // les card de 2 à 10
        }

        @Override
        public String toString() {
            return value + "-" + type;
        }

        public boolean isAce() {
            return value == "A";
        }

        public String getImagePath(){
            return "/card/" + toString() + ".png";
        }
    }

    ArrayList<Card> main;
    Random random = new Random();

    //Croupier
    Card hiddenCard;
    ArrayList<Card> mainCroupier;
    int sommeCroupier;
    int nmbAsCroupier;

    //joueur
    ArrayList<Card> mainJoueur;
    int sommeJoueur;
    int nmbAsJoueur;

    //fenetre
    int boardWith = 600;
    int boardHeight = boardWith;

    int cardWidth = 110;
    int cardHeight = 154;

    class SignupDialog extends JDialog {
        JTextField tfEmail = new JTextField(22);
        JTextField tfPseudo = new JTextField(22);
        JPasswordField pfPass = new JPasswordField(22);
        JPasswordField pfConf = new JPasswordField(22);
        JButton btnCreate = new JButton("Créer");
        JLabel lblMsg = new JLabel(" ");

        private final BlackJack app;

        interface Callback { void onCreated(long userId); }

        public SignupDialog(Frame owner,BlackJack app ,Callback cb ) {
            super(owner, "Créer un compte", true);
            this.app = app;
            var p = new JPanel(new GridBagLayout());
            var c = new GridBagConstraints(); c.insets = new Insets(6,6,6,6);

            c.gridx=0;c.gridy=0;c.anchor=GridBagConstraints.LINE_END; p.add(new JLabel("Email :"), c);
            c.gridx=1;c.anchor=GridBagConstraints.LINE_START; p.add(tfEmail, c);
            c.gridx=0;c.gridy=1;c.anchor=GridBagConstraints.LINE_END; p.add(new JLabel("Pseudo :"), c);
            c.gridx=1;c.anchor=GridBagConstraints.LINE_START; p.add(tfPseudo, c);
            c.gridx=0;c.gridy=2;c.anchor=GridBagConstraints.LINE_END; p.add(new JLabel("Mot de passe :"), c);
            c.gridx=1;c.anchor=GridBagConstraints.LINE_START; p.add(pfPass, c);
            c.gridx=0;c.gridy=3;c.anchor=GridBagConstraints.LINE_END; p.add(new JLabel("Confirmer :"), c);
            c.gridx=1;c.anchor=GridBagConstraints.LINE_START; p.add(pfConf, c);

            lblMsg.setForeground(new Color(180,0,0));
            var south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(btnCreate);

            getContentPane().add(lblMsg, BorderLayout.NORTH);
            getContentPane().add(p, BorderLayout.CENTER);
            getContentPane().add(south, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(btnCreate);
            pack(); setLocationRelativeTo(owner);

            btnCreate.addActionListener(e -> {
                String email = tfEmail.getText().trim();
                String pseudo= tfPseudo.getText().trim();
                String pwd   = new String(pfPass.getPassword());
                String conf  = new String(pfConf.getPassword());
                if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { lblMsg.setText("Email invalide."); return; }
                if (pwd.length()<12 || !pwd.matches(".*[A-Z].*") || !pwd.matches(".*[a-z].*")
                        || !pwd.matches(".*\\d.*") || !pwd.matches(".*[^A-Za-z0-9].*")) {
                    lblMsg.setText("Mot de passe trop faible (12+, Maj, Min, Chiffre, Spécial)."); return;
                }
                if (!pwd.equals(conf)) { lblMsg.setText("Les mots de passe ne correspondent pas."); return; }

                try {
                    long id = app.createUser(email, pseudo, pwd);
                    JOptionPane.showMessageDialog(this, "Compte créé. Solde initial: 10 000 XPF.");
                    cb.onCreated(id);
                    dispose();
                } catch (Exception ex) {
                    lblMsg.setText("Erreur: " + ex.getMessage());
                }
            });
        }
    }

    private int computeTotal(java.util.List<Card> hand) {
        int sum = 0, aces = 0;
        for (Card c : hand) {
            sum += c.getValue();
            if (c.isAce()) aces++;
        }
        while (sum > 21 && aces > 0) { sum -= 10; aces--; }
        return sum;
    }


    class LoginDialog extends JDialog {
        JTextField tfEmail = new JTextField(22);
        JPasswordField pfPass = new JPasswordField(22);
        JButton btnLogin = new JButton("Se connecter");
        JButton btnSignup = new JButton("Créer un compte");
        JLabel lblMsg = new JLabel(" ");
        private final BlackJack app;

        interface Callback { void onSuccess(long userId); }

        public LoginDialog(Frame owner, BlackJack app,
                           LoginDialog.Callback cb,
                           java.util.function.Consumer<Void> onSignup)
        {
            super(owner, "Connexion", true);
            this.app = app;
            var p = new JPanel(new GridBagLayout());
            var c = new GridBagConstraints(); c.insets = new Insets(6,6,6,6);

            c.gridx=0;c.gridy=0;c.anchor=GridBagConstraints.LINE_END; p.add(new JLabel("Email :"), c);
            c.gridx=1;c.anchor=GridBagConstraints.LINE_START; p.add(tfEmail, c);
            c.gridx=0;c.gridy=1;c.anchor=GridBagConstraints.LINE_END; p.add(new JLabel("Mot de passe :"), c);
            c.gridx=1;c.anchor=GridBagConstraints.LINE_START; p.add(pfPass, c);

            lblMsg.setForeground(new Color(180,0,0));
            var south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(btnSignup); south.add(btnLogin);

            getContentPane().add(lblMsg, BorderLayout.NORTH);
            getContentPane().add(p, BorderLayout.CENTER);
            getContentPane().add(south, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(btnLogin);
            pack(); setLocationRelativeTo(owner);

            btnLogin.addActionListener(e -> {
                String email = tfEmail.getText().trim();
                String pwd   = new String(pfPass.getPassword());
                if (email.isEmpty() || pwd.isEmpty()) { lblMsg.setText("Remplis email et mot de passe."); return; }

                try {
                    var u = app.findUserByEmail(email);
                    if (u==null) { lblMsg.setText("Utilisateur introuvable."); return; }
                    if (!checkPwd(pwd, u.hash())) { lblMsg.setText("Mot de passe incorrect."); return; }
                    cb.onSuccess(u.id());
                    dispose();
                } catch (Exception ex1) {
                    lblMsg.setText("Erreur: " + ex1.getMessage());
                }
            });

            btnSignup.addActionListener(e -> onSignup.accept(null));
        }
    }

    private void drawBalanceBadge(Graphics2D g2) {
        // lisse les textes/formes
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String label = "Solde  " + balanceCached + " XPF";
        Font font = new Font("Arial", Font.BOLD, 16);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int padX = 14;         // padding intérieur horizontal
        int padY = 8;          // padding intérieur vertical
        int r = 14;            // arrondi
        int textW = fm.stringWidth(label);
        int textH = fm.getAscent(); // hauteur utile pour baseline

        int w = textW + padX * 2 + 18; // + place pour un petit “jeton”
        int h = textH + padY * 2;

        int x = gamePanel.getWidth() - w - 16; // marge droite
        int y = 12; // marge haute

        // fond semi-transparent
        g2.setColor(new Color(0, 0, 0, 110));
        g2.fillRoundRect(x, y, w, h, r, r);

        // liseré
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawRoundRect(x, y, w, h, r, r);

        // petit jeton "or"
        int coinD = h - 10;
        int coinX = x + 8;
        int coinY = y + (h - coinD) / 2;
        g2.setColor(new Color(214, 170, 60)); // or
        g2.fillOval(coinX, coinY, coinD, coinD);
        g2.setColor(new Color(120, 90, 30));
        g2.drawOval(coinX, coinY, coinD, coinD);

        // texte
        int textX = coinX + coinD + 8;
        int textY = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(new Color(245, 242, 230));
        g2.drawString(label, textX, textY);
    }

    private int computeDealerTotal(boolean reveal) {
        int sum = 0, aces = 0;

        for (Card c : mainCroupier) {         // cartes visibles
            sum += c.getValue();
            if (c.isAce()) aces++;
        }
        if (reveal && hiddenCard != null) {   // ajoute la carte cachée après "Rester"
            sum += hiddenCard.getValue();
            if (hiddenCard.isAce()) aces++;
        }

        while (sum > 21 && aces > 0) { sum -= 10; aces--; }
        return sum;
    }

    JFrame frame = new JFrame("Black Jack");
    JPanel gamePanel = new JPanel(){
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            try {
                //la carte caché
                Image hiddenCardImage = new ImageIcon(getClass().getResource("/card/BACK.png")).getImage();
                if (!stayButton.isEnabled()) {
                    hiddenCardImage = new ImageIcon(getClass().getResource(hiddenCard.getImagePath())).getImage();
                }
                g.drawImage(hiddenCardImage, 20, 20, cardWidth, cardHeight, null);

                //main croupier
                for (int i = 0; i < mainCroupier.size(); i++) {
                    Card cardCroupier = mainCroupier.get(i);
                    String imagePath = cardCroupier.getImagePath();
                    Image CarteImage = new ImageIcon(getClass().getResource(imagePath)).getImage();

                    g.drawImage(CarteImage, cardWidth + 25 + (cardWidth + 5 )*i, 20, cardWidth, cardHeight, null);
                }

                //main joueur
                for (int i = 0; i < mainJoueur.size(); i++) {
                    Card cardCroupier = mainJoueur.get(i);
                    String imagePath = cardCroupier.getImagePath();
                    Image CarteImage = new ImageIcon(getClass().getResource(imagePath)).getImage();

                    g.drawImage(CarteImage, 20 + (cardWidth + 5 )*i, 320, cardWidth, cardHeight, null);
                }
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.setColor(Color.white);

                drawBalanceBadge((Graphics2D) g);

// Total joueur (toujours visible)
                int playerTotal = computeTotal(mainJoueur);     // ta méthode computeTotal si tu l'as ajoutée
                g.drawString("Joueur : " + playerTotal, 20, 320 + cardHeight + 30);

// Total croupier : "X + ?" pendant la manche, total complet après "Rester"
                boolean revealed = !stayButton.isEnabled();     // une fois "Rester" cliqué, le bouton est désactivé
                if (revealed) {
                    g.drawString("Croupier : " + computeDealerTotal(true), 20, 20 + cardHeight + 30);
                } else {
                    g.drawString("Croupier : " + computeDealerTotal(false) + " + ?", 20, 20 + cardHeight + 30);
                }

                if(!stayButton.isEnabled()){
                    sommeCroupier = reduceCroupierAs();
                    sommeJoueur = reduceJoueurAs();
                    System.out.println(("STAY : "));
                    System.out.println(sommeCroupier);
                    System.out.println(sommeJoueur);

                    String message = "";
                    if(sommeJoueur > 21){
                        message = "Tu as perdus";
                    } else if (sommeCroupier > 21) {
                        message = "Tu as gagnés";
                    } else if (sommeCroupier == sommeJoueur) {
                        message = "Egalité";
                    } else if (sommeCroupier > sommeJoueur) {
                        message = "Tu as perdus";
                    } else if (sommeJoueur > sommeCroupier) {
                        message = "Tu as gagnés";
                    }

                    g.setFont(new Font("Arial", Font.PLAIN, 30));
                    g.setColor(Color.white);
                    g.drawString(message, 220 , 250);
                }

            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    };

    // Les bouttons
    JPanel buttonPanel = new JPanel();
    JButton hitButton = new JButton("Tirez");
    JButton stayButton = new JButton("Rester");
    JButton newRoundButton = new JButton("Nouvelle manche");
    JButton betButton = new JButton("Changer mise");




    long userId = -1;
    long sessionId = -1;
    int currentBet = 500;
    boolean settled = false;


    BlackJack(){
        startGame();
        openDb();

        // Dialogues
        LoginDialog login = new LoginDialog(frame, this,
                uid -> { userId = uid; },
                v -> {
                    SignupDialog su = new SignupDialog(frame, this, uid -> userId = uid);
                    su.setVisible(true);
                }
        );
        login.setLocationRelativeTo(null);
        login.setVisible(true);
        if (userId <= 0) { // l’utilisateur a fermé sans se connecter
            JOptionPane.showMessageDialog(null, "Connexion requise pour jouer.");
            System.exit(0);
        }

        // Crédit quotidien + affichage solde
        applyDailyCredit(userId);
        refreshBalanceUI();

        int chosen = askBetAmount();
        if (chosen <= 0) { // annule → proposer la mise minimale si possible
            if (getBalance(userId) < 250) {
                JOptionPane.showMessageDialog(frame, "Solde insuffisant pour jouer.", "Info", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            }
            currentBet = 250;
        } else {
            currentBet = chosen;
        }

        // Démarrer une session et prélever la mise
        sessionId = startSession(userId);
        placeBet(userId, sessionId, currentBet);
        refreshBalanceUI();
        betButton.setEnabled(false);

        frame.setVisible(true);
        frame.setSize(boardWith, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        gamePanel.setLayout(new BorderLayout());
        gamePanel.setBackground(new Color(53,101,77));
        frame.add(gamePanel);

        hitButton.setFocusable(false);
        buttonPanel.add(hitButton);
        stayButton.setFocusable(false);
        buttonPanel.add(stayButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        newRoundButton.setFocusable(false);
        newRoundButton.setEnabled(false);        // activé seulement quand la manche est finie
        buttonPanel.add(newRoundButton);
        newRoundButton.addActionListener(e -> startNewRound());

        betButton.setFocusable(false);
        buttonPanel.add(betButton);


        hitButton.addActionListener(e -> {
            Card card = main.remove(main.size()-1);
            sommeJoueur+= card.getValue();
            nmbAsJoueur+= card.isAce()? 1 : 0;
            mainJoueur.add(card);
            if (reduceJoueurAs() >= 21){
                hitButton.setEnabled(false);
            }
            gamePanel.repaint();

        });

        betButton.addActionListener(e -> {
            int choix = askBetAmount();
            if (choix > 0) {
                currentBet = choix;
                JOptionPane.showMessageDialog(frame, "Mise fixée à " + currentBet + " XPF.");
            }
        });


        stayButton.addActionListener(e -> {
            hitButton.setEnabled(false);
            stayButton.setEnabled(false);

            while(sommeCroupier < 17){
                Card card = main.remove(main.size()-1);
                sommeCroupier+= card.getValue();
                nmbAsCroupier+= card.isAce()? 1 : 0;
                mainCroupier.add(card);
                gamePanel.repaint();
            }
            if (!settled) {
                sommeCroupier = reduceCroupierAs();
                sommeJoueur   = reduceJoueurAs();

                String resultat;
                int payout; // montant à créditer au wallet

                boolean joueurBust  = (sommeJoueur > 21);
                boolean dealerBust  = (sommeCroupier > 21);

                if (joueurBust) {
                    resultat = "LOSE";
                    payout   = 0;                 // on a déjà retiré la mise
                } else if (dealerBust) {
                    resultat = "WIN";
                    payout   = currentBet * 2;    // mise + gain
                } else if (sommeCroupier == sommeJoueur) {
                    resultat = "PUSH";
                    payout   = currentBet;        // on rend la mise
                } else if (sommeCroupier > sommeJoueur) {
                    resultat = "LOSE";
                    payout   = 0;
                } else {
                    resultat = "WIN";
                    payout   = currentBet * 2;
                }

                settle(userId, sessionId, payout, resultat);  // <- 'payout' et non pas -mise
                settled = true;
                refreshBalanceUI();
                newRoundButton.setEnabled(true);
                betButton.setEnabled(true);
            }

        });

        gamePanel.repaint();
    }

    public void startGame(){
        //main
        buildMain();
        MelangeMain();

        //croupier
        mainCroupier = new ArrayList<Card>();
        sommeCroupier = 0;
        nmbAsCroupier = 0;

        hiddenCard = main.remove(main.size()-1); //enleve la derniere card de la main
        sommeCroupier += hiddenCard.getValue();
        nmbAsCroupier += hiddenCard.isAce() ? 1 : 0;

        Card card = main.remove(main.size()-1);
        sommeCroupier += card.getValue();
        nmbAsCroupier += card.isAce() ? 1 : 0;
        mainCroupier.add(card);

        System.out.println("DEALER:");
        System.out.println(hiddenCard);
        System.out.println(mainCroupier);
        System.out.println(sommeCroupier);
        System.out.println(nmbAsCroupier);

        //joueur
        mainJoueur = new ArrayList<Card>();
        sommeJoueur = 0;
        nmbAsJoueur = 0;

        for(int i = 0; i < 2; i++){
            card = main.remove(main.size()-1);
            sommeJoueur += card.getValue();
            nmbAsJoueur += card.isAce() ? 1 : 0;
            mainJoueur.add(card);
        }

        System.out.println("JOUEUR:");
        System.out.println(mainJoueur);
        System.out.println(sommeJoueur);
        System.out.println(nmbAsJoueur);

    }

    public void buildMain(){
        main = new ArrayList<Card>();
        String[] values = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        String[] type = {"D","H","S","C"};

        for (int i = 0; i < type.length; i++){
            for (int j = 0; j < values.length; j++){
                Card card = new Card(values[j], type[i]);
                main.add(card);
            }

        }

        System.out.println("MAIN CREER");
        System.out.println(main);

    }

    public void MelangeMain(){
        for (int i = 0; i < main.size(); i++){
            int j = random.nextInt(main.size());
            Card card = main.get(i);
            Card randomCard = main.get(j);
            main.set(i,randomCard);
            main.set(j,card);
        }

        System.out.println("APRES MELANGE");
        System.out.println(main);
    }

    public int reduceJoueurAs() {
        while(sommeJoueur > 21 && nmbAsJoueur > 0){
            sommeJoueur -= 10;
            nmbAsJoueur -= 1;
        }
        return sommeJoueur;
    }

    public int reduceCroupierAs() {
        while(sommeCroupier > 21 && nmbAsCroupier > 0){
            sommeCroupier -= 10;
            nmbAsCroupier -= 1;
        }
        return sommeCroupier;
    }

    private void startNewRound() {
        settled = false;

        // réactive les boutons de jeu et cache le message (car stay redevient activé)
        hitButton.setEnabled(true);
        stayButton.setEnabled(true);
        newRoundButton.setEnabled(false);
        betButton.setEnabled(false);

        // (re)distribue les cartes et reconstruit le paquet
        startGame();


        sessionId = startSession(userId);
        placeBet(userId, sessionId, currentBet);
        refreshBalanceUI();

        gamePanel.repaint();
    }

    private int askBetAmount() {
        int balance = getBalance(userId);
        if (balance < 250) {
            JOptionPane.showMessageDialog(frame, "Solde insuffisant (min 250 XPF).", "Mise", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        // min 250, max = solde, pas 50
        int step = 50;
        int min = 250;
        int max = balance;

        // si currentBet n'est pas valide, on propose min ou le max dispo
        int initial = Math.min(Math.max(currentBet, min), max);
        javax.swing.JSpinner spinner = new javax.swing.JSpinner(
                new javax.swing.SpinnerNumberModel(initial, min, max, step)
        );
        int res = JOptionPane.showConfirmDialog(frame, spinner, "Choisir la mise (XPF)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return -1;

        int val = (int) spinner.getValue();
        // sécurité : arrondir au pas si besoin
        val = Math.max(min, Math.min(max, (val / step) * step));
        return val;
    }



}
