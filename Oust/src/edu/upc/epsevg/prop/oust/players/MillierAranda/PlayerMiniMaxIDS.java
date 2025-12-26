package edu.upc.epsevg.prop.oust.players.MillierAranda;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * PlayerMiniMaxIDS implementa cerca iterativa en profunditat (IDS)
 * reutilitzant l'algorisme de PlayerMiniMax.
 * 
 * Compleix amb els requisits de lliurament:
 * - Constructor buit
 * - Temps fixat a 5 segons
 * - Reutilitza PlayerMiniMax
 * 
 * @author Erik Millier & Alex Aranda
 */
public class PlayerMiniMaxIDS extends PlayerMiniMax implements IPlayer, IAuto {
    
    private static final long TEMPS_LIMIT = 5000; // 5 segons
    private long tempsInici;
    private boolean tempsEsgotat;
    private long totalNodes;
    private int profunditatMaximaAssolida;
    
    /**
     * Constructor buit obligatori segons requisits de lliurament.
     * Fixa el temps a 5 segons.
     */
    public PlayerMiniMaxIDS() {
        super(1); // Profunditat inicial, serà sobrescrita per IDS
    }
    
    @Override
    public String getName() {
        return "PMiniMaxIDS(5s)";
    }
    
    /**
     * Calcula el millor moviment utilitzant IDS,
     * reutilitzant minimaxAB de PlayerMiniMax.
     */
    @Override
    public PlayerMove move(GameStatus s) {
        tempsInici = System.currentTimeMillis();
        tempsEsgotat = false;
        totalNodes = 0;
        profunditatMaximaAssolida = 0;
        
        PlayerMove millorMoviment = null;
        
        // Cerca iterativa: augmentem profunditat fins que s'acaba el temps
        for (int depth = 1; depth <= 100; depth++) {
            if (tempsEsgotat()) {
                break;
            }
            
            try {
                // Reutilitzem el mètode move de PlayerMiniMax
                // però amb profunditat controlada
                profunditatMaxima = depth;
                PlayerMove pm = super.move(s);
                
                if (pm != null) {
                    millorMoviment = pm;  // Guardem tot el PlayerMove
                    profunditatMaximaAssolida = depth;
                    totalNodes += nodes;  // nodes és protected ara
                }
                
                // Si trobem victòria segura, sortim
                if (pm != null && pm.getH() > 900000) {
                    break;
                }
                
            } catch (Exception e) {
                // Temps esgotat o altre error
                break;
            }
        }
        
        // Retornem el millor moviment trobat, actualitzant metadades
        if (millorMoviment != null) {
            millorMoviment.setNumerOfNodesExplored(totalNodes);
            millorMoviment.setMaxDepthReached(profunditatMaximaAssolida);
        }
        
        return millorMoviment;
    }
    
    /**
     * Verifica si s'ha esgotat el temps.
     */
    private boolean tempsEsgotat() {
        if (System.currentTimeMillis() - tempsInici >= TEMPS_LIMIT) {
            tempsEsgotat = true;
            return true;
        }
        return false;
    }
    
    @Override
    public void timeout() {
        tempsEsgotat = true;
    }
}
