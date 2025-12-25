package edu.upc.epsevg.prop.oust;

import java.awt.Point;
import java.util.*;

public class GameStatusTunned extends GameStatus {

    private MyStatus info;

    public GameStatusTunned(GameStatus gs) {
        super(gs);
        info = new MyStatus();
        recomputeAll();
    }

    public MyStatus getInfo() {
        return info;
    }

    @Override
    public void placeStone(Point point) {
        int before1 = info.stonesP1;
        int before2 = info.stonesP2;

        super.placeStone(point);

        recomputeAll();

        // detectar captura
        if (info.stonesP1 < before1 || info.stonesP2 < before2) {
            info.lastMoveWasCapture = true;
        } else {
            info.lastMoveWasCapture = false;
        }
    }

    // =============================
    // Recalcular toda la heurística
    // =============================

    private void recomputeAll() {
        info.stonesP1 = countStones(PlayerType.PLAYER1);
        info.stonesP2 = countStones(PlayerType.PLAYER2);

        info.biggestGroupP1 = biggestGroup(PlayerType.PLAYER1);
        info.biggestGroupP2 = biggestGroup(PlayerType.PLAYER2);
    }

    // =============================
    // Funciones auxiliares
    // =============================

    private int countStones(PlayerType p) {
        int count = 0;
        int size = getSquareSize();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (isInBounds(new Point(x, y)) && getColor(x, y) == p) {
                    count++;
                }
            }
        }
        return count;
    }

    private int biggestGroup(PlayerType p) {
        int size = getSquareSize();
        boolean[][] visited = new boolean[size][size];
        int best = 0;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {

                Point start = new Point(x, y);
                if (!isInBounds(start)) continue;
                if (visited[x][y]) continue;
                if (getColor(x, y) != p) continue;

                int group = flood(start, p, visited);
                best = Math.max(best, group);
            }
        }
        return best;
    }

    private int flood(Point start, PlayerType p, boolean[][] visited) {
        Stack<Point> st = new Stack<>();
        st.push(start);
        visited[start.x][start.y] = true;

        int total = 0;

        while (!st.isEmpty()) {
            Point u = st.pop();
            total++;

            for (Dir d : Dir.values()) {
                Point v = d.add(u);
                if (!isInBounds(v)) continue;
                if (visited[v.x][v.y]) continue;
                if (getColor(v.x, v.y) != p) continue;

                visited[v.x][v.y] = true;
                st.push(v);
            }
        }
        return total;
    }
}