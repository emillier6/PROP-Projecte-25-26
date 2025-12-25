package edu.upc.epsevg.prop.oust.players.MillierAranda;

import edu.upc.epsevg.prop.oust.GameStatusTunned;
import edu.upc.epsevg.prop.oust.MyStatus;
import edu.upc.epsevg.prop.oust.PlayerType;

/**
 * Heurística per avaluar posicions del joc Oust.
 * Considera material, grups, mobilitat i amenaces.
 * 
 * @author Alex Aranda & Erik Millier
 */
public class HeuristicaOust {
    
    // Pesos de l'heurística (públics per poder ajustar-los)
    public float pesEnemic = 150.0f;
    public float pesPropies = 0.5f;
    public float pesGrupGran = 80.0f;
    public float pesCaptures = 500.0f;
    public float pesCadena = 150.0f;
    public float pesMobilitat = 3.0f;
    
    /**
     * Constructor per defecte amb pesos equilibrats.
     */
    public HeuristicaOust() {
        // Usa els pesos per defecte
    }
    
    /**
     * Constructor amb pesos personalitzats.
     */
    public HeuristicaOust(float pesEnemic, float pesPropies, float pesGrupGran,
                          float pesCaptures, float pesCadena, float pesMobilitat) {
        this.pesEnemic = pesEnemic;
        this.pesPropies = pesPropies;
        this.pesGrupGran = pesGrupGran;
        this.pesCaptures = pesCaptures;
        this.pesCadena = pesCadena;
        this.pesMobilitat = pesMobilitat;
    }
    
    /**
     * Avalua una posició del joc des del punt de vista d'un jugador.
     * 
     * @param estat estat del joc a avaluar
     * @param jugador jugador des del punt de vista del qual avaluar
     * @return puntuació (més alta = millor per al jugador)
     */
    public float avaluar(GameStatusTunned estat, PlayerType jugador) {
        MyStatus info = estat.getInfo();
        PlayerType rival = (jugador == PlayerType.PLAYER1) ? 
                          PlayerType.PLAYER2 : PlayerType.PLAYER1;
        
        // 1) Material
        int pecesPropies = (jugador == PlayerType.PLAYER1) ? 
                          info.stonesP1 : info.stonesP2;
        int pecesRival = (jugador == PlayerType.PLAYER1) ? 
                        info.stonesP2 : info.stonesP1;
        
        // 2) Grups
        int grupPropi = (jugador == PlayerType.PLAYER1) ? 
                       info.biggestGroupP1 : info.biggestGroupP2;
        int grupRival = (jugador == PlayerType.PLAYER1) ? 
                       info.biggestGroupP2 : info.biggestGroupP1;
        
        // 3) Mobilitat (només si és el torn del jugador)
        int mobilitatPropia = 0;
        if (estat.getCurrentPlayer() == jugador && estat.getMoves() != null) {
            mobilitatPropia = estat.getMoves().size();
        }
        
        // Combinació final
        float puntuacio = 0;
        puntuacio -= pesEnemic * pecesRival;
        puntuacio -= pesPropies * pecesPropies;
        puntuacio += pesGrupGran * (grupPropi - grupRival);
        puntuacio += pesMobilitat * mobilitatPropia;
        
        return puntuacio;
    }
    
    /**
     * Afegeix amenaces de captures a l'avaluació.
     * Aquest mètode és opcional i es pot cridar després d'avaluar().
     */
    public float afegirAmenaces(float puntuacioBase, int capturesPropies, 
                                int capturesRival, int cadenaPropria, int cadenaRival) {
        return puntuacioBase 
               + pesCaptures * (capturesPropies - capturesRival)
               - pesCadena * cadenaRival;
    }
}
