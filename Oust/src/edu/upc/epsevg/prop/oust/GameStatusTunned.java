
package edu.upc.epsevg.prop.oust;

import java.awt.Point;
import java.util.*;

/**
 *
 * @author Usuari
 */
public class GameStatusTunned extends GameStatus{
    
    // Cache per emmagatzemar moviments ja calculats
    private List<Point> cachedMoves;
    private boolean movesCached;
    
    // Hashcode per a detecció d'estats repetits
    private Integer cachedHashCode;
    
    // Cache de moviments de captura
    private List<Point> cachedCaptureMoves;
    private List<Point> cachedNonCaptureMoves;
    private boolean captureMovesAnalyzed;
    
    /**
     * Constructor que crea un nou estat a partir d'un GameStatus existent.
     * 
     * @param gs Estat del joc a copiar
     */
    public GameStatusTunned(GameStatus gs) {
        super(gs);
        this.movesCached = false;
        this.cachedMoves = null;
        this.cachedHashCode = null;
        this.captureMovesAnalyzed = false;
        this.cachedCaptureMoves = null;
        this.cachedNonCaptureMoves = null;
    }
    
    /**
     * Obté els moviments possibles amb cache per evitar recàlculs.
     * 
     * @return Llista de moviments disponibles
     */
    @Override
    public List<Point> getMoves() {
        if (!movesCached) {
            cachedMoves = super.getMoves();
            movesCached = true;
        }
        return cachedMoves;
    }
    
    /**
     * Obté només els moviments de captura.
     * CRUCIAL per Oust: moviments que connecten amb grups propis.
     * 
     * @return Llista de moviments de captura
     */
    public List<Point> getCaptureMoves() {
        if (!captureMovesAnalyzed) {
            analyzeMoves();
        }
        return cachedCaptureMoves;
    }
    
    /**
     * Obté només els moviments no-captura.
     * 
     * @return Llista de moviments no-captura
     */
    public List<Point> getNonCaptureMoves() {
        if (!captureMovesAnalyzed) {
            analyzeMoves();
        }
        return cachedNonCaptureMoves;
    }
    
    /**
     * Analitza tots els moviments i els classifica en captura/no-captura.
     */
    private void analyzeMoves() {
        cachedCaptureMoves = new ArrayList<>();
        cachedNonCaptureMoves = new ArrayList<>();
        
        List<Point> allMoves = getMoves();
        PlayerType currentPlayer = getCurrentPlayer();
        
        for (Point move : allMoves) {
            if (isCapturingMove(move, currentPlayer)) {
                cachedCaptureMoves.add(move);
            } else {
                cachedNonCaptureMoves.add(move);
            }
        }
        
        captureMovesAnalyzed = true;
    }
    
    /**
     * Col·loca una pedra i invalida el cache.
     * 
     * @param p Posició on col·locar la pedra
     */
    @Override
    public void placeStone(Point p) {
        super.placeStone(p);
        invalidateCache();
    }
    
    /**
     * Invalida tot el cache quan l'estat canvia.
     */
    private void invalidateCache() {
        movesCached = false;
        cachedMoves = null;
        cachedHashCode = null;
        captureMovesAnalyzed = false;
        cachedCaptureMoves = null;
        cachedNonCaptureMoves = null;
    }
    
    /**
     * Calcula un hashcode per a l'estat actual.
     * 
     * @return Hashcode de l'estat
     */
    @Override
    public int hashCode() {
        if (cachedHashCode == null) {
            cachedHashCode = super.hashCode();
        }
        return cachedHashCode;
    }
    
    /**
     * Comprova si un moviment és de captura.
     * Un moviment captura si connecta amb pedres pròpies I pot capturar enemics.
     * 
     * @param p Posició a comprovar
     * @param player Jugador que fa el moviment
     * @return True si el moviment captura pedres enemigues
     */
    public boolean isCapturingMove(Point p, PlayerType player) {
        if (!isInBounds(p) || getColor(p) != null) {
            return false;
        }
        
        // Comprovar si connecta amb pedres pròpies (requisit per captura)
        boolean connectsWithOwn = false;
        
        for (Dir dir : Dir.values()) {
            Point adj = dir.add(p);
            if (isInBounds(adj) && getColor(adj) == player) {
                connectsWithOwn = true;
                break;
            }
        }
        
        return connectsWithOwn;
    }
    
