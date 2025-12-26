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
            // Avaluem l'estat amb la heurística
            float h = evaluate(s, me);
            // Si el joc ha acabat, exagerem el valor per prioritzar finals clars
            // (guanyar/perdre ha de dominar sobre heurístiques intermèdies)
            return s.isGameOver() ? h * 1000.0f : h;
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
        // El jugador al qual li toca moure en aquest estat
        PlayerType turnPlayer = s.getCurrentPlayer();
        // Moviments legals disponibles en aquest estat
        List<Point> moves = s.getMoves();

        List<TurnMove> out = new ArrayList<>();
        // Si no hi ha moviments, no podem generar cap torn
        if (moves == null || moves.isEmpty()) return out;

        // --- PAS 1: puntuem i ordenem moviments ---
        // Només ramifiquem sobre la primera col·locació del torn.
        // Això evita generar totes les seqüències possibles de captures,
        // que seria exponencial i massa lent.
        List<ScoredPoint> scored = new ArrayList<>(moves.size());
        for (Point p : moves) {
            // Simulem el moviment sobre una còpia
            GameStatus ns = new GameStatus(s);
            ns.placeStone(p);
            // Assignem score heurístic a aquest primer moviment
            scored.add(new ScoredPoint(p, evaluate(ns, turnPlayer)));
        }
        // Ordenem per score descendent (millors candidats primer)
        scored.sort((a, b) -> Float.compare(b.score, a.score));

         // --- PAS 2: top-k sobre la primera col·locació ---
        int limit = Math.min(TOP_K, scored.size());
        for (int i = 0; i < limit; i++) {
            Point first = scored.get(i).p;

            // Creem una còpia per construir el torn complet
            GameStatus cur = new GameStatus(s);
            // Guardem la seqüència de col·locacions del torn
            List<Point> path = new ArrayList<>();

            // Apliquem la primera col·locació (branca aquí)
            path.add(first);
            cur.placeStone(first);

            // --- PAS 3: completar el torn greedy (captures encadenades) ---
            // En Oust, si captures, continues tirant.
            // Aquí triem la millor continuació local (greedy) per acabar el torn
            // sense explotar totes les variants.
            while (!cur.isGameOver() && cur.getCurrentPlayer() == turnPlayer) {
                List<Point> cont = cur.getMoves();
                if (cont == null || cont.isEmpty()) break;

                Point best = null;
                float bestScore = Float.NEGATIVE_INFINITY;

                // Triem la continuació que maximitza l'heurística
                for (Point c : cont) {
                    GameStatus ns = new GameStatus(cur);
                    ns.placeStone(c);
                    float sc = evaluate(ns, turnPlayer);
                    if (sc > bestScore) {
                        bestScore = sc;
                        best = c;
                    }
                }
                // Apliquem la millor continuació trobada
                path.add(best);
                cur.placeStone(best);
            }
            // Afegim el torn complet generat i l'estat resultant
            out.add(new TurnMove(path, cur));
        }

        return out;
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
        // Identifiquem el jugador rival
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // --- 1) MATERIAL: comptar peces pròpies i rivals ---
        int my = 0, enemy = 0;
        int size = s.getSize();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                PlayerType c = s.getColor(x, y);
                if (c == me) my++;
                else if (c == opp) enemy++;
            }
        }

        // --- 2) MOBILITAT: nombre de moviments disponibles (quan és el teu torn) ---
        int myMoves = 0;
        if (s.getCurrentPlayer() == me) {
            List<Point> mvs = s.getMoves();
            myMoves = (mvs == null) ? 0 : mvs.size();
        }

        // --- 3) AMENAÇA DEL RIVAL: captures immediates i cadena de captures (aprox) ---
        // Només és exacta quan és torn del rival; en cas contrari retorna valors neutres.
        ThreatInfo th = computeOpponentThreatIfOpponentTurn(s, me);

        // --- 4) COMBINACIÓ DE FACTORS ---
        // Score més alt = millor per "me".
        //  - Penalitzem fortament tenir moltes peces enemigues (objectiu: eliminar rival).
        //  - Penalitzem captures immediates del rival (defensa contra agressius).
        //  - Penalitzem lleugerament moltes peces pròpies (evitar inflar-se sense necessitat).
        //  - Premiem mobilitat i penalitzem la mobilitat del rival.
        return (-W_ENEMY * enemy)
                - (W_MY * my)
                - (W_CAP * th.enemyCaptureMoves)
                - (W_CHAIN * th.enemyMaxGreedyChain)
                + (W_MOB * (myMoves - th.enemyMoves));
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
        // Objecte on acumularem la informació sobre l'amenaça del rival
        ThreatInfo ti = new ThreatInfo();
        
        // Si el joc ja ha acabat, no hi ha cap amenaça possible
        if (s.isGameOver()) return ti;

        // Determinem quin és el jugador rival
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // Només calculem l'amenaça si realment és el torn del rival.
        // El codi no permet consultar moviments d'un jugador arbitrari
        // sense modificar l'estat del joc.
        if (s.getCurrentPlayer() != opp) return ti;

        // Obtenim tots els moviments possibles del rival
        List<Point> moves = s.getMoves();
        // Guardem el nombre total de moviments disponibles del rival (mobilitat)
        ti.enemyMoves = (moves == null) ? 0 : moves.size();
        // Si el rival no té moviments, no pot capturar
        if (moves == null || moves.isEmpty()) return ti;

        int capCount = 0; // Comptador de moviments que permeten captura
        int bestChain = 0; // Millor longitud estimada de cadena de captures

        // Analitzem cada possible moviment del rival
        for (Point p : moves) {
            // Simulem el moviment sobre una còpia de l'estat del joc
            GameStatus ns = new GameStatus(s);
            // Guardem quin jugador té el torn abans de col·locar la fitxa
            PlayerType before = ns.getCurrentPlayer();
            ns.placeStone(p);

            // En Oust, si després de col·locar la fitxa el jugador NO canvia,
            // vol dir que s'ha produït una captura
            boolean captured = (!ns.isGameOver() && ns.getCurrentPlayer() == before);
            if (captured) {
                capCount++;

                // Estimem de manera greedy quantes captures consecutives podria fer
                // el rival a partir d'aquest moviment
                int chainLen = 1 + greedyChainLength(ns, before);
                // Ens quedem amb la cadena més llarga trobada
                if (chainLen > bestChain) bestChain = chainLen;
            }
        }
        
        // Guardem la informació calculada
        ti.enemyCaptureMoves = capCount;
        ti.enemyMaxGreedyChain = bestChain;
        return ti;
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
