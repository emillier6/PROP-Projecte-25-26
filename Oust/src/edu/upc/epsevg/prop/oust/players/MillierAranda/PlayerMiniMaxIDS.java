/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust.players.MillierAranda;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.SearchType;

/**
 * Implementació d'un jugador Minimax amb Iterative Deepening Search (IDS).
 *
 * <p>Aquest jugador utilitza la tècnica d'Iterative Deepening, que consisteix en
 * executar múltiples cerques Minimax amb profunditats creixents (1, 2, 3, ...)
 * fins que s'exhaureix el temps disponible.</p>
 *
 * <h2>Avantatges d'IDS:</h2>
 * <ul>
 *   <li><b>Gestió del temps:</b> Sempre retorna un moviment vàlid dins del temps límit</li>
 *   <li><b>Optimització:</b> Utilitza tot el temps disponible de forma eficient</li>
 *   <li><b>Millor moviment:</b> Retorna el millor moviment trobat fins al timeout</li>
 *   <li><b>Ordenació de moviments:</b> Les cerques anteriors ajuden a l'ordenació</li>
 * </ul>
 * 
 * <h2>Funcionament:</h2>
 * <ol>
 *   <li>Cerca amb profunditat 1 (ràpid)</li>
 *   <li>Guarda el resultat</li>
 *   <li>Cerca amb profunditat 2</li>
 *   <li>Si completa abans del timeout, guarda el resultat</li>
 *   <li>Continua incrementant la profunditat...</li>
 *   <li>Quan arriba el timeout, retorna l'últim resultat vàlid</li>
 * </ol>
 * 
 * <p><b>Nota important:</b> Aquest jugador REUTILITZA l'algorisme de PlayerMiniMax,
 * complint així amb l'especificació que PlayerMiniMaxIDS ha d'utilitzar
 * l'algorisme de PlayerMiniMax.</p>
 * 
 * <p><b>Gestió del timeout:</b> Quan el framework crida timeout(), aquest jugador
 * notifica immediatament al PlayerMiniMax que s'està executant per interrompre'l
 * de forma neta i retornar l'últim moviment vàlid calculat.</p>
 * 
 * @author Erik Millier, Alex Aranda
 * @see PlayerMiniMax
 */
public class PlayerMiniMaxIDS implements IPlayer, IAuto {
    
    /** Nom identificatiu del jugador. */
    private String name;
    
    /** 
     * Flag que indica si s'ha exhaurit el temps de computació.
     * És volatile per assegurar visibilitat entre threads.
     */
    private volatile boolean timeout;
    
    /** 
     * Referència al PlayerMiniMax que s'està executant actualment.
     * Permet interrompre l'execució quan es rep un timeout.
     * És volatile per assegurar visibilitat entre threads.
     */
    private volatile PlayerMiniMax currentPlayerMiniMax;
    
    /**
     * Constructor per defecte del jugador Minimax amb IDS.
     * 
     * <p>Aquest constructor és OBLIGATORI segons l'especificació del professor.
     * No requereix paràmetres ja que la profunditat es determina dinàmicament
     * durant l'execució segons el temps disponible.</p>
     */
    public PlayerMiniMaxIDS() {
        this.name = "MiniMaxIDS";
        this.timeout = false;
        this.currentPlayerMiniMax = null;
    }
    
    /**
     * Notifica que s'ha exhaurit el temps de computació.
     * 
     * <p>Aquest mètode és cridat pel framework quan s'assoleix el límit de temps.
     * Immediatament notifica al PlayerMiniMax en execució (si n'hi ha) per
     * interrompre la cerca i retornar el millor moviment trobat fins ara.</p>
     * 
     * <p>La interrupció és neta: PlayerMiniMax deixa de calcular i retorna
     * l'última profunditat completada amb èxit.</p>
     */
    @Override
    public void timeout() {
        timeout = true;
        // Notificar immediatament al PlayerMiniMax que s'està executant
        if (currentPlayerMiniMax != null) {
            currentPlayerMiniMax.setExternalTimeout(true);
        }
    }
    
