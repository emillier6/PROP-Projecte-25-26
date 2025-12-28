/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust.players.MillierAranda;

import edu.upc.epsevg.prop.oust.Dir;
import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.PlayerType;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Classe optimitzada que extén GameStatus amb funcionalitats de cache i 
 * anàlisi de moviments per millorar el rendiment del jugador Minimax.
 * 
 * <p>Aquesta classe proporciona:</p>
 * <ul>
 *   <li>Cache de moviments per evitar recàlculs costosos</li>
 *   <li>Anàlisi i classificació de moviments (captura vs no-captura)</li>
 *   <li>Funcions d'avaluació de grups i vulnerabilitats</li>
 *   <li>Detecció de trampes estratègiques</li>
 * </ul>
 * 
 * <p>El sistema de cache s'invalida automàticament quan es col·loca una nova pedra,
 * garantint la coherència de les dades.</p>
 * 
 * @author Erik Millier, Alex Aranda
 * 
 */
public class GameStatusTunned extends GameStatus {
    
    /** Llista de moviments calculats i emmagatzemats en cache. */
    private List<Point> cachedMoves;
    /** Indica si els moviments han estat calculats i estan en cache. */
    private boolean movesCached;
    
    /** Hashcode precalculat de l'estat actual per optimitzar comparacions. */
    private Integer cachedHashCode;
    
    /** Llista de moviments que resulten en captures, emmagatzemada en cache. */
    private List<Point> cachedCaptureMoves;
    /** Llista de moviments que no resulten en captures, emmagatzemada en cache. */
    private List<Point> cachedNonCaptureMoves;
    /** Indica si els moviments han estat analitzats i classificats. */
    private boolean captureMovesAnalyzed;
    
    /**
     * Constructor que crea un nou estat optimitzat a partir d'un GameStatus existent.
     * 
     * <p>Inicialitza tots els sistemes de cache en estat buit, que seran
     * poblats sota demanda quan es necessitin.</p>
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
     * <p>Els moviments es calculen només la primera vegada que es demanen,
     * i les crides posteriors retornen la llista en cache.</p>
     * 
     * @return Llista de moviments disponibles per al jugador actual
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
     * Obté només els moviments que resulten en captura de pedres enemigues.
     * 
     * <p>Un moviment es considera de captura si connecta amb pedres pròpies
     * i té pedres enemigues adjacents que poden ser capturades segons les
     * regles d'Oust.</p>
     * 
     * @return Llista de moviments de captura disponibles
     */
    public List<Point> getCaptureMoves() {
        if (!captureMovesAnalyzed) {
            analyzeMoves();
        }
        return cachedCaptureMoves;
    }
    
    /**
     * Obté només els moviments que no resulten en captura.
     * 
     * <p>Aquests moviments són útils per construir grups sense activar
     * captures immediates.</p>
     * 
     * @return Llista de moviments no-captura disponibles
     */
    public List<Point> getNonCaptureMoves() {
        if (!captureMovesAnalyzed) {
            analyzeMoves();
        }
        return cachedNonCaptureMoves;
    }
    
    /**
     * Analitza tots els moviments i els classifica en captura/no-captura.
     * 
     * <p>Aquest mètode s'executa només una vegada per estat, i els resultats
     * es guarden en cache per a accessos posteriors.</p>
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
     * <p>Després de col·locar una pedra, l'estat del joc canvia completament,
     * per tant tot el cache queda obsolet i és invalidat.</p>
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
     * 
     * <p>Aquest mètode s'ha de cridar sempre que l'estat del tauler
     * es modifiqui, per assegurar que les dades en cache no quedin obsoletes.</p>
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
     * <p>El hashcode es calcula només una vegada i es guarda en cache
     * per optimitzar les comparacions d'estats en la taula de transposició.</p>
     * 
     * @return Hashcode de l'estat actual
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
     * 
     * <p>Un moviment captura si connecta amb pedres pròpies (requisit per
     * continuar el torn segons les regles d'Oust) i té oportunitat de
     * capturar pedres enemigues.</p>
     * 
     * @param p Posició a comprovar
     * @param player Jugador que fa el moviment
     * @return true si el moviment captura pedres enemigues, false altrament
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
     * Comprova si un moviment és de captura per al jugador actual.
     * 
     * <p>Aquesta és una versió simplificada de {@link #isCapturingMove(Point, PlayerType)}
     * que utilitza el jugador actual.</p>
     * 
     * @param p Posició a comprovar
     * @return true si el moviment captura, false altrament
     */
    public boolean isCapturingMove(Point p) {
        return isCapturingMove(p, getCurrentPlayer());
    }
    