    /**
     * Comprova si un moviment és de captura (amb jugador actual).
     * 
     * @param p Posició a comprovar
     * @return True si el moviment captura
     */
    public boolean isCapturingMove(Point p) {
        return isCapturingMove(p, getCurrentPlayer());
    }
    
    /**
     * Estima quantes pedres enemigues pot capturar un moviment.
     * 
     * @param p Posició del moviment
     * @return Nombre estimat de pedres capturades
     */
    public int estimateCaptureValue(Point p) {
        if (!isCapturingMove(p)) {
            return 0;
        }
        
        PlayerType player = getCurrentPlayer();
        PlayerType enemy = player.opposite();
        int captureValue = 0;
        
        // Comptar pedres enemigues adjacents
        for (Dir dir : Dir.values()) {
            Point adj = dir.add(p);
            if (isInBounds(adj) && getColor(adj) == enemy) {
                captureValue++;
            }
        }
        
        return captureValue;
    }
    
    /**
     * Crea una còpia optimitzada de l'estat actual.
     * 
     * @return Nova instància de GameStatusTunned
     */
    public GameStatusTunned copy() {
        return new GameStatusTunned(this);
    }
    
    /**
     * Avaluació ràpida de l'estat per heurístiques.
     * 
     * @return Mapa amb mètriques de l'estat
     */
    public Map<String, Integer> getQuickMetrics() {
        Map<String, Integer> metrics = new HashMap<>();
        
        int p1Stones = 0;
        int p2Stones = 0;
        
        for (int i = 0; i < getSquareSize(); i++) {
            for (int j = 0; j < getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!isInBounds(p)) continue;
                
                PlayerType color = getColor(p);
                if (color == PlayerType.PLAYER1) {
                    p1Stones++;
                } else if (color == PlayerType.PLAYER2) {
                    p2Stones++;
                }
            }
        }
        
        metrics.put("player1_stones", p1Stones);
        metrics.put("player2_stones", p2Stones);
        metrics.put("available_moves", getMoves().size());
        metrics.put("capture_moves", getCaptureMoves().size());
        