    /**
     * Retorna el nom identificatiu del jugador.
     * 
     * @return Nom del jugador ("MiniMaxIDS")
     */
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Decideix el millor moviment utilitzant Iterative Deepening Search.
     * 
     * <p>Aquest mètode implementa l'estratègia IDS:</p>
     * <ol>
     *   <li>Inicia amb profunditat 1</li>
     *   <li>Crea una instància de PlayerMiniMax amb aquesta profunditat</li>
     *   <li>Executa la cerca Minimax</li>
     *   <li>Si es completa abans del timeout, guarda el resultat</li>
     *   <li>Incrementa la profunditat i repeteix</li>
     *   <li>Quan arriba el timeout, retorna l'últim resultat vàlid</li>
     * </ol>
     * 
     * <p><b>Gestió del timeout:</b> Si el timeout arriba mentre PlayerMiniMax
     * s'està executant, aquest és notificat immediatament i interromp la cerca.
     * El resultat parcial es descarta i es retorna l'última profunditat
     * completada amb èxit.</p>
     * 
     * <p><b>Resultat garantit:</b> Aquest mètode sempre retorna un moviment vàlid,
     * encara que només hagi completat la cerca a profunditat 1.</p>
     * 
     * @param s Estat actual del joc
     * @return Millor moviment trobat dins del temps disponible, incloent:
     *         <ul>
     *           <li>Llista de punts que formen el camí del moviment</li>
     *           <li>Nombre total de nodes explorats en totes les iteracions</li>
     *           <li>Profunditat màxima assolida abans del timeout</li>
     *           <li>Tipus de cerca (MINIMAX_IDS)</li>
     *         </ul>
     */
    @Override
    public PlayerMove move(GameStatus s) {
        timeout = false;
        
        PlayerMove millorMoviment = null;
        int profunditatActual = 1;
        int profunditatMaximaAssolida = 0;
        long nodesExploratsTotals = 0;
        
        // Iterative Deepening: Incrementar profunditat fins timeout
        while (!timeout) {
            // Crear nova instància de PlayerMiniMax amb profunditat actual
            PlayerMiniMax jugadorMiniMax = new PlayerMiniMax(profunditatActual);
            
            // Establir com a jugador actual per poder-lo interrompre
            currentPlayerMiniMax = jugadorMiniMax;
            
            // Cridar move() del PlayerMiniMax
            PlayerMove movimentActual = jugadorMiniMax.move(s);
            
            // Netejar referència
            currentPlayerMiniMax = null;
            
            // Si timeout durant la cerca, descartar resultat parcial
            if (timeout) {
                break;
            }
            
            // Guardar el millor moviment trobat fins ara
            if (movimentActual != null && movimentActual.getPoints() != null && !movimentActual.getPoints().isEmpty()) {
                millorMoviment = movimentActual;
                profunditatMaximaAssolida = profunditatActual;
                nodesExploratsTotals += movimentActual.getNumerOfNodesExplored();
            }
            
            // Incrementar profunditat per la següent iteració
            profunditatActual++;
            
            // Límit de seguretat per evitar profunditats massa grans
            if (profunditatActual > 100) {
                break;
            }
        }
        
        // Si no s'ha trobat cap moviment (cas extrem), retornar moviment buit
        if (millorMoviment == null) {
            millorMoviment = new PlayerMove(java.util.Collections.emptyList(), 0, 0, SearchType.MINIMAX_IDS);
        } else {
            // Actualitzar informació amb profunditat màxima i nodes totals
            millorMoviment = new PlayerMove(
                millorMoviment.getPoints(),
                nodesExploratsTotals,
                profunditatMaximaAssolida,
                SearchType.MINIMAX_IDS
            );
        }
        
        return millorMoviment;
    }
}
