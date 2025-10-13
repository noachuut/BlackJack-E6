package org.example;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

public class BlackJack {

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
    JPanel buttonPanel = new JPanel();
    JButton hitButton = new JButton("Tirez");
    JButton stayButton = new JButton("Rester");




    BlackJack(){
        startGame();

        frame.setVisible(true);
        frame.setSize(boardWith, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        gamePanel.setLayout(new BorderLayout());
        gamePanel.setBackground(new Color(53,101,77));
        frame.add(gamePanel);

        hitButton.setFocusable(false);
        buttonPanel.add(hitButton);
        stayButton.setFocusable(false);
        buttonPanel.add(stayButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

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


}
