/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust.players.MillierAranda;


import edu.upc.epsevg.prop.oust.Dir;
import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.GameStatusTunned;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.PlayerType;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.*;

/**
 * PlayerMiniMax implementa un jugador basat en l'algorisme Minimax
 * amb profunditat limitada i poda alpha-beta per al joc Oust.
 *
 * <p>En Oust, un torn pot consistir en diverses col·locacions consecutives
 * a causa de les captures encadenades. Per aquest motiu, la cerca Minimax
 * es realitza a nivell de torns complets i no de col·locacions individuals.</p>
 *
 * <p>Per evitar l'explosió combinatòria associada a explorar totes les possibles
 * seqüències de captures, aquest jugador aplica diverses optimitzacions:</p>
 *
 * <ul>
 *   <li>Només es ramifica sobre la primera col·locació del torn (estratègia Top-K).</li>
 *   <li>La resta de col·locacions del mateix torn es completen de manera greedy.</li>
 *   <li>S'utilitza poda alpha-beta per reduir l'espai de cerca.</li>
 * </ul>
 *
 * <p>La funció heurística combina informació de material, mobilitat i
 * amenaces immediates del rival, amb l'objectiu de defensar-se de jugadors
 * altament agressius com MalaOustiaPlayer.</p>
 *
 * <p>Aquesta versió ignora el control de temps i està pensada per a Minimax
 * amb profunditat fixa.</p>
 * 
 * @author Erik Millier
 */
public class PlayerMiniMax implements IPlayer, IAuto {
    
    private String name;
    private int profunditatMaxima;
    private volatile boolean timeout; // volatile per visibilitat entre threads
    private PlayerType jugadorPropi;
    private long nodesExplorats;
    
    // Taula de transposició per evitar recalcular estats ja visitats
    private Map<Integer, TranspositionEntry> taulaTransposicio;
    
    // Constants per a la heurística
    private static final int VICTORIA = 1000000;
    private static final int DERROTA = -1000000;
    
    /**
     * Constructor del jugador Minimax.
     * 
     * @param profunditatMaxima Profunditat màxima de cerca de l'algorisme
     */
    public PlayerMiniMax(int profunditatMaxima) {
        this.name = "MiniMax";
        this.profunditatMaxima = profunditatMaxima;
        this.timeout = false;
        this.nodesExplorats = 0;
        this.taulaTransposicio = new HashMap<>();
    }
    
    @Override
    public void timeout() {
        timeout = true;
    }
    
    @Override
    public String getName() {
        return name + " (Prof: " + profunditatMaxima + ")";
    }
    