        return metrics;
    }
    
    /**
     * Obté la mida d'un grup connectat que conté un punt.
     * 
     * @param p Punt del grup
     * @return Mida del grup (0 si no hi ha pedra)
     */
    public int getGroupSize(Point p) {
        if (!isInBounds(p)) return 0;
        
        PlayerType color = getColor(p);
        if (color == null) return 0;
        
        // Usar BFS per comptar el grup
        Set<Point> visitats = new HashSet<>();
        Queue<Point> queue = new LinkedList<>();
        queue.add(p);
        visitats.add(p);
        
        int mida = 0;
        while (!queue.isEmpty()) {
            Point actual = queue.poll();
            mida++;
            
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(actual);
                if (isInBounds(adj) && !visitats.contains(adj) && getColor(adj) == color) {
                    visitats.add(adj);
                    queue.add(adj);
                }
            }
        }
        
        return mida;
    }
    
    /**
     * Obté tots els grups d'un jugador amb les seves mides.
     * 
     * @param player Jugador
     * @return Mapa de punt representatiu → mida del grup
     */
    public Map<Point, Integer> getGroupSizes(PlayerType player) {
        Map<Point, Integer> grups = new HashMap<>();
        Set<Point> visitats = new HashSet<>();
        
        for (int i = 0; i < getSquareSize(); i++) {
            for (int j = 0; j < getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!isInBounds(p) || visitats.contains(p)) continue;
                if (getColor(p) != player) continue;
                
                int mida = getGroupSize(p);
                grups.put(p, mida);
                
                // Marcar tot el grup com visitat
                marcarGrupVisitat(p, player, visitats);
            }
        }
        
        return grups;
    }
    
    /**
     * Marca tots els punts d'un grup com a visitats.
     */
    private void marcarGrupVisitat(Point inici, PlayerType color, Set<Point> visitats) {
        Queue<Point> queue = new LinkedList<>();
        queue.add(inici);
        visitats.add(inici);
        
        while (!queue.isEmpty()) {
            Point actual = queue.poll();
            
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(actual);
                if (isInBounds(adj) && !visitats.contains(adj) && getColor(adj) == color) {
                    visitats.add(adj);
                    queue.add(adj);
                }
            }
        }
    }
    
    /**
     * Troba el grup més gran adjacent a un punt.
     * 
     * @param p Punt central
     * @param player Jugador a buscar
     * @param radi Radi de cerca (1 = adjacents directes, 2 = radi 2, etc.)
     * @return Mida del grup més gran trobat
     */
    public int findLargestNearbyGroup(Point p, PlayerType player, int radi) {
        Set<Point> explorats = new HashSet<>();
        Set<Point> candidats = new HashSet<>();
        
        // BFS per trobar tots els punts dins del radi
        Queue<Point> queue = new LinkedList<>();
        Map<Point, Integer> distancies = new HashMap<>();
        
        queue.add(p);
        distancies.put(p, 0);
        
        while (!queue.isEmpty()) {
            Point actual = queue.poll();
            int dist = distancies.get(actual);
            
            if (dist >= radi) continue;
            
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(actual);
                if (!isInBounds(adj) || distancies.containsKey(adj)) continue;
                
                distancies.put(adj, dist + 1);
                queue.add(adj);
                
                if (getColor(adj) == player) {
                    candidats.add(adj);
                }
            }
        }
        
        // Trobar el grup més gran entre els candidats
        int midaMaxima = 0;
        for (Point candidat : candidats) {
            if (explorats.contains(candidat)) continue;
            
            int midaGrup = getGroupSize(candidat);
            midaMaxima = Math.max(midaMaxima, midaGrup);
            
            // Marcar tot el grup com explorat
            marcarGrupVisitat(candidat, player, explorats);
        }
        
        return midaMaxima;
    }
    
    /**
     * Detecta si un punt és una possible trampa.
     * Una trampa és una pedra aïllada/petit grup amb:
     * 1. Enemic adjacent
     * 2. Grup aliat proper més gran que pot contra-atacar
     * 
     * @param p Punt a analitzar
     * @return true si és una trampa efectiva
     */
    public boolean isPossibleTrap(Point p) {
        if (!isInBounds(p)) return false;
        
        PlayerType color = getColor(p);
        if (color == null) return false;
        
        // Ha de ser un grup petit (trampa)
        int midaGrup = getGroupSize(p);
        if (midaGrup > 2) return false;
        
        PlayerType enemic = color.opposite();
        
        // Ha de tenir enemic adjacent
        boolean teEnemicAdjacent = false;
        int midaGrupEnemicAdjacent = 0;
        
        for (Dir dir : Dir.values()) {
            Point adj = dir.add(p);
            if (isInBounds(adj) && getColor(adj) == enemic) {
                teEnemicAdjacent = true;
                midaGrupEnemicAdjacent = Math.max(midaGrupEnemicAdjacent, getGroupSize(adj));
            }
        }
        
        if (!teEnemicAdjacent) return false;
        
        // Buscar grup aliat proper que pugui contra-atacar
        int grupAliatProper = findLargestNearbyGroup(p, color, 2);
        
        // És trampa si grup aliat > grup enemic que capturaria
        return grupAliatProper > midaGrupEnemicAdjacent + midaGrup;
    }
    
    /**
     * Avalua si capturar un punt seria caure en una trampa.
     * 
     * @param p Punt que l'enemic podria capturar
     * @param player Jugador que capturaria
     * @return true si és una trampa de l'enemic
     */
    public boolean isEnemyTrap(Point p, PlayerType player) {
        if (!isInBounds(p)) return false;
        
        PlayerType colorTrampa = getColor(p);
        if (colorTrampa == null || colorTrampa == player) return false;
        
        // Comprovar si és trampa des del punt de vista de l'enemic
        int midaGrupTrampa = getGroupSize(p);
        if (midaGrupTrampa > 2) return false;
        
        // Tenim pedres adjacents?
        boolean tePropriAdjacent = false;
        int midaGrupPropiAdjacent = 0;
        
        for (Dir dir : Dir.values()) {
            Point adj = dir.add(p);
            if (isInBounds(adj) && getColor(adj) == player) {
                tePropriAdjacent = true;
                midaGrupPropiAdjacent = Math.max(midaGrupPropiAdjacent, getGroupSize(adj));
            }
        }
        
        if (!tePropriAdjacent) return false;
        
        // L'enemic té grup proper més gran?
        int grupEnemicProper = findLargestNearbyGroup(p, colorTrampa, 2);
        
        return grupEnemicProper > midaGrupPropiAdjacent + midaGrupTrampa;
    }
    
}
