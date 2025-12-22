/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust.players.MillierAranda;


import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.PlayerType;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 *
 * @author emillier
 */
public class PlayerMiniMax implements IPlayer, IAuto {
    
    private final int profunditatMaxima;
    private final String name;
    
    private long nodes;
    
    private static final boolean DEBUG = false;
    private static final int TOP_K = 8;
    
    
    // --- Heurística (pesos inicials) ---
    private static final float W_ENEMY = 100.0f;  // reduir enemics (fort)
    private static final float W_MY    = 1.0f;    // penalitzar inflar-se (suau)
    private static final float W_CAP   = 400.0f;  // captures immediates del rival (molt fort)
    private static final float W_CHAIN = 200.0f;  // cadena de captures del rival (fort)
    private static final float W_MOB   = 2.0f;    // mobilitat (mitjà)
    
    
    /**
     * Representa un "moviment de torn" complet (path de col·locacions) i l'estat resultant.
     */
    private static class TurnMove {
        final List<Point> path;
        final GameStatus result;

        TurnMove(List<Point> path, GameStatus result) {
            this.path = path;
            this.result = result;
        }
    }
    
    /**
     * Punt amb score precalculat per ordenar barat i fer top-k.
     */
    private static class ScoredPoint {
        final Point p;
        final float score;
        ScoredPoint(Point p, float score) { this.p = p; this.score = score; }
    }
    
    /**
     * Informació d'amenaça del rival.
     */
    private static class ThreatInfo {
        int enemyMoves;
        int enemyCaptureMoves;
        int enemyMaxGreedyChain;
    }
    
    /**
     * Constructor obligatori segons les normes de lliurament.
     * @param profunditatMaxima profunditat màxima d'exploració (quan implementeu minimax)
     */
    public PlayerMiniMax(int profunditatMaxima) {
        this.profunditatMaxima = profunditatMaxima;
        this.name = "PlayerMiniMax(" + profunditatMaxima + ")";
    }

    @Override
    public String getName() {
        return "MiniMaxSimple(" + name + ")";
    }

    /**
     * Retorna una seqüència de punts (tirada) vàlida.
     * Important: si hi ha captures, el joc obliga a continuar tirant
     * fins fer una non-capturing placement (quan canvia el jugador).
     */
    @Override
    public PlayerMove move(GameStatus status) {
        
        nodes = 0;
        PlayerType me = status.getCurrentPlayer();

        List<TurnMove> actions = generateTurnMoves(status);
        TurnMove bestAction = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        float alpha = Float.NEGATIVE_INFINITY;
        float beta  = Float.POSITIVE_INFINITY;

        for (TurnMove a : actions) {
            float sc = minimax(a.result, profunditatMaxima - 1, alpha, beta, me);

            if (sc > bestScore) {
                bestScore = sc;
                bestAction = a;
            }
            alpha = Math.max(alpha, bestScore); // root alpha update
        }

        List<Point> path = (bestAction == null) ? new ArrayList<>() : bestAction.path;

        if (DEBUG) {
            System.out.println("[MiniMax] depth=" + profunditatMaxima +
                    " nodes=" + nodes +
                    " pathLen=" + path.size() +
                    " h=" + bestScore);
        }

        PlayerMove pm = new PlayerMove(path, nodes, profunditatMaxima, SearchType.MINIMAX);
        pm.setNumerOfNodesExplored(nodes);
        pm.setMaxDepthReached(profunditatMaxima);
        pm.setH(bestScore);
        return pm;
    }

    // -------------------------------------------------------------------------
    // MINIMAX + ALPHA-BETA (profunditat per torn)
    // -------------------------------------------------------------------------