    /**
     * Decideix el moviment del jugador utilitzant Minimax amb poda alpha-beta.
     * 
     * @param s Estat actual del joc
     * @return El millor moviment trobat
     */
    @Override
    public PlayerMove move(GameStatus s) {
        timeout = false;
        nodesExplorats = 0;
        jugadorPropi = s.getCurrentPlayer();
        taulaTransposicio.clear();
        
        GameStatusTunned estatOptimitzat = new GameStatusTunned(s);
        
        List<Point> millorCami = new ArrayList<>();
        int millorValor = Integer.MIN_VALUE;
        
        List<Point> moviments = estatOptimitzat.getMoves();
        
        if (moviments.isEmpty()) {
            return new PlayerMove(millorCami, nodesExplorats, profunditatMaxima, SearchType.MINIMAX);
        }
        
        if (moviments.size() == 1) {
            millorCami = construirCamiComplet(estatOptimitzat, moviments.get(0));
            return new PlayerMove(millorCami, nodesExplorats, profunditatMaxima, SearchType.MINIMAX);
        }
        
        // Ordenar moviments amb heurística avançada
        moviments = ordenarMovimentsAvançat(estatOptimitzat, moviments);
        
        for (Point mov : moviments) {
            if (timeout) break;
            
            GameStatusTunned nouEstat = new GameStatusTunned(estatOptimitzat);
            List<Point> cami = construirCamiComplet(nouEstat, mov);
            
            int valor = minimax(nouEstat, profunditatMaxima - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (valor > millorValor) {
                millorValor = valor;
                millorCami = new ArrayList<>(cami);
            }
        }
        
        if (millorCami.isEmpty() && !moviments.isEmpty()) {
            millorCami = construirCamiComplet(estatOptimitzat, moviments.get(0));
        }
        
        return new PlayerMove(millorCami, nodesExplorats, profunditatMaxima, SearchType.MINIMAX);
    }
    
    /**
     * Construeix el camí complet d'un moviment.
     * IMPORTANT: Després de captures, seguim tenint el torn i hem de continuar.
     */
    private List<Point> construirCamiComplet(GameStatusTunned estat, Point primerMov) {
        List<Point> cami = new ArrayList<>();
        GameStatusTunned nouEstat = new GameStatusTunned(estat);
        PlayerType jugadorActual = nouEstat.getCurrentPlayer();
        
        cami.add(primerMov);
        nouEstat.placeStone(primerMov);
        
        // Continuar mentre seguim tenint el torn
        int maxIteracions = 50; // Protecció contra bucles
        int iter = 0;
        
        while (nouEstat.getCurrentPlayer() == jugadorActual && !nouEstat.isGameOver() && iter < maxIteracions) {
            List<Point> captureMoves = nouEstat.getCaptureMoves();
            List<Point> nonCaptureMoves = nouEstat.getNonCaptureMoves();
            
            // Si hi ha moviments de captura, prioritzar-los SEMPRE
            Point seguentMov;
            if (!captureMoves.isEmpty()) {
                seguentMov = seleccionarMillorMovimentCaptura(nouEstat, captureMoves);
            } else if (!nonCaptureMoves.isEmpty()) {
                seguentMov = seleccionarMillorMovimentNoCaptura(nouEstat, nonCaptureMoves);
            } else {
                break; // No hi ha moviments
            }
            
            cami.add(seguentMov);
            nouEstat.placeStone(seguentMov);
            iter++;
        }
        
        return cami;
    }
    
    /**
     * Selecciona el millor moviment de captura.
     * Tria el que captura més pedres enemigues.
     */
    private Point seleccionarMillorMovimentCaptura(GameStatusTunned estat, List<Point> captureMoves) {
        Point millorMov = captureMoves.get(0);
        int millorValor = Integer.MIN_VALUE;
        
        for (Point mov : captureMoves) {
            int captureValue = estat.estimateCaptureValue(mov);
            GameStatusTunned temp = new GameStatusTunned(estat);
            temp.placeStone(mov);
            int heurValue = heuristicaRapida(temp);
            
            int valor = captureValue * 100 + heurValue;
            
            if (valor > millorValor) {
                millorValor = valor;
                millorMov = mov;
            }
        }
        
        return millorMov;
    }
    
    /**
     * Selecciona el millor moviment no-captura.
     * Prioritza CONTROL DEL CENTRE especialment en fase inicial.
     */
    private Point seleccionarMillorMovimentNoCaptura(GameStatusTunned estat, List<Point> nonCaptureMoves) {
        Point millorMov = nonCaptureMoves.get(0);
        int millorValor = Integer.MIN_VALUE;
        int centre = estat.getSize() / 2;
        
        // Comptar pedres totals per determinar fase
        int totalPedres = 0;
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (estat.isInBounds(p) && estat.getColor(p) != null) {
                    totalPedres++;
                }
            }
        }
        
        int maxPedres = (int)(estat.getSize() * estat.getSize() * 0.7);
        boolean faseTemprana = totalPedres < maxPedres * 0.3;
        
        for (Point mov : nonCaptureMoves) {
            // Preferir posicions centrals (MOLT més en fase inicial)
            int distanciaCentre = Math.abs(mov.x - centre) + Math.abs(mov.y - centre);
            int valorPosicio = (estat.getSize() - distanciaCentre);
            
            if (faseTemprana) {
                valorPosicio *= 5; // x5 en fase inicial!
            } else {
                valorPosicio *= 2;
            }
            
            GameStatusTunned temp = new GameStatusTunned(estat);
            temp.placeStone(mov);
            int heurValue = heuristicaRapida(temp);
            
            int valor = valorPosicio * 30 + heurValue;
            
            if (valor > millorValor) {
                millorValor = valor;
                millorMov = mov;
            }
        }
        
