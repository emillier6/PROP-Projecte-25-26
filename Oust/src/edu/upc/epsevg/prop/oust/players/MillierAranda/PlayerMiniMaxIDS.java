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
 * PlayerMiniMaxIDS implementa un jugador Minimax amb Iterative Deepening Search.
 *
 * <p>Aquest jugador reutilitza el PlayerMiniMax amb profunditat fixa,
 * incrementant progressivament la profunditat de cerca fins que el framework
 * indica que el temps s'ha exhaurit mitjançant el mètode timeout().</p>
 *
 * <p>El jugador sempre retorna l'últim moviment complet calculat correctament
 * abans de l'expiració del temps.</p>
 * 
 * @author Erik Millier
 */
public class PlayerMiniMaxIDS implements IPlayer, IAuto {
    
    private String name;
    private volatile boolean timeout;
    private PlayerType jugadorPropi;
    private long nodesExplorats;
    
    // Taula de transposició compartida entre iteracions
    private Map<Integer, TranspositionEntry> taulaTransposicio;
    
    private static final int VICTORIA = 1000000;
    private static final int DERROTA = -1000000;
    
    /**
     * Constructor per defecte del jugador Minimax amb IDS.
     */
    public PlayerMiniMaxIDS() {
        this.name = "MiniMaxIDS";
        this.timeout = false;
        this.taulaTransposicio = new HashMap<>();
    }
    
