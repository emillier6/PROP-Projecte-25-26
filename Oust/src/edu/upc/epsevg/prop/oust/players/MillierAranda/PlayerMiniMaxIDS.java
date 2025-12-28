package edu.upc.epsevg.prop.oust.players.MillierAranda;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import java.awt.Point;
import java.util.List;

/**
 * PlayerMiniMaxIDS Configurable: Permite definir el timeout por partida.
 * @author Erik Millier & Alex Aranda & AI Tuned
 */
public class PlayerMiniMaxIDS extends PlayerMiniMax implements IPlayer, IAuto {

    // Ya no es 'static final', ahora es una variable de instancia.
    // Valor por defecto: 4800ms (para partidas de 5s estándar)
    private long tempsLimit = 4800; 
    
    private long tempsInici;
    private long totalNodes;
    private int profunditatMaximaAssolida;

    public PlayerMiniMaxIDS() {
        super(1);
    }
    
    /**
     * Configura el temps màxim per torn.
     * Es recomana cridar aquest mètode des del Game.java abans de començar.
     * @param seconds Temps total del torn en segons (ex: 5, 10, 2...)
     */
    public void setTimeout(int seconds) {
        // Convertim a mil·lisegons i restem un marge de seguretat (ex: 200ms)
        // Això assegura que retornem el control abans que el Watchdog ens mati.
        this.tempsLimit = (seconds * 1000) - 200;
        
        // Protecció mínima per evitar temps negatius si el timeout és molt baix
        if (this.tempsLimit < 100) this.tempsLimit = 100;
    }

    @Override
    public String getName() {
        // Mostrem el límit real en el nom per depurar
        return "IDS(Limit:" + tempsLimit + "ms)";
    }

    @Override
    public PlayerMove move(GameStatus s) {
        tempsInici = System.currentTimeMillis();
        totalNodes = 0;
        profunditatMaximaAssolida = 0;
        
        PlayerMove millorMoviment = null;

        for (int depth = 1; depth <= 100; depth++) {
            if (tempsEsgotat()) break;
            
            try {
                profunditatMaxima = depth;
                PlayerMove pm = super.move(s);
                
                if (pm != null) {
                    if (!tempsEsgotat()) { 
                        millorMoviment = pm;  
                        profunditatMaximaAssolida = depth;
                        totalNodes += nodes;
                    }
                }
                
                // Victòria immediata detectada
                if (pm != null && pm.getH() > 900000) {
                    millorMoviment = pm;
                    break;
                }
                
            } catch (Exception e) {
                break;
            }
        }
        
        if (millorMoviment != null) {
            millorMoviment.setNumerOfNodesExplored(totalNodes);
            millorMoviment.setMaxDepthReached(profunditatMaximaAssolida);
        }
        
        return millorMoviment;
    }

    private boolean tempsEsgotat() {
        // Ara utilitzem la variable d'instància 'tempsLimit'
        if (System.currentTimeMillis() - tempsInici >= this.tempsLimit) {
            timeout(); 
            return true;
        }
        return false;
    }

    @Override
    public void timeout() {
        super.timeout(); // El fre d'emergència del pare (PlayerMiniMax)
    }
}