    /**
     * Estima quantes pedres enemigues pot capturar un moviment.
     * 
     * <p>Aquesta estimació es basa en comptar les pedres enemigues adjacents
     * a la posició del moviment. És una aproximació que s'utilitza per ordenar
     * moviments de captura.</p>
     * 
     * @param p Posició del moviment
     * @return Nombre estimat de pedres capturades (0 si no és captura)
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
     * <p>Nota: El cache no es copia, cada instància tindrà el seu propi cache
     * independent.</p>
     * 
     * @return Nova instància de GameStatusTunned amb l'estat copiat
     */
    public edu.upc.epsevg.prop.oust.players.MillierAranda.GameStatusTunned copy() {
        return new edu.upc.epsevg.prop.oust.players.MillierAranda.GameStatusTunned(this);
    }
    
    /**
     * Avaluació ràpida de l'estat per heurístiques.
     * 
     * <p>Proporciona mètriques bàsiques de l'estat sense càlculs complexos,
     * útils per avaluacions ràpides o debug.</p>
     * 
     * @return Mapa amb mètriques de l'estat (pedres, moviments disponibles, etc.)
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
     * <p>Utilitza una cerca en amplada (BFS) per comptar totes les pedres
     * del mateix color connectades a la posició donada.</p>
     * 
     * @param p Punt del grup a comptar
     * @return Mida del grup (0 si no hi ha pedra a la posició)
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
     * <p>Recorre tot el tauler identificant grups únics i calculant-ne la mida.
     * Cada grup només es processa una vegada.</p>
     * 
     * @param player Jugador del qual es volen obtenir els grups
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
     * 
     * <p>Utilitzat internament per evitar processar el mateix grup múltiples vegades.</p>
     * 
     * @param inici Punt inicial del grup
     * @param color Color del jugador
     * @param visitats Conjunt de punts ja visitats (s'actualitza durant l'execució)
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
     * Troba el grup més gran adjacent a un punt dins d'un radi especificat.
     * 
     * <p>Útil per avaluar el suport estratègic d'una posició, per exemple
     * per detectar si una trampa té un grup aliat proper capaç de contra-atacar.</p>
     * 
     * @param p Punt central des d'on buscar
     * @param player Jugador del qual buscar grups
     * @param radi Radi màxim de cerca (1 = adjacents directes, 2 = radi 2, etc.)
     * @return Mida del grup més gran trobat dins del radi (0 si no n'hi ha cap)
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
     * 
     * <p>Una trampa és una pedra aïllada o petit grup amb:</p>
     * <ol>
     *   <li>Un enemic adjacent (que podria intentar capturar-la)</li>
     *   <li>Un grup aliat proper més gran que pot contra-atacar</li>
     * </ol>
     * 
     * <p>Les trampes són estratègies per atreure l'enemic a una posició
     * desavantatjosa.</p>
     * 
     * @param p Punt a analitzar
     * @return true si és una trampa efectiva, false altrament
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
     * Avalua si capturar un punt seria caure en una trampa de l'enemic.
     * 
     * <p>Aquesta funció és útil per evitar moviments aparentment bons
     * (captures) que en realitat són trampes de l'enemic.</p>
     * 
     * @param p Punt que l'enemic podria estar oferint com a trampa
     * @param player Jugador que està considerant capturar
     * @return true si capturar aquest punt seria caure en una trampa, false altrament
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