    @Override
    public void timeout() {
        timeout = true;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Decideix el moviment utilitzant Iterative Deepening Search.
     */
    @Override
    public PlayerMove move(GameStatus s) {
        timeout = false;
        nodesExplorats = 0;
        jugadorPropi = s.getCurrentPlayer();
        taulaTransposicio.clear();
        
        GameStatusTunned estatOptimitzat = new GameStatusTunned(s);
        
        List<Point> millorCami = new ArrayList<>();
        int profunditatActual = 1;
        int profunditatMaximaAssolida = 0;
        
        List<Point> movimentsDisponibles = estatOptimitzat.getMoves();
        
        if (movimentsDisponibles.isEmpty()) {
            return new PlayerMove(new ArrayList<>(), 0, 0, SearchType.MINIMAX_IDS);
        }
        
        if (movimentsDisponibles.size() == 1) {
            List<Point> cami = construirCamiComplet(estatOptimitzat, movimentsDisponibles.get(0));
            return new PlayerMove(cami, 1, 1, SearchType.MINIMAX_IDS);
        }
        
        // Iterative Deepening
        while (!timeout) {
            List<Point> camiActual = buscarMillorMoviment(estatOptimitzat, profunditatActual);
            
            if (timeout) break;
            
            if (camiActual != null && !camiActual.isEmpty()) {
                millorCami = camiActual;
                profunditatMaximaAssolida = profunditatActual;
                profunditatActual++;
            } else {
                break;
            }
        }
        
        if (millorCami.isEmpty() && !movimentsDisponibles.isEmpty()) {
            millorCami = construirCamiComplet(estatOptimitzat, movimentsDisponibles.get(0));
            profunditatMaximaAssolida = 1;
        }
        
        return new PlayerMove(millorCami, nodesExplorats, profunditatMaximaAssolida, SearchType.MINIMAX_IDS);
    }
    
    /**
     * Busca el millor moviment a una profunditat donada.
     * USA filtratge dins de minimax.
     */
    private List<Point> buscarMillorMoviment(GameStatusTunned estat, int profunditat) {
        List<Point> millorCami = new ArrayList<>();
        int millorValor = Integer.MIN_VALUE;
        
        // Obtenir moviments JA FILTRATS
        List<Point> moviments = filtrarMovimentsSegurs(estat, estat.getMoves(), true);
        
        for (Point mov : moviments) {
            if (timeout) return null;
            
            GameStatusTunned nouEstat = new GameStatusTunned(estat);
            List<Point> cami = construirCamiComplet(nouEstat, mov);
            
            if (timeout) return null;
            
            int valor = minimax(nouEstat, profunditat - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (timeout) return null;
            
            if (valor > millorValor) {
                millorValor = valor;
                millorCami = new ArrayList<>(cami);
            }
        }
        
        return millorCami;
    }
    
    /**
     * Algorisme Minimax amb poda alpha-beta.
     * INTEGRA restriccions de distància per tallar branques perilloses.
     */
    private int minimax(GameStatusTunned estat, int profunditat, int alpha, int beta, boolean esMaximitzant) {
        nodesExplorats++;
        
        if (timeout) return 0;
        
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
        
        // FILTRAR moviments segons distància i captures
        moviments = filtrarMovimentsSegurs(estat, moviments, esMaximitzant);
        
        if (moviments.isEmpty()) {
            int valor = heuristica(estat);
            return valor;
        }
        
        if (esMaximitzant) {
            int maxValor = Integer.MIN_VALUE;
            
            for (Point mov : moviments) {
                if (timeout) return maxValor;
                
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
                if (timeout) return minValor;
                
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
     * FILTRA moviments segons regles de distància i seguretat.
     * MOLT SIMPLE I DIRECTE: Si dist < 3, DESCARTAR (excepte captures).
     */
    private List<Point> filtrarMovimentsSegurs(GameStatusTunned estat, List<Point> moviments, boolean esNostre) {
        if (!esNostre) {
            // No filtrem moviments de l'enemic
            return moviments;
        }
        
        PlayerType enemic = jugadorPropi.opposite();
        
        List<Point> movimentsSegurs = new ArrayList<>();
        
        for (Point mov : moviments) {
            // 1. SI ÉS CAPTURA: Avaluar si és segura
            if (estat.isCapturingMove(mov)) {
                GameStatusTunned simul = new GameStatusTunned(estat);
                simul.placeStone(mov);
                
                if (!potSerCapturatEnSeguent(simul, mov)) {
                    movimentsSegurs.add(mov);
                }
                continue;
            }
            
            // 2. NO ÉS CAPTURA: Comprovar distància ESTRICTAMENT
            int distMin = calcularDistanciaMinima(estat, mov, enemic);
            
            // REGLA SIMPLE: Només acceptar dist >= 3
            if (distMin >= 3) {
                movimentsSegurs.add(mov);
            }
        }
        
        // Si NO hi ha moviments dist 3+, buscar trampes dist 2
        if (movimentsSegurs.isEmpty()) {
            for (Point mov : moviments) {
                if (estat.isCapturingMove(mov)) continue;
                
                int distMin = calcularDistanciaMinima(estat, mov, enemic);
                if (distMin == 2 && esTrampaValida(estat, mov)) {
                    movimentsSegurs.add(mov);
                }
            }
        }
        
        // Si encara NO hi ha res, retornar tots (situació desesperada)
        if (movimentsSegurs.isEmpty()) {
            movimentsSegurs = moviments;
        }
        
        // ORDENAR només els moviments segurs
        return ordenarMovimentsSegursSimple(estat, movimentsSegurs);
    }
    
    /**
     * Ordena moviments SEGURS de forma simple.
     * NO avalua heurísticament distància (ja està filtrada).
     */
    private List<Point> ordenarMovimentsSegursSimple(GameStatusTunned estat, List<Point> moviments) {
        List<MovimentAmbValor> avaluats = new ArrayList<>();
        int centre = estat.getSize() / 2;
        
        for (Point mov : moviments) {
            int valor = 0;
            
            // 1. Prioritat a captures
            if (estat.isCapturingMove(mov)) {
                valor += estat.estimateCaptureValue(mov) * 1000;
            }
            
            // 2. Connexió amb aliats
            int aliatsAdjacents = 0;
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(mov);
                if (estat.isInBounds(adj) && estat.getColor(adj) == jugadorPropi) {
                    aliatsAdjacents++;
                }
            }
            valor += aliatsAdjacents * 300;
            
            // 3. Distància al centre
            int distCentre = Math.abs(mov.x - centre) + Math.abs(mov.y - centre);
            valor += (estat.getSize() - distCentre) * 50;
            
            // 4. Variació mínima
            Random rand = new Random(mov.x * 1000 + mov.y);
            valor += rand.nextInt(10) - 5;
            
            avaluats.add(new MovimentAmbValor(mov, valor));
        }
        
        avaluats.sort((a, b) -> Integer.compare(b.valor, a.valor));
        
        List<Point> resultat = new ArrayList<>();
        for (MovimentAmbValor mv : avaluats) {
            resultat.add(mv.moviment);
        }
        
        return resultat;
    }
    
    /**
     * Calcula la distància mínima a qualsevol pedra enemiga.
     */
    private int calcularDistanciaMinima(GameStatusTunned estat, Point pos, PlayerType enemic) {
        int distMin = Integer.MAX_VALUE;
        
        for (int di = -5; di <= 5; di++) {
            for (int dj = -5; dj <= 5; dj++) {
                if (di == 0 && dj == 0) continue;
                
                Point p = new Point(pos.x + di, pos.y + dj);
                if (!estat.isInBounds(p)) continue;
                if (estat.getColor(p) != enemic) continue;
                
                int dist = Math.abs(di) + Math.abs(dj);
                distMin = Math.min(distMin, dist);
            }
        }
        
        return distMin;
    }
    
    /**
     * Comprova si una posició pot ser capturada en el següent torn.
     */
    private boolean potSerCapturatEnSeguent(GameStatusTunned estat, Point pos) {
        PlayerType enemic = jugadorPropi.opposite();
        List<Point> movimentsEnemic = estat.getMoves();
        
        for (Point movEnemic : movimentsEnemic) {
            if (estat.isCapturingMove(movEnemic, enemic)) {
                GameStatusTunned test = new GameStatusTunned(estat);
                test.placeStone(movEnemic);
                
                if (test.getColor(pos) == null) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Detecta si és una trampa vàlida (dist 2).
     */
    private boolean esTrampaValida(GameStatusTunned estat, Point pos) {
        GameStatusTunned simul = new GameStatusTunned(estat);
        simul.placeStone(pos);
        
        if (simul.isPossibleTrap(pos)) {
            if (!potSerCapturatEnSeguent(simul, pos)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Construeix el camí complet d'un moviment amb prioritat per captures.
     * IMPORTANT: Filtra TAMBÉ els moviments consecutius després de captures!
     */
    private List<Point> construirCamiComplet(GameStatusTunned estat, Point primerMov) {
        List<Point> cami = new ArrayList<>();
        GameStatusTunned nouEstat = new GameStatusTunned(estat);
        PlayerType jugadorActual = nouEstat.getCurrentPlayer();
        
        cami.add(primerMov);
        nouEstat.placeStone(primerMov);
        
        int maxIteracions = 50;
        int iter = 0;
        
        while (nouEstat.getCurrentPlayer() == jugadorActual && !nouEstat.isGameOver() && iter < maxIteracions) {
            List<Point> captureMoves = nouEstat.getCaptureMoves();
            List<Point> nonCaptureMoves = nouEstat.getNonCaptureMoves();
            
            Point seguentMov = null;
            
            if (!captureMoves.isEmpty()) {
                // Filtrar captures segures
                List<Point> capturesSegures = new ArrayList<>();
                for (Point cap : captureMoves) {
                    GameStatusTunned test = new GameStatusTunned(nouEstat);
                    test.placeStone(cap);
                    if (!potSerCapturatEnSeguent(test, cap)) {
                        capturesSegures.add(cap);
                    }
                }
                
                if (!capturesSegures.isEmpty()) {
                    seguentMov = seleccionarMillorCaptura(nouEstat, capturesSegures);
                } else {
                    // Si totes les captures són perilloses, buscar no-captures segures
                    seguentMov = seleccionarMovimentSegur(nouEstat, nonCaptureMoves);
                }
            } else if (!nonCaptureMoves.isEmpty()) {
                // Filtrar no-captures per distància
                seguentMov = seleccionarMovimentSegur(nouEstat, nonCaptureMoves);
            }
            
            if (seguentMov == null) {
                break; // No hi ha moviment segur
            }
            
            cami.add(seguentMov);
            nouEstat.placeStone(seguentMov);
            iter++;
        }
        
        return cami;
    }
    
    /**
     * Aplica un moviment complet AMB FILTRATGE de moviments consecutius.
     */
    private void aplicarMovimentComplet(GameStatusTunned estat, Point mov) {
        PlayerType jugadorActual = estat.getCurrentPlayer();
        estat.placeStone(mov);
        
        int maxIteracions = 50;
        int iter = 0;
        
        while (estat.getCurrentPlayer() == jugadorActual && !estat.isGameOver() && iter < maxIteracions) {
            List<Point> captureMoves = estat.getCaptureMoves();
            List<Point> nonCaptureMoves = estat.getNonCaptureMoves();
            
            Point millorMov = null;
            
            if (!captureMoves.isEmpty()) {
                // Filtrar captures segures
                List<Point> capturesSegures = new ArrayList<>();
                for (Point cap : captureMoves) {
                    GameStatusTunned test = new GameStatusTunned(estat);
                    test.placeStone(cap);
                    if (!potSerCapturatEnSeguent(test, cap)) {
                        capturesSegures.add(cap);
                    }
                }
                
                if (!capturesSegures.isEmpty()) {
                    millorMov = seleccionarMillorCaptura(estat, capturesSegures);
                } else {
                    // Si totes les captures són perilloses, buscar no-captures segures
                    millorMov = seleccionarMovimentSegur(estat, nonCaptureMoves);
                }
            } else if (!nonCaptureMoves.isEmpty()) {
                // Filtrar no-captures per distància
                millorMov = seleccionarMovimentSegur(estat, nonCaptureMoves);
            }
            
            if (millorMov == null) {
                break; // No hi ha moviment segur, acabar torn
            }
            
            estat.placeStone(millorMov);
            iter++;
        }
    }
    
    /**
     * Selecciona un moviment SEGUR de no-captura.
     * Aplica el mateix filtratge de distància >= 3.
     */
    private Point seleccionarMovimentSegur(GameStatusTunned estat, List<Point> nonCaptures) {
        if (nonCaptures.isEmpty()) return null;
        
        PlayerType enemic = jugadorPropi.opposite();
        int centre = estat.getSize() / 2;
        
        // Filtrar per distància >= 3
        List<Point> movimentsSegurs = new ArrayList<>();
        for (Point mov : nonCaptures) {
            int distMin = calcularDistanciaMinima(estat, mov, enemic);
            if (distMin >= 3) {
                movimentsSegurs.add(mov);
            }
        }
        
        // Si NO hi ha dist >= 3, buscar trampes dist 2
        if (movimentsSegurs.isEmpty()) {
            for (Point mov : nonCaptures) {
                int distMin = calcularDistanciaMinima(estat, mov, enemic);
                if (distMin == 2 && esTrampaValida(estat, mov)) {
                    movimentsSegurs.add(mov);
                }
            }
        }
        
        // Si encara no hi ha res, retornar null (NO fer moviment suïcida)
        if (movimentsSegurs.isEmpty()) {
            return null;
        }
        
        // Triar el més proper al centre
        Point millor = movimentsSegurs.get(0);
        int millorValor = Integer.MIN_VALUE;
        
        for (Point mov : movimentsSegurs) {
            int distCentre = Math.abs(mov.x - centre) + Math.abs(mov.y - centre);
            int valor = estat.getSize() - distCentre;
            
            // Bonus per connectar amb aliats
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(mov);
                if (estat.isInBounds(adj) && estat.getColor(adj) == jugadorPropi) {
                    valor += 100;
                }
            }
            
            if (valor > millorValor) {
                millorValor = valor;
                millor = mov;
            }
        }
        
        return millor;
    }
    
    /**
     * Selecciona la millor captura.
     */
    private Point seleccionarMillorCaptura(GameStatusTunned estat, List<Point> captures) {
        Point millor = captures.get(0);
        int millorValor = estat.estimateCaptureValue(millor);
        
        for (Point mov : captures) {
            int valor = estat.estimateCaptureValue(mov);
            if (valor > millorValor) {
                millorValor = valor;
                millor = mov;
            }
        }
        
        return millor;
    }
    
    /**
     * Avalua estat terminal.
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
     * Funció heurística completa amb estratègia AGRESSIVA.
     */
    private int heuristica(GameStatusTunned estat) {
        int score = 0;
        
        int pedresPropi = 0;
        int pedresEnemic = 0;
        Map<Point, Boolean> visitats = new HashMap<>();
        Map<Point, Integer> midaGrupPropi = new HashMap<>();
        Map<Point, Integer> midaGrupEnemic = new HashMap<>();
        int grupsPropi = 0;
        int grupsEnemic = 0;
        int midaGrupPropiMaxim = 0;
        int midaGrupEnemicMaxim = 0;
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!estat.isInBounds(p)) continue;
                
                PlayerType color = estat.getColor(p);
                if (color == jugadorPropi) {
                    pedresPropi++;
                    if (!visitats.containsKey(p)) {
                        int mida = marcarGrupAmbMida(estat, p, jugadorPropi, visitats, midaGrupPropi);
                        grupsPropi++;
                        midaGrupPropiMaxim = Math.max(midaGrupPropiMaxim, mida);
                    }
                } else if (color != null) {
                    pedresEnemic++;
                    if (!visitats.containsKey(p)) {
                        int mida = marcarGrupAmbMida(estat, p, color, visitats, midaGrupEnemic);
                        grupsEnemic++;
                        midaGrupEnemicMaxim = Math.max(midaGrupEnemicMaxim, mida);
                    }
                }
            }
        }
        
        score += (pedresPropi - pedresEnemic) * 60;
        
        if (pedresPropi > pedresEnemic) {
            score += (pedresPropi - pedresEnemic) * 40;
        }
        
        score -= grupsPropi * 35;
        score += grupsEnemic * 35;
        
        score += midaGrupPropiMaxim * 35;
        score -= midaGrupEnemicMaxim * 35;
        
        if (pedresPropi > pedresEnemic + 15) {
            score -= (pedresPropi - pedresEnemic - 15) * 15;
        }
        
        int mobilitat = estat.getMoves().size();
        if (estat.getCurrentPlayer() == jugadorPropi) {
            score += mobilitat * 20;
        } else {
            score -= mobilitat * 20;
        }
        
        if (pedresEnemic == 0 && pedresPropi > 0) {
            score += VICTORIA / 2;
        }
        if (pedresPropi == 0 && pedresEnemic > 0) {
            score -= VICTORIA / 2;
        }
        
        return score;
    }
    
    /**
     * Marca grup i retorna la seva mida.
     */
    private int marcarGrupAmbMida(GameStatusTunned estat, Point inici, PlayerType color, 
                                   Map<Point, Boolean> visitats, Map<Point, Integer> midaGrup) {
        Stack<Point> pila = new Stack<>();
        List<Point> pedresGrup = new ArrayList<>();
        pila.push(inici);
        
        while (!pila.isEmpty()) {
            Point actual = pila.pop();
            if (visitats.containsKey(actual)) continue;
            
            visitats.put(actual, true);
            pedresGrup.add(actual);
            
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(actual);
                if (estat.isInBounds(adj) && estat.getColor(adj) == color && !visitats.containsKey(adj)) {
                    pila.push(adj);
                }
            }
        }
        
        int mida = pedresGrup.size();
        for (Point p : pedresGrup) {
            midaGrup.put(p, mida);
        }
        
        return mida;
    }
    
    /**
     * Classe auxiliar.
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
     * Entrada taula transposició.
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
