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
import java.util.List;
import java.util.Random;

/**
 *
 * @author emillier
 */
public class PlayerMiniMax implements IPlayer, IAuto {
    
    private final int profunditatMaxima;
    private final String name;

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
        List<Point> path = new ArrayList<>();

        PlayerType me = status.getCurrentPlayer();

        // Construïm la seqüència completa del torn (captures encadenades incloses)
        while (!status.isGameOver() && status.getCurrentPlayer() == me) {

            List<Point> moves = status.getMoves();

            // Si no hi ha moviments, s'ha de passar.
            // (En principi el framework ho gestiona, però retornem path buit.)
            if (moves == null || moves.isEmpty()) {
                break;
            }

            Point best = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            for (Point cand : moves) {
                GameStatus next = new GameStatus(status); // còpia
                next.placeStone(cand);                    // simula

                float sc = evaluate(next, me);

                if (sc > bestScore) {
                    bestScore = sc;
                    best = cand;
                }
            }

            Point chosen = best;
            System.out.println("[MiniMax] trio " + chosen + " score=" + bestScore);
            path.add(chosen);
            status.placeStone(chosen); // IMPORTANT: modifica l'estat simulat
        }

        if (path == null) path = new ArrayList<>();
        // Constructor REAL de PlayerMove segons la vostra llibreria:
        // PlayerMove(List<Point>, long nodes, int depth, SearchType)
        PlayerMove pm = new PlayerMove(path, 0L, 0, SearchType.MINIMAX);
        pm.setNumerOfNodesExplored(0L);
        pm.setMaxDepthReached(0);
        pm.setH(0.0f); // valor heurístic (ja el fareu servir més endavant)

        return pm;
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
    
    private float evaluate(GameStatus s, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        int my = 0, enemy = 0;
        int size = s.getSize();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                PlayerType c = s.getColor(x, y);   // <-- IMPORTANT: existeix al GameStatus del .jar
                if (c == me) my++;
                else if (c == opp) enemy++;
            }
        }
        return my - enemy;
    }
    
}