    private float minimax(GameStatus s, int depth, float alpha, float beta, PlayerType me) {
        nodes++;

        if (depth <= 0 || s.isGameOver()) {
            float h = evaluate(s, me);
            return s.isGameOver() ? h * 1000.0f : h;
        }

        boolean maximizing = (s.getCurrentPlayer() == me);

        List<TurnMove> actions = generateTurnMoves(s);
        if (actions.isEmpty()) return evaluate(s, me);

        if (maximizing) {
            float best = Float.NEGATIVE_INFINITY;
            for (TurnMove a : actions) {
                float val = minimax(a.result, depth - 1, alpha, beta, me);
                best = Math.max(best, val);
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            float best = Float.POSITIVE_INFINITY;
            for (TurnMove a : actions) {
                float val = minimax(a.result, depth - 1, alpha, beta, me);
                best = Math.min(best, val);
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }
    
    // -------------------------------------------------------------------------
    // GENERACIÓ D'ACCIONS (torn complet) — híbrid:
    //  - branquem TOP_K sobre la primera col·locació
    //  - completem la resta del torn greedy fins que el torn acaba
    // -------------------------------------------------------------------------

    private List<TurnMove> generateTurnMoves(GameStatus s) {
        PlayerType turnPlayer = s.getCurrentPlayer();
        List<Point> moves = s.getMoves();

        List<TurnMove> out = new ArrayList<>();
        if (moves == null || moves.isEmpty()) return out;

        // 1) Puntuar i ordenar moviments (1 simulació per moviment)
        List<ScoredPoint> scored = new ArrayList<>(moves.size());
        for (Point p : moves) {
            GameStatus ns = new GameStatus(s);
            ns.placeStone(p);
            scored.add(new ScoredPoint(p, evaluate(ns, turnPlayer)));
        }
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        // 2) Top-K només per la primera col·locació
        int limit = Math.min(TOP_K, scored.size());
        for (int i = 0; i < limit; i++) {
            Point first = scored.get(i).p;

            GameStatus cur = new GameStatus(s);
            List<Point> path = new ArrayList<>();

            path.add(first);
            cur.placeStone(first);

            // 3) Completar la resta del torn greedy
            while (!cur.isGameOver() && cur.getCurrentPlayer() == turnPlayer) {
                List<Point> cont = cur.getMoves();
                if (cont == null || cont.isEmpty()) break;

                Point best = null;
                float bestScore = Float.NEGATIVE_INFINITY;

                for (Point c : cont) {
                    GameStatus ns = new GameStatus(cur);
                    ns.placeStone(c);
                    float sc = evaluate(ns, turnPlayer);
                    if (sc > bestScore) {
                        bestScore = sc;
                        best = c;
                    }
                }

                path.add(best);
                cur.placeStone(best);
            }

            out.add(new TurnMove(path, cur));
        }

        return out;
    }
           
    
    /**
     * El framework crida timeout() si s'acaba el temps.
     * En minimax “sense temps” podeu deixar-ho buit.
     * (A IDS ho fareu servir segur.)
     */
    @Override
    public void timeout() {
        // No fem res de moment
    }
    
    // -------------------------------------------------------------------------
    // HEURÍSTICA DEFENSIVA
    // -------------------------------------------------------------------------

    private float evaluate(GameStatus s, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // Material
        int my = 0, enemy = 0;
        int size = s.getSize();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                PlayerType c = s.getColor(x, y);
                if (c == me) my++;
                else if (c == opp) enemy++;
            }
        }

        // Mobilitat (només exacta quan és el teu torn)
        int myMoves = 0;
        if (s.getCurrentPlayer() == me) {
            List<Point> mvs = s.getMoves();
            myMoves = (mvs == null) ? 0 : mvs.size();
        }

        // Amenaça rival (exacta quan és torn del rival)
        ThreatInfo th = computeOpponentThreatIfOpponentTurn(s, me);

        // Score: més alt = millor per "me"
        return (-W_ENEMY * enemy)
                - (W_MY * my)
                - (W_CAP * th.enemyCaptureMoves)
                - (W_CHAIN * th.enemyMaxGreedyChain)
                + (W_MOB * (myMoves - th.enemyMoves));
    }
    
    /**
     * Calcula amenaça del rival només quan REALMENT és el torn del rival.
     * (El framework no permet obtenir moviments d'un jugador arbitrari sense canviar torn.)
     */
    private ThreatInfo computeOpponentThreatIfOpponentTurn(GameStatus s, PlayerType me) {
        ThreatInfo ti = new ThreatInfo();

        if (s.isGameOver()) return ti;

        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // Si NO és torn del rival, retorn neutral
        if (s.getCurrentPlayer() != opp) return ti;

        List<Point> moves = s.getMoves();
        ti.enemyMoves = (moves == null) ? 0 : moves.size();
        if (moves == null || moves.isEmpty()) return ti;

        int capCount = 0;
        int bestChain = 0;

        for (Point p : moves) {
            GameStatus ns = new GameStatus(s);
            PlayerType before = ns.getCurrentPlayer();
            ns.placeStone(p);

            // Captura si el jugador NO canvia (i el joc no ha acabat)
            boolean captured = (!ns.isGameOver() && ns.getCurrentPlayer() == before);
            if (captured) {
                capCount++;

                // Estimem longitud de cadena amb greedy (aprox)
                int chainLen = 1 + greedyChainLength(ns, before);
                if (chainLen > bestChain) bestChain = chainLen;
            }
        }

        ti.enemyCaptureMoves = capCount;
        ti.enemyMaxGreedyChain = bestChain;
        return ti;
    }
    
    /**
     * Estima quantes col·locacions seguides pot fer el mateix jugador (cadena de captures),
     * triant sempre la millor continuació segons una heurística ràpida de material.
     */
    private int greedyChainLength(GameStatus s, PlayerType player) {
        int len = 0;

        while (!s.isGameOver() && s.getCurrentPlayer() == player) {
            List<Point> moves = s.getMoves();
            if (moves == null || moves.isEmpty()) break;

            Point best = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            for (Point p : moves) {
                GameStatus ns = new GameStatus(s);
                ns.placeStone(p);
                float sc = quickMaterial(ns, player);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = p;
                }
            }

            s.placeStone(best);
            len++;
        }

        return len;
    }
    
    /**
     * Heurística ràpida per seleccionar continuacions en cadenes:
     * prioritzar reduir enemics.
     */
    private float quickMaterial(GameStatus s, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        int my = 0, enemy = 0;
        int size = s.getSize();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                PlayerType c = s.getColor(x, y);
                if (c == me) my++;
                else if (c == opp) enemy++;
            }
        }
        return (-100.0f * enemy) - my;
    }
    
}