        return millorMov;
    }
    
    /**
     * Algorisme Minimax amb poda alpha-beta i taula de transposició.
     */
    private int minimax(GameStatusTunned estat, int profunditat, int alpha, int beta, boolean esMaximitzant) {
        nodesExplorats++;
        
        if (timeout) return 0;
        
        // Comprovar taula de transposició
        int hashCode = estat.hashCode();
        TranspositionEntry entry = taulaTransposicio.get(hashCode);
        if (entry != null && entry.profunditat >= profunditat) {
            return entry.valor;
        }
        
        if (estat.isGameOver()) {
            int valor = avaluarEstatTerminal(estat);
            taulaTransposicio.put(hashCode, new TranspositionEntry(valor, profunditat));
            return valor;
        }
        
        if (profunditat == 0) {
            int valor = heuristica(estat);
            taulaTransposicio.put(hashCode, new TranspositionEntry(valor, profunditat));
            return valor;
        }
        
        List<Point> moviments = estat.getMoves();
        
        if (moviments.isEmpty()) {
            int valor = heuristica(estat);
            taulaTransposicio.put(hashCode, new TranspositionEntry(valor, profunditat));
            return valor;
        }
        
        moviments = ordenarMovimentsAvançat(estat, moviments);
        
        if (esMaximitzant) {
            int maxValor = Integer.MIN_VALUE;
            
            for (Point mov : moviments) {
                if (timeout) break;
                
                GameStatusTunned nouEstat = new GameStatusTunned(estat);
                aplicarMovimentComplet(nouEstat, mov);
                
                int valor = minimax(nouEstat, profunditat - 1, alpha, beta, false);
                maxValor = Math.max(maxValor, valor);
                alpha = Math.max(alpha, valor);
                
                if (beta <= alpha) break;
            }
            
            taulaTransposicio.put(hashCode, new TranspositionEntry(maxValor, profunditat));
            return maxValor;
            
        } else {
            int minValor = Integer.MAX_VALUE;
            
            for (Point mov : moviments) {
                if (timeout) break;
                
                GameStatusTunned nouEstat = new GameStatusTunned(estat);
                aplicarMovimentComplet(nouEstat, mov);
                
                int valor = minimax(nouEstat, profunditat - 1, alpha, beta, true);
                minValor = Math.min(minValor, valor);
                beta = Math.min(beta, valor);
                
                if (beta <= alpha) break;
            }
            
            taulaTransposicio.put(hashCode, new TranspositionEntry(minValor, profunditat));
            return minValor;
        }
    }
    
    /**
     * Aplica un moviment complet amb PRIORITAT per captures.
     */
    private void aplicarMovimentComplet(GameStatusTunned estat, Point mov) {
        PlayerType jugadorActual = estat.getCurrentPlayer();
        estat.placeStone(mov);
        
        int maxIteracions = 50;
        int iter = 0;
        
        while (estat.getCurrentPlayer() == jugadorActual && !estat.isGameOver() && iter < maxIteracions) {
            List<Point> captureMoves = estat.getCaptureMoves();
            List<Point> nonCaptureMoves = estat.getNonCaptureMoves();
            
            Point millorMov;
            if (!captureMoves.isEmpty()) {
                millorMov = seleccionarMillorMovimentCaptura(estat, captureMoves);
            } else if (!nonCaptureMoves.isEmpty()) {
                millorMov = seleccionarMillorMovimentNoCaptura(estat, nonCaptureMoves);
            } else {
                break;
            }
            
            estat.placeStone(millorMov);
            iter++;
        }
    }
    
    /**
     * Ordena moviments amb PRIORITAT ABSOLUTA per captures.
     * Moviments de captura sempre van primer.
     */
    private List<Point> ordenarMovimentsAvançat(GameStatusTunned estat, List<Point> moviments) {
        List<MovimentAmbValor> captures = new ArrayList<>();
        List<MovimentAmbValor> noCaptures = new ArrayList<>();
        
        for (Point mov : moviments) {
            GameStatusTunned temp = new GameStatusTunned(estat);
            temp.placeStone(mov);
            
            int captureValue = estat.estimateCaptureValue(mov);
            int heurValue = heuristicaRapida(temp);
            int valor = captureValue * 1000 + heurValue;
            
            MovimentAmbValor mv = new MovimentAmbValor(mov, valor);
            
            if (estat.isCapturingMove(mov)) {
                captures.add(mv);
            } else {
                noCaptures.add(mv);
            }
        }
        
        // Ordenar captures per valor (millors primer)
        captures.sort((a, b) -> Integer.compare(b.valor, a.valor));
        
        // Ordenar no-captures per valor
        noCaptures.sort((a, b) -> Integer.compare(b.valor, a.valor));
        
        // CAPTURES PRIMER, després no-captures
        List<Point> resultat = new ArrayList<>();
        for (MovimentAmbValor mv : captures) {
            resultat.add(mv.moviment);
        }
        for (MovimentAmbValor mv : noCaptures) {
            resultat.add(mv.moviment);
        }
        
        return resultat;
    }
    
    /**
     * Avalua un estat terminal.
     */
    private int avaluarEstatTerminal(GameStatusTunned estat) {
        PlayerType guanyador = estat.GetWinner();
        if (guanyador == jugadorPropi) {
            return VICTORIA;
        } else if (guanyador != null) {
            return DERROTA;
        }
        return 0;
    }
    
    /**
     * Heurística ràpida per ordenació de moviments.
     */
    private int heuristicaRapida(GameStatusTunned estat) {
        int pedresPropi = 0;
        int pedresEnemic = 0;
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!estat.isInBounds(p)) continue;
                
                PlayerType color = estat.getColor(p);
                if (color == jugadorPropi) {
                    pedresPropi++;
                } else if (color != null) {
                    pedresEnemic++;
                }
            }
        }
        
        return (pedresPropi - pedresEnemic) * 100;
    }
    
    /**
     * Funció heurística completa i avançada amb detecció de trampes.
     * Inspirada en MVP però millorada per Oust.
     */
    private int heuristica(GameStatusTunned estat) {
        int score = 0;
        
        // Comptar pedres i grups amb informació detallada
        int pedresPropi = 0;
        int pedresEnemic = 0;
        Map<Point, Boolean> visitats = new HashMap<>();
        int grupsPropi = 0;
        int grupsEnemic = 0;
        int midaGrupPropiMax = 0;
        int midaGrupEnemicMax = 0;
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!estat.isInBounds(p)) continue;
                
                PlayerType color = estat.getColor(p);
                if (color == jugadorPropi) {
                    pedresPropi++;
                    if (!visitats.containsKey(p)) {
                        int mida = marcarGrup(estat, p, jugadorPropi, visitats);
                        grupsPropi++;
                        midaGrupPropiMax = Math.max(midaGrupPropiMax, mida);
                    }
                } else if (color != null) {
                    pedresEnemic++;
                    if (!visitats.containsKey(p)) {
                        int mida = marcarGrup(estat, p, color, visitats);
                        grupsEnemic++;
                        midaGrupEnemicMax = Math.max(midaGrupEnemicMax, mida);
                    }
                }
            }
        }
        
        // Factor 1: Diferència de pedres (pes moderat)
        score += (pedresPropi - pedresEnemic) * 30;
        
        // Factor 2: CONNECTIVITAT - menys grups és MOLT millor
        score -= grupsPropi * 40;
        score += grupsEnemic * 40;
        
        // Factor 3: Grup màxim (tener un grup gran és clau!)
        score += midaGrupPropiMax * 25;
        score -= midaGrupEnemicMax * 25;
        
        // Factor 4: Penalització per tenir massa pedres (vulnerabilitat)
        if (pedresPropi > pedresEnemic + 8) {
            score -= (pedresPropi - pedresEnemic) * 30;
        }
        
        // Factor 5: Mobilitat
        int mobilitat = estat.getMoves().size();
        if (estat.getCurrentPlayer() == jugadorPropi) {
            score += mobilitat * 12;
        } else {
            score -= mobilitat * 12;
        }
        
        // Factor 6: Control del centre (més pes en fase inicial)
        score += avaluarControlCentreMillorat(estat, pedresPropi + pedresEnemic);
        
        // Factor 7: Situació dominant
        if (pedresEnemic == 0 && pedresPropi > 0) {
            score += VICTORIA / 2;
        }
        if (pedresPropi == 0 && pedresEnemic > 0) {
            score -= VICTORIA / 2;
        }
        
        return score;
    }
    
    /**
     * Avalua el control del centre amb més pes en fase inicial.
     */
    private int avaluarControlCentreMillorat(GameStatusTunned estat, int totalPedres) {
        int score = 0;
        int mida = estat.getSize();
        int centre = mida / 2;
        int maxPedres = (int)(mida * mida * 0.7);
        
        // Determinar fase del joc
        boolean faseTemprana = totalPedres < maxPedres * 0.3;
        int multiplicador = faseTemprana ? 4 : 1; // x4 en fase inicial!
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point pos = new Point(i, j);
                if (!estat.isInBounds(pos)) continue;
                
                PlayerType pedra = estat.getColor(pos);
                
                if (pedra != null) {
                    int distanciaCentre = Math.abs(i - centre) + Math.abs(j - centre);
                    int valorPosicio = Math.max(1, mida - distanciaCentre) * multiplicador;
                    
                    if (pedra == jugadorPropi) {
                        score += valorPosicio * 3;
                    } else {
                        score -= valorPosicio * 3;
                    }
                }
            }
        }
        
        return score;
    }
    
    /**
     * Marca tots els punts d'un grup connectat i retorna la mida.
     */
    private int marcarGrup(GameStatusTunned estat, Point inici, PlayerType color, Map<Point, Boolean> visitats) {
        Stack<Point> pila = new Stack<>();
        pila.push(inici);
        int mida = 0;
        
        while (!pila.isEmpty()) {
            Point actual = pila.pop();
            if (visitats.containsKey(actual)) continue;
            
            visitats.put(actual, true);
            mida++;
            
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(actual);
                if (estat.isInBounds(adj) && estat.getColor(adj) == color && !visitats.containsKey(adj)) {
                    pila.push(adj);
                }
            }
        }
        
        return mida;
    }
    
    /**
     * Avalua el control del centre del tauler.
     */
    private int avaluarControlCentre(GameStatusTunned estat) {
        int score = 0;
        int mida = estat.getSize();
        int centre = mida / 2;
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point pos = new Point(i, j);
                if (!estat.isInBounds(pos)) continue;
                
                PlayerType pedra = estat.getColor(pos);
                if (pedra != null) {
                    int distanciaCentre = Math.abs(i - centre) + Math.abs(j - centre);
                    int valorPosicio = Math.max(1, mida - distanciaCentre);
                    
                    if (pedra == jugadorPropi) {
                        score += valorPosicio * 5;
                    } else {
                        score -= valorPosicio * 5;
                    }
                }
            }
        }
        
        return score;
    }
    
    /**
     * Avalua les captures potencials disponibles.
     */
    private int avaluarCapturesPotencials(GameStatusTunned estat) {
        int score = 0;
        List<Point> moviments = estat.getMoves();
        
        for (Point mov : moviments) {
            if (estat.isCapturingMove(mov)) {
                score += 25; // Bonus per moviment de captura disponible
            }
        }
        
        return score;
    }
    
    /**
     * Classe auxiliar per ordenar moviments.
     */
    private static class MovimentAmbValor {
        Point moviment;
        int valor;
        
        MovimentAmbValor(Point moviment, int valor) {
            this.moviment = moviment;
            this.valor = valor;
        }
    }
    
    /**
     * Entrada de la taula de transposició.
     */
    private static class TranspositionEntry {
        int valor;
        int profunditat;
        
        TranspositionEntry(int valor, int profunditat) {
            this.valor = valor;
            this.profunditat = profunditat;
        }
    }
    
}
