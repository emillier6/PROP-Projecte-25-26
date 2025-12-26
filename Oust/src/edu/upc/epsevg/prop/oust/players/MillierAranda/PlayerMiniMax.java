/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust.players.MillierAranda;


import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.GameStatusTunned;
import edu.upc.epsevg.prop.oust.MyStatus;
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
import java.util.Set;
import java.util.HashSet;

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
    
    protected int profunditatMaxima;  // CHANGED: final → protected per herència
    private final String name;
    
    protected long nodes;  // CHANGED: private → protected per herència
    
    private static final boolean DEBUG = false;
    private static final int TOP_K = 8;
    
    
    // --- Heurística (pesos inicials) ---
    private static final float W_ENEMY = 100.0f;  // reduir enemics (fort)
    private static final float W_MY    = 1.0f;    // penalitzar inflar-se (suau)
    private static final float W_CAP   = 400.0f;  // captures immediates del rival (molt fort)
    private static final float W_CHAIN = 200.0f;  // cadena de captures del rival (fort)
    private static final float W_MOB   = 2.0f;    // mobilitat (mitjà)
    
    
    /**
     * Representa un torn complet del joc, incloent totes les col·locacions
     * realitzades durant el torn i l'estat resultant del tauler.
     */
    private static class TurnMove {
        final List<Point> path;
        final GameStatus result;

        TurnMove(List<Point> path, GameStatus result) {
            // Guardem la seqüència de punts i l'estat final associat
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
        ScoredPoint(Point p, float score) {
            // Moviment candidat
            this.p = p;
            // Valor heurístic calculat després de simular aquest moviment
            this.score = score; 
        }
    }
    
    /**
     * Emmagatzema informació sobre amenaces immediates del rival
     * utilitzades per la funció heurística.
     */
    private static class ThreatInfo {
        int enemyMoves; // Mobilitat del rival (número de moviments possibles)
        int enemyCaptureMoves; // Moviments del rival que capturen immediatament
        int enemyMaxGreedyChain; // Longitud màxima estimada de cadena de captures del rival (greedy)
    }
    
    /**
     * Crea un jugador Minimax amb una profunditat màxima de cerca fixa.
     *
     * @param profunditatMaxima profunditat màxima de la cerca Minimax,
     *        mesurada en nombre de torns complets
     */
    public PlayerMiniMax(int profunditatMaxima) {
        // Guardem la profunditat màxima amb la que treballarà el minimax
        this.profunditatMaxima = profunditatMaxima;
        // Nom del jugador (inclou la profunditat per facilitar proves)
        this.name = "PlayerMiniMax(" + profunditatMaxima + ")";
    }

    @Override
    public String getName() {
        return "MiniMaxSimple(" + name + ")";
    }

    /**
     * Calcula i retorna una jugada vàlida per al jugador actual.
     *
     * <p>La jugada retornada pot contenir diverses col·locacions si es produeixen
     * captures encadenades. El mètode genera possibles torns complets, els avalua
     * mitjançant Minimax amb poda alpha-beta, i selecciona el millor segons
     * la funció heurística.</p>
     *
     * @param status estat actual del joc
     * @return un PlayerMove vàlid amb la seqüència de col·locacions del torn
     */
    @Override
    public PlayerMove move(GameStatus status) {
        // Reiniciem el comptador de nodes explorats per aquesta jugada
        nodes = 0;
        // Jugador que inicia la cerca (el que li toca moure ara)
        PlayerType me = status.getCurrentPlayer();

        // Generem els possibles torns (accions) a partir de l'estat actual
        List<TurnMove> actions = generateTurnMoves(status);
        
        // Variables per guardar la millor acció trobada
        TurnMove bestAction = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        // Paràmetres alpha-beta per al node arrel
        float alpha = Float.NEGATIVE_INFINITY;
        float beta  = Float.POSITIVE_INFINITY;

        // Avaluem cada torn candidat amb minimax i ens quedem amb el millor
        for (TurnMove a : actions) {
            // IMPORTANT: després d'aplicar el torn, reduïm la profunditat restant
            float sc = minimax(a.result, profunditatMaxima - 1, alpha, beta, me);

            // Actualitzem millor acció si trobem una millor puntuació
            if (sc > bestScore) {
                bestScore = sc;
                bestAction = a;
            }
            // Actualitzem alpha al node arrel (pot ajudar a podar més aviat)
            alpha = Math.max(alpha, bestScore); // root alpha update
        }

        // Si no hi ha cap acció (cas rar), retornem una llista buida per seguretat
        List<Point> path = (bestAction == null) ? new ArrayList<>() : bestAction.path;

        // Debug opcional per veure nodes, profunditat, etc.
        if (DEBUG) {
            System.out.println("[MiniMax] depth=" + profunditatMaxima +
                    " nodes=" + nodes +
                    " pathLen=" + path.size() +
                    " h=" + bestScore);
        }

        // Construïm el PlayerMove amb informació útil per a logs i memòria
        PlayerMove pm = new PlayerMove(path, nodes, profunditatMaxima, SearchType.MINIMAX);
        pm.setNumerOfNodesExplored(nodes);
        pm.setMaxDepthReached(profunditatMaxima);
        pm.setH(bestScore);
        return pm;
    }

    // -------------------------------------------------------------------------
    // MINIMAX + ALPHA-BETA (profunditat per torn)
    // -------------------------------------------------------------------------
    /**
     * Implementació de l'algorisme Minimax amb poda alpha-beta.
     *
     * <p>La profunditat es mesura en torns complets. Un node és maximitzador
     * quan és el torn del jugador que inicia la cerca.</p>
     *
     * @param s estat actual del joc
     * @param depth profunditat restant de la cerca
     * @param alpha valor alpha per a la poda
     * @param beta valor beta per a la poda
     * @param me jugador per al qual s'està realitzant la cerca
     * @return valor heurístic de l'estat del joc
     */
    private float minimax(GameStatus s, int depth, float alpha, float beta, PlayerType me) {
        // Comptem aquest node com a explorat (estadístiques)
        nodes++;

        // Cas base: o bé s'ha arribat a profunditat 0 o el joc s'ha acabat
        if (depth <= 0 || s.isGameOver()) {
            float h = evaluate(s, me);
            
            // --- KILLER INSTINCT MODIFICATION ---
            if (s.isGameOver()) {
                // Si la partida ha terminado, ya no hay heurística que valga.
                // Es VICTORIA ABSOLUTA o DERROTA ABSOLUTA.
                // Usamos INFINITY para que ningún cálculo de libertades pueda superar esto.
                
                // Si h es positivo, gané yo -> +Infinito
                // Si h es negativo, ganó el rival -> -Infinito
                return (h > 0) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            }
            // ------------------------------------
            
            return h;
        }

        // Determinem si aquest node és de maximització (torn de "me") o minimització
        boolean maximizing = (s.getCurrentPlayer() == me);

        // Generem tots els torns possibles des d'aquest estat
        List<TurnMove> actions = generateTurnMoves(s);
        // Si per algun motiu no hi ha accions, retornem una avaluació directa
        if (actions.isEmpty()) return evaluate(s, me);

        if (maximizing) {
            // Node MAX: volem maximitzar el valor
            float best = Float.NEGATIVE_INFINITY;
            for (TurnMove a : actions) {
                // Apliquem recursió amb profunditat - 1
                float val = minimax(a.result, depth - 1, alpha, beta, me);
                // Actualitzem millor valor del MAX
                best = Math.max(best, val);
                // Actualitzem alpha amb el millor valor trobat
                alpha = Math.max(alpha, best);
                // Poda alpha-beta: si beta <= alpha, no cal explorar més fills
                if (beta <= alpha) break;
            }
            return best;
        } else {
            // Node MIN: volem minimitzar el valor
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
    //  - branques TOP_K sobre la primera col·locació
    //  - completem la resta del torn greedy fins que el torn acaba
    // -------------------------------------------------------------------------
    /**
     * Genera tots els torns candidats a partir d'un estat del joc.
     *
     * <p>Només es ramifica sobre les primeres col·locacions del torn (estratègia Top-K).
     * La resta del torn es completa de manera greedy per respectar les regles
     * de captures encadenades sense incrementar excessivament el branching factor.</p>
     *
     * @param s estat actual del joc
     * @return llista de possibles torns complets
     */
    private List<TurnMove> generateTurnMoves(GameStatus s) {
        if (!(s instanceof GameStatusTunned)) {
            s = new GameStatusTunned(s);
        }
        
        PlayerType turnPlayer = s.getCurrentPlayer();
        List<Point> moves = s.getMoves(); // Aquest mètode ve de GameStatus (no modificable)
        List<TurnMove> out = new ArrayList<>();
        
        if (moves == null || moves.isEmpty()) return out;

        List<ScoredPoint> scored = new ArrayList<>(moves.size());
        
        for (Point p : moves) {
            // 1. FILTRE RÀPID ESTRUCTURAL:
            // Si és un singleton meu tocant un enemic, és un 99% probable suïcidi.
            // Ho penalitzem BRUTALMENT abans de fer cap simulació costosa.
            if (isSuicidePlacement(s, p, turnPlayer)) {
                // Li donem un valor baixíssim perquè el TOP_K el descarti
                scored.add(new ScoredPoint(p, -1000000.0f)); 
                continue; 
            }

            // Simulació normal per la resta
            GameStatus ns = new GameStatus(s);
            ns.placeStone(p);
            
            float currentEval = evaluate(ns, turnPlayer);
            
            // 2. FILTRE DE SIMULACIÓ (Double Check):
            // Si el filtre ràpid ha fallat, comprovem si realment ens capturen
            if (!ns.isGameOver() && canBeCapturedEasily(ns, p, turnPlayer)) {
                currentEval -= 100000.0f; // Penalització "infinita"
            }
            
            scored.add(new ScoredPoint(p, currentEval));
        }

        // Ordenem: Els moviments suïcides quedaran al final de tot
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        // TOP-K
        int limit = Math.min(TOP_K, scored.size());
        for (int i = 0; i < limit; i++) {
            // Si fins i tot el millor moviment té puntuació de suïcidi,
            // vol dir que no tenim opció, així que l'afegim igualment.
            // Però si té puntuació normal, els suïcides (al final de la llista) s'ignoraran.
            ScoredPoint sp = scored.get(i);
            
            // Optimització: Si tenim bons moviments (> -50000), no mirem els suïcides
            if (i > 0 && sp.score < -50000.0f && scored.get(0).score > -50000.0f) {
                break; 
            }

            Point first = sp.p;
            GameStatus cur = new GameStatus(s);
            List<Point> path = new ArrayList<>();

            path.add(first);
            cur.placeStone(first);

            // Completar Greedy (igual que tenies)
            while (!cur.isGameOver() && cur.getCurrentPlayer() == turnPlayer) {
                List<Point> cont = cur.getMoves();
                if (cont == null || cont.isEmpty()) break;

                Point best = null;
                float bestScore = Float.NEGATIVE_INFINITY;

                for (Point c : cont) {
                    GameStatus ns = new GameStatus(cur);
                    ns.placeStone(c);
                    float sc = evaluate(ns, turnPlayer); // Aquí usa l'evaluate normal
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
     * Detecta estructuralment si col·locar una pedra a P és un suïcidi evident.
     * Criteri: Si la pedra serà un Singleton (no toca amics) I toca algun enemic.
     * En Oust, això és mort segura el 99% de les vegades.
     */
    private boolean isSuicidePlacement(GameStatus s, Point p, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        
        // 1. Mirem si connecta amb alguna pedra meva (si ho fa, NO és singleton, pot ser segur)
        if (teVeiPropi(s, p.x, p.y, me)) {
            return false; // Connectem amb grup amic, per tant no és suïcidi immediat de singleton
        }

        // 2. Si és un singleton (no tinc amics al costat), mirem si tinc enemics
        if (teVeiEnemicHex(s, p.x, p.y, opp)) {
            return true; // Singleton vs Enemic = SUÏCIDI
        }
        
        return false; // Singleton aïllat (segur)
    }
           
    private boolean canBeCapturedEasily(GameStatus s, Point lastMove, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        // Miramos los movimientos legales del oponente
        List<Point> oppMoves = s.getMoves();
        if (oppMoves == null) return false;

        for (Point op : oppMoves) {
            GameStatus simulation = new GameStatus(s);
            simulation.placeStone(op);
            // Si después de su movimiento, nuestra piedra en 'lastMove' ya no está,
            // significa que nos ha capturado.
            if (simulation.getColor(lastMove.x, lastMove.y) != me) {
                return true; 
            }
        }
        return false;
    }

    /**
     * Cuenta cuántas fichas propias están en peligro inmediato.
     * Se considera peligro inmediato si es un singleton (aislada) 
     * y tiene al menos un vecino enemigo.
     */
    private int countImmediateThreats(GameStatus s, PlayerType me) {
        int threats = 0;
        int size = s.getSize();
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Si la casilla es mía
                if (s.getColor(x, y) == me) {
                    // Si NO tengo vecinos propios (soy un singleton)
                    // Y SI tengo vecinos enemigos
                    if (!teVeiPropi(s, x, y, me) && teVeiEnemicHex(s, x, y, opp)) {
                        threats++;
                    }
                }
            }
        }
        return threats;
    }

    /**
     * El framework crida timeout() si s'acaba el temps.
     * En minimax “sense temps” ho deixem buit.
     * (A IDS ho fareu servir segur.)
     */
    @Override
    public void timeout() {
        // No fem res de moment
    }
    
    // -------------------------------------------------------------------------
    // HEURÍSTICA DEFENSIVA
    // -------------------------------------------------------------------------
    /**
     * Funció heurística defensiva.
     *
     * <p>Avalua un estat del joc combinant diferents factors:</p>
     * <ul>
     *   <li>Material: penalitza fortament el nombre de peces rivals.</li>
     *   <li>Material propi: penalització suau per evitar inflar-se excessivament.</li>
     *   <li>Mobilitat: premia tenir més moviments disponibles.</li>
     *   <li>Amenaça rival: penalitza moviments rivals que permeten captures immediates.</li>
     * </ul>
     *
     * <p>Un valor més alt indica un estat més favorable per al jugador {@code me}.</p>
     *
     * @param s estat actual del joc
     * @param me jugador per al qual s'avalua l'estat
     * @return valor heurístic de l'estat
     */    
    private float evaluate(GameStatus s, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        
        GameStatusTunned gst = (s instanceof GameStatusTunned) ? 
                               (GameStatusTunned)s : new GameStatusTunned(s);
        MyStatus info = gst.getInfo();

        // Dades bàsiques
        int myBiggest = (me == PlayerType.PLAYER1) ? info.biggestGroupP1 : info.biggestGroupP2;
        int enemyBiggest = (me == PlayerType.PLAYER1) ? info.biggestGroupP2 : info.biggestGroupP1;
        int my = (me == PlayerType.PLAYER1) ? info.stonesP1 : info.stonesP2;
        int enemy = (me == PlayerType.PLAYER1) ? info.stonesP2 : info.stonesP1;

        // --- 0. DETECCIÓ DE FASE DE JOC ---
        // Calculem quants hexàgons té el tauler aproximadament (per saber si estem al final)
        // La formula exacta d'hexàgons és 3*n*(n-1) + 1, però amb n*n*3 ens val per estimar.
        int totalCellsEstimate = s.getSize() * s.getSize() * 3 / 4; 
        int totalStones = my + enemy;
        
        // Estem al Endgame si hi ha moltes peces (> 60% ocupació)
        boolean isEndGame = totalStones > (totalCellsEstimate * 0.60);

        // --- 1. VICTORIA / DERROTA TOTAL ---
        if (enemy == 0) return 100000.0f;
        if (my == 0) return -100000.0f;

        // --- 2. DEFINICIÓ DE PESOS DINÀMICS ---
        float W_BIGGEST, W_SINGLE, W_LIBERTIES, W_VULNERABLE, W_MATERIAL;

        if (isEndGame) {
            // ESTRATÈGIA ENDGAME (Supervivència i Ofec)
            W_BIGGEST = 80.0f;      // Menys important fer grups grans
            W_SINGLE = 10.0f;       // Els singletons ja no són tan útils, fan nosa
            W_LIBERTIES = 40.0f;    // CRÍTIC: Necessitem espai per no ofegar-nos
            W_VULNERABLE = -100.0f; // PARANOIA: Si tens un punt feble, et mataran segur
            W_MATERIAL = 20.0f;     // El material importa per desempatar
        } else {
            // ESTRATÈGIA EARLY/MID (Expansió i Estructura)
            W_BIGGEST = 100.0f;
            W_SINGLE = 25.0f;       // Molt bo tenir llavors plantades
            W_LIBERTIES = 5.0f;     // No és prioritari encara
            W_VULNERABLE = -15.0f;  // Risc acceptable
            W_MATERIAL = 10.0f;
        }

        // --- 3. CÀLCULS AUXILIARS ---
        float captureBonus = info.lastMoveWasCapture ? 50.0f : 0.0f;
        int mySingletons = comptarSingletons(s, me);
        int enemySingletons = comptarSingletons(s, opp);
        int myLiberties = comptarLibertats(s, me);
        int enemyLiberties = comptarLibertats(s, opp);
        int myVulnerable = comptarGrupsVulnerables(s, me, opp);

        // --- 4. FORMULA FINAL ---
        float score = W_BIGGEST * (myBiggest - enemyBiggest) 
            + captureBonus                        
            + W_SINGLE * (mySingletons - enemySingletons * 0.5f) 
            + W_LIBERTIES * (myLiberties - enemyLiberties)  
            + W_VULNERABLE * myVulnerable               
            - 50.0f * enemy       // Sempre volem eliminar enemics               
            + W_MATERIAL * my;

        // Penalització per amenaça immediata (es manté igual de forta)
        if (!info.lastMoveWasCapture) {
            score -= 2000.0f * countImmediateThreats(s, me); 
        }
        
        return score;
    }
    
    /**
     * Compta les caselles buides úniques adjacents als meus grups.
     * Al final de la partida, qui té més espai únic guanya.
     */
    private int comptarLibertats(GameStatus s, PlayerType player) {
        Set<Point> uniqueLiberties = new HashSet<>();
        int size = s.getSize();
        int[][] dirs = {{1,0}, {-1,0}, {0,-1}, {1,-1}, {-1,1}, {0,1}};
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (s.getColor(x, y) == player) {
                    for (int[] d : dirs) {
                        int nx = x + d[0];
                        int ny = y + d[1];
                        // Verifiquem límits manualment per rapidesa o usem isInBounds
                        if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                            if (s.getColor(nx, ny) == null) {
                                uniqueLiberties.add(new Point(nx, ny));
                            }
                        }
                    }
                }
            }
        }
        return uniqueLiberties.size();
    }
    
    /**
     * Detecta grups vulnerables: grups grans tocant singletons enemics.
     * Risc: Un grup gran pot ser capturat per un singleton enemic que creix.
     */
    private int comptarGrupsVulnerables(GameStatus s, PlayerType me, PlayerType opp) {
        // Simplificat: comptar peces pròpies amb veïns enemics aïllats
        int vulnerable = 0;
        int size = s.getSize();
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (s.getColor(x, y) == me) {
                    // Si té veí enemic que és singleton, és vulnerable
                    if (teVeiEnemicSingleton(s, x, y, opp)) {
                        vulnerable++;
                    }
                }
            }
        }
        return vulnerable;
    }
    
    /**
     * Comprova si té veí enemic que és singleton (amenaça).
     */
    private boolean teVeiEnemicSingleton(GameStatus s, int x, int y, PlayerType enemy) {
        int size = s.getSize();
        int[][] dirs = {{1,0}, {-1,0}, {0,-1}, {1,-1}, {-1,1}, {0,1}};
        
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                if (s.getColor(nx, ny) == enemy) {
                    // Comprovar si aquest enemic és singleton
                    if (!teVeiPropi(s, nx, ny, enemy)) {
                        return true;  // Té veí enemic aïllat (amenaça!)
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Compta singletons: peces sense veïns del mateix color.
     * En Oust, els singletons són ESTRATÈGICS (flexibilitat).
     */
    private int comptarSingletons(GameStatus s, PlayerType player) {
        int count = 0;
        int size = s.getSize();
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (s.getColor(x, y) == player) {
                    // Comprova si té algun veí del mateix color
                    if (!teVeiPropi(s, x, y, player)) {
                        count++;  // És singleton!
                    }
                }
            }
        }
        return count;
    }
    
    /**
     * Comprova si una posició té algun veí del mateix color.
     */
    private boolean teVeiPropi(GameStatus s, int x, int y, PlayerType player) {
        int size = s.getSize();
        int[][] dirs = {{1,0}, {-1,0}, {0,-1}, {1,-1}, {-1,1}, {0,1}};
        
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                if (s.getColor(nx, ny) == player) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Comprova si una posició té algun veí enemig (graella hexagonal).
     */
    private boolean teVeiEnemicHex(GameStatus s, int x, int y, PlayerType enemy) {
        int size = s.getSize();
        // 6 direccions hexagonals: E, W, NE, NW, SE, SW
        int[][] dirs = {{1,0}, {-1,0}, {0,-1}, {1,-1}, {-1,1}, {0,1}};
        
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                if (s.getColor(nx, ny) == enemy) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Calcula la possible amenaça immediata del rival en un estat del joc.
     *
     * <p>Aquest mètode analitza els moviments disponibles del rival i detecta
     * quins d'aquests permeten realitzar una captura immediata, és a dir,
     * col·locacions després de les quals el jugador rival pot continuar
     * tirant en el mateix torn.</p>
     *
     * <p>Per limitacions del framework, l'amenaça només es calcula quan
     * realment és el torn del rival. Si no és així, el mètode retorna una
     * amenaça nul·la.</p>
     *
     * @param s estat actual del joc
     * @param me jugador per al qual s'està avaluant l'amenaça
     * @return informació sobre les amenaces immediates del rival
     */
    private ThreatInfo computeOpponentThreatIfOpponentTurn(GameStatus s, PlayerType me) {
        // OPTIMITZAT: Desactivat càlcul d'amenaces per millorar rendiment
        // De 56 nodes → 10,000+ nodes per segon
        return new ThreatInfo(); // Retorna tots els valors a 0
    }
    
    /**
     * Estima la longitud d'una possible cadena de captures consecutives
     * que un jugador pot realitzar a partir d'un estat del joc.
     *
     * <p>La cadena s'estima de manera aproximada mitjançant una estratègia greedy:
     * a cada pas es selecciona la col·locació que maximitza una heurística
     * ràpida basada en el material, sense explorar totes les possibilitats.</p>
     *
     * <p>Aquest mètode no garanteix trobar la cadena òptima, però proporciona
     * una estimació eficient del potencial ofensiu immediat d'un jugador.</p>
     *
     * @param s estat inicial del joc
     * @param player jugador del qual s'estima la cadena de captures
     * @return longitud estimada de la cadena de captures consecutives
     */
    private int greedyChainLength(GameStatus s, PlayerType player) {
        int len = 0; // Comptador de col·locacions consecutives (captures)

        // Mentre el joc no hagi acabat i el mateix jugador continuï tenint el torn,
        // vol dir que les captures obliguen a seguir col·locant fitxes
        while (!s.isGameOver() && s.getCurrentPlayer() == player) {
            // Obtenim tots els moviments possibles en aquest punt
            List<Point> moves = s.getMoves();
            // Si no hi ha moviments, la cadena s'atura
            if (moves == null || moves.isEmpty()) break;

            Point best = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            // Seleccionem de manera greedy el moviment que maximitza
            // una heurística ràpida basada en el material
            for (Point p : moves) {
                // Simulem el moviment
                GameStatus ns = new GameStatus(s);
                ns.placeStone(p);
                
                // Avaluem ràpidament l'estat resultant
                float sc = quickMaterial(ns, player);
                
                // Ens quedem amb el moviment que dona millor resultat
                if (sc > bestScore) {
                    bestScore = sc;
                    best = p;
                }
            }
            // Apliquem el millor moviment trobat
            s.placeStone(best);
            // Incrementem la longitud estimada de la cadena
            len++;
        }

        return len;
    }
    
    /**
     * Funció heurística ràpida basada únicament en el material del tauler.
     *
     * <p>Aquesta heurística penalitza fortament el nombre de peces rivals i
     * lleugerament el nombre de peces pròpies. S'utilitza exclusivament com
     * a criteri local per seleccionar continuacions en cadenes de captures,
     * on el cost computacional ha de ser mínim.</p>
     *
     * <p>No està pensada per avaluar estats globals del joc, sinó com a
     * aproximació eficient per a decisions locals.</p>
     *
     * @param s estat actual del joc
     * @param me jugador per al qual s'avalua el material
     * @return valor heurístic ràpid basat en el material
     */
    private float quickMaterial(GameStatus s, PlayerType me) {
        // Determinem el jugador rival
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        int my = 0, enemy = 0; // Nombre de peces pròpies i peces rel rival
        int size = s.getSize();
        
        // Recorrem tot el tauler per comptar les peces
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                PlayerType c = s.getColor(x, y);
                if (c == me) my++;
                else if (c == opp) enemy++;
            }
        }
        // Retornem una heurística simple i molt barata:
        // - penalitza fortament les peces enemigues
        // - penalitza lleugerament les peces pròpies
        //
        // Aquesta funció NO s'utilitza per decisions globals,
        // sinó com a criteri local dins de cadenes de captures.
        return (-100.0f * enemy) - my;
    }
    
}
