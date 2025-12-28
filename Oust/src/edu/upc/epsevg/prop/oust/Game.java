package edu.upc.epsevg.prop.oust;

import edu.upc.epsevg.prop.oust.players.HumanPlayer;
import edu.upc.epsevg.prop.oust.players.RandomPlayer;
import edu.upc.epsevg.prop.oust.players.MillierAranda.PlayerMiniMax;
import edu.upc.epsevg.prop.oust.players.MillierAranda.PlayerMiniMaxIDS;

import javax.swing.SwingUtilities;

/**
 * Oust: el joc de taula.
 * @author Bernat
 */
public class Game {
    /**
     * @param args no usats
     */
    public static void main(String[] args) { 
         
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                
                //---------------------------------------------
                // Un parell de tontets jugant 
                //---------------------------------------------
                //IPlayer player1 = new RandomPlayer("Asterix");
                //IPlayer player2 = new RandomPlayer("Obelix");
                
                
                
                //---------------------------------------------
                // Deixem el tontet en mans d'una mala persona
                //---------------------------------------------
                //IPlayer player1 = new RandomPlayer("Asterix");
                //IPlayer player2 = new MalaOustiaPlayer();
                
                
                //---------------------------------------------
                // Enjoy!
                //---------------------------------------------
                //IPlayer player1 = new RandomPlayer("");
                
                // Opcions de jugadors personalitzats:
                // PlayerMiniMax: profunditat fixa (més ràpid però menys adaptable)
                // IDSPlayerMiniMax: cerca iterativa amb límit de temps (més intel·ligent)
                
                IPlayer player1 = new MalaOustiaPlayer();
                //IPlayer player2 = new IDSPlayerMiniMax(3000);  // Profunditat 3
                

                // Alternativa amb IDS (cerca iterativa):
                IPlayer player2 = new PlayerMiniMaxIDS();  // Constructor buit, 5s per defecte
                
                                
                
                //---------------------------------------------
                // Customitzeu els paràmetres
                //---------------------------------------------
                int midaCostat = 7;
                int timeoutEnSegons = 5;
                boolean pauseEnAutomatic = false;
                
                new Board(player1 , player2, midaCostat /*mida*/,  timeoutEnSegons/*s timeout*/, pauseEnAutomatic);
             }
        });
    }
}
