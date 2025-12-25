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
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * IDSPlayerMiniMax implementa un jugador amb cerca iterativa en profunditat (IDS)
 * utilitzant l'algorisme Minimax amb poda alpha-beta i taula de transposicions.
 * 
 * <p>Aquesta implementació millora el PlayerMiniMax bàsic afegint:</p>
 * <ul>
 *   <li>Cerca iterativa que s'aprofundeix progressivament fins que s'acaba el temps</li>
 *   <li>Taula de transposicions per evitar recalcular posicions ja visitades</li>
 *   <li>Ordenació de moviments per millorar l'eficiència de la poda alpha-beta</li>
 *   <li>Control de temps per adaptar-se al límit de temps del joc</li>
 * </ul>
 * 
 * @author Alex Aranda
 */
public class IDSPlayerMiniMax implements IPlayer, IAuto {
    
    private final String nom;
    private final long limitTemps; // Temps màxim en mil·lisegons
    
    private PlayerType jugadorActual;
    private long tempsInici;
    private long nodesExplorats;
    private int profunditatAssolida;
    private boolean tempsEsgotat;
    
    // Taula de transposicions per emmagatzemar estats ja avaluats
    private final HashMap<Long, EntradaTaulaTransposicio> taulaTransposicions;
    private static final int MIDA_TAULA = 150_000;
    
    private static final boolean DEBUG = false;
    private static final int TOP_K = 8;
    
    // Random per trencar empats
    private final Random random = new Random();
    
    // --- Heurística (pesos ajustats per ser més agressiu) ---
    private static final float W_ENEMY = 150.0f;  // reduir enemics (MOLT fort - més agressiu)
    private static final float W_MY    = 0.5f;    // penalitzar inflar-se (molt suau)
    private static final float W_CAP   = 500.0f;  // captures immediates del rival (CRÍTIC)
    private static final float W_CHAIN = 150.0f;  // cadena de captures del rival (menys defensiu)
    private static final float W_MOB   = 3.0f;    // mobilitat (més important)
    
    /**
     * Crea un jugador IDS Minimax amb un límit de temps especificat.
     * 
     * @param tempsMaximMillis temps màxim per moviment en mil·lisegons
     */
    public IDSPlayerMiniMax(long tempsMaximMillis) {
        this.limitTemps = tempsMaximMillis;
        this.nom = "IDSMiniMax(" + tempsMaximMillis + "ms)";
        this.taulaTransposicions = new HashMap<>(MIDA_TAULA);
    }
    
    @Override
    public String getName() {
        return nom;
    }
    
    /**
     * Calcula el millor moviment utilitzant cerca iterativa en profunditat.
     * 
     * @param status estat actual del joc
     * @return el millor moviment trobat dins del límit de temps
     */
    @Override
    public PlayerMove move(GameStatus status) {
        // Inicialització
        tempsInici = System.currentTimeMillis();
        jugadorActual = status.getCurrentPlayer();
        nodesExplorats = 0;
        profunditatAssolida = 0;
        tempsEsgotat = false;
        taulaTransposicions.clear();
        
        List<Point> millorSequencia = new ArrayList<>();
        float millorPuntuacio = Float.NEGATIVE_INFINITY;
        
        // Cerca iterativa: anem augmentant la profunditat fins que s'acaba el temps
        for (int profunditat = 1; profunditat <= 100; profunditat++) {
            try {
                ResultatCerca resultat = cercarAmbProfunditat(status, profunditat);
                
                if (resultat != null && !resultat.sequencia.isEmpty()) {
                    millorSequencia = resultat.sequencia;
                    millorPuntuacio = resultat.puntuacio;
                    profunditatAssolida = profunditat;
                    
                    if (DEBUG) {
                        System.out.println("[IDS] Profunditat " + profunditat + 
                                         " completada. Nodes: " + nodesExplorats + 
                                         " Score: " + millorPuntuacio);
                    }
                }
                
                // Si hem trobat una victòria segura, no cal seguir buscant
                if (millorPuntuacio > 900000) {
                    break;
                }
                
            } catch (ExcepcioTempsEsgotat e) {
                // Temps esgotat, retornem el millor moviment trobat fins ara
                if (DEBUG) {
                    System.out.println("[IDS] Temps esgotat a profunditat " + profunditat);
                }
                break;
            }
        }
        
        // Construïm el PlayerMove amb tota la informació
        PlayerMove pm = new PlayerMove(millorSequencia, nodesExplorats, 
                                       profunditatAssolida, SearchType.MINIMAX_IDS);
        pm.setNumerOfNodesExplored(nodesExplorats);
        pm.setMaxDepthReached(profunditatAssolida);
        pm.setH(millorPuntuacio);
        
        return pm;
    }
    
    /**
     * Realitza una cerca completa a una profunditat determinada.
     * 
     * @param estat estat inicial del joc
     * @param profunditatObjectiu profunditat màxima de cerca
     * @return resultat de la cerca amb la millor seqüència i puntuació
     */
    private ResultatCerca cercarAmbProfunditat(GameStatus estat, int profunditatObjectiu) {
        List<Point> sequenciaCompleta = new ArrayList<>();
        GameStatusTunned estatActual = new GameStatusTunned(estat);
        
        // Generem el torn complet (pot incloure múltiples col·locacions per captures)
        while (true) {
            verificarTemps();
            
            // Cerquem el millor moviment individual amb minimax
            ResultatMinimax res = minimaxAmbPoda(estatActual, profunditatObjectiu, 
                                                 Float.NEGATIVE_INFINITY, 
                                                 Float.POSITIVE_INFINITY, 
                                                 true);
            
            if (res.moviment == null) {
                break;
            }
            
            sequenciaCompleta.add(res.moviment);
            estatActual.placeStone(res.moviment);
            
            // Si el torn ha acabat (no hi ha captura), sortim
            if (estatActual.isGameOver() || 
                estatActual.getCurrentPlayer() != jugadorActual) {
                break;
            }
        }
        
        float puntuacioFinal = avaluar(estatActual);
        return new ResultatCerca(sequenciaCompleta, puntuacioFinal);
    }
    
    /**
     * Implementació de Minimax amb poda alpha-beta i taula de transposicions.
     * 
     * @param estat estat actual del joc
     * @param profunditat profunditat restant
     * @param alpha valor alpha per a la poda
     * @param beta valor beta per a la poda
     * @param esMaximitzador true si és torn del jugador maximitzador
     * @return resultat amb el millor moviment i la seva puntuació
     */
    private ResultatMinimax minimaxAmbPoda(GameStatusTunned estat, int profunditat, 
                                           float alpha, float beta, 
                                           boolean esMaximitzador) {
        verificarTemps();
        nodesExplorats++;
        
        // Consultem la taula de transposicions
        long clau = calcularHash(estat);
        EntradaTaulaTransposicio entrada = taulaTransposicions.get(clau);
        
        if (entrada != null && entrada.profunditat >= profunditat) {
            return new ResultatMinimax(entrada.moviment, entrada.puntuacio);
        }
        
        // Cas base: profunditat 0 o joc acabat
        if (profunditat <= 0 || estat.isGameOver()) {
            float valorHeuristic = avaluar(estat);
            // Amplificar el valor si el joc ha acabat
            if (estat.isGameOver()) {
                valorHeuristic *= 1500.0f;
            }
            guardarATaulaTransposicions(clau, null, valorHeuristic, profunditat);
            return new ResultatMinimax(null, valorHeuristic);
        }
        
        // Generem i ordenem els moviments
        List<Point> moviments = obtenirMovimentsOrdenats(estat);
        
        if (moviments.isEmpty()) {
            float valor = avaluar(estat);
            guardarATaulaTransposicions(clau, null, valor, profunditat);
            return new ResultatMinimax(null, valor);
        }
        
        Point millorMoviment = null;
        float millorValor = esMaximitzador ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        
        // Llista per guardar tots els moviments amb la mateixa millor puntuació
        List<Point> millorsMoviments = new ArrayList<>();
        
        for (Point mov : moviments) {
            GameStatusTunned nouEstat = new GameStatusTunned(estat);
            nouEstat.placeStone(mov);
            
            ResultatMinimax resultat = minimaxAmbPoda(nouEstat, profunditat - 1, 
                                                      alpha, beta, !esMaximitzador);
            
            if (esMaximitzador) {
                if (resultat.puntuacio > millorValor) {
                    millorValor = resultat.puntuacio;
                    millorMoviment = mov;
                    millorsMoviments.clear();
                    millorsMoviments.add(mov);
                } else if (resultat.puntuacio == millorValor) {
                    // Empat! Afegim aquest moviment a la llista
                    millorsMoviments.add(mov);
                }
                alpha = Math.max(alpha, millorValor);
            } else {
                if (resultat.puntuacio < millorValor) {
                    millorValor = resultat.puntuacio;
                    millorMoviment = mov;
                    millorsMoviments.clear();
                    millorsMoviments.add(mov);
                } else if (resultat.puntuacio == millorValor) {
                    // Empat! Afegim aquest moviment a la llista
                    millorsMoviments.add(mov);
                }
                beta = Math.min(beta, millorValor);
            }
            
            // Poda alpha-beta
            if (beta <= alpha) {
                break;
            }
        }
        
        // Si hi ha múltiples moviments amb la mateixa puntuació, triar-ne un aleatòriament
        if (millorsMoviments.size() > 1) {
            millorMoviment = millorsMoviments.get(random.nextInt(millorsMoviments.size()));
        }
        
        guardarATaulaTransposicions(clau, millorMoviment, millorValor, profunditat);
        return new ResultatMinimax(millorMoviment, millorValor);
    }
    
    /**
     * Obté els moviments legals ordenats per prioritat (millors primers).
     * 
     * @param estat estat del joc
     * @return llista de moviments ordenats
     */
    private List<Point> obtenirMovimentsOrdenats(GameStatusTunned estat) {
        List<Point> moviments = estat.getMoves();
        if (moviments == null || moviments.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Ordenem els moviments segons el seu potencial de captura
        List<MovimentPuntuat> movimentsPuntuats = new ArrayList<>();
        for (Point p : moviments) {
            int guanyCaptura = calcularGuanyCaptura(estat, p);
            movimentsPuntuats.add(new MovimentPuntuat(p, guanyCaptura));
        }
        
        movimentsPuntuats.sort((a, b) -> Integer.compare(b.puntuacio, a.puntuacio));
        
        List<Point> resultat = new ArrayList<>();
        for (MovimentPuntuat mp : movimentsPuntuats) {
            resultat.add(mp.punt);
        }
        
        return resultat;
    }
    
    /**
     * Calcula quantes peces enemigues es capturarien amb un moviment.
     * 
     * @param estat estat actual
     * @param moviment moviment a avaluar
     * @return nombre de peces capturades
     */
    private int calcularGuanyCaptura(GameStatusTunned estat, Point moviment) {
        try {
            GameStatusTunned copia = new GameStatusTunned(estat);
            PlayerType torn = copia.getCurrentPlayer();
            
            MyStatus infoBefore = copia.getInfo();
            int pecesRivalAbans = (torn == PlayerType.PLAYER1) ? 
                                 infoBefore.stonesP2 : infoBefore.stonesP1;
            
            copia.placeStone(moviment);
            
            MyStatus infoAfter = copia.getInfo();
            int pecesRivalDespres = (torn == PlayerType.PLAYER1) ? 
                                   infoAfter.stonesP2 : infoAfter.stonesP1;
            
            return Math.max(0, pecesRivalAbans - pecesRivalDespres);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Funció heurística defensiva optimitzada amb MyStatus.
     */
    private float avaluar(GameStatusTunned s) {
        PlayerType opp = (jugadorActual == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        MyStatus info = s.getInfo();

        // 1) MATERIAL (precalculat!)
        int my = (jugadorActual == PlayerType.PLAYER1) ? info.stonesP1 : info.stonesP2;
        int enemy = (jugadorActual == PlayerType.PLAYER1) ? info.stonesP2 : info.stonesP1;

        // 2) MOBILITAT
        int myMoves = 0;
        if (s.getCurrentPlayer() == jugadorActual) {
            List<Point> mvs = s.getMoves();
            myMoves = (mvs == null) ? 0 : mvs.size();
        }

        // 3) AMENAÇA DEL RIVAL
        AmenacesInfo ai = computeOpponentThreat(s);

        // 4) COMBINACIÓ
        return (-W_ENEMY * enemy)
                - (W_MY * my)
                - (W_CAP * ai.enemyCaptureMoves)
                - (W_CHAIN * ai.enemyMaxGreedyChain)
                + (W_MOB * (myMoves - ai.enemyMoves));
    }
    
    /**
     * Calcula amenaces del rival.
     */
    private AmenacesInfo computeOpponentThreat(GameStatusTunned s) {
        AmenacesInfo ai = new AmenacesInfo();
        if (s.isGameOver()) return ai;

        PlayerType opp = (jugadorActual == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        if (s.getCurrentPlayer() != opp) return ai;

        List<Point> moves = s.getMoves();
        ai.enemyMoves = (moves == null) ? 0 : moves.size();
        if (moves == null || moves.isEmpty()) return ai;

        int capCount = 0;
        int bestChain = 0;

        for (Point p : moves) {
            GameStatusTunned ns = new GameStatusTunned(s);
            PlayerType before = ns.getCurrentPlayer();
            ns.placeStone(p);

            boolean captured = (!ns.isGameOver() && ns.getCurrentPlayer() == before);
            if (captured) {
                capCount++;
                int chainLen = 1 + greedyChainLength(ns, before);
                if (chainLen > bestChain) bestChain = chainLen;
            }
        }

        ai.enemyCaptureMoves = capCount;
        ai.enemyMaxGreedyChain = bestChain;
        return ai;
    }
    
    /**
     * Estima longitud de cadena greedy.
     */
    private int greedyChainLength(GameStatusTunned s, PlayerType player) {
        int len = 0;
        while (!s.isGameOver() && s.getCurrentPlayer() == player) {
            List<Point> moves = s.getMoves();
            if (moves == null || moves.isEmpty()) break;

            Point best = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            for (Point p : moves) {
                GameStatusTunned ns = new GameStatusTunned(s);
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
     * Heurística ràpida basada en material (usa MyStatus).
     */
    private float quickMaterial(GameStatusTunned s, PlayerType me) {
        PlayerType opp = (me == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        MyStatus info = s.getInfo();
        
        int my = (me == PlayerType.PLAYER1) ? info.stonesP1 : info.stonesP2;
        int enemy = (me == PlayerType.PLAYER1) ? info.stonesP2 : info.stonesP1;
        
        return (-100.0f * enemy) - my;
    }
    
    /**
     * Calcula un hash únic per a un estat del joc.
     * 
     * @param estat estat del joc
     * @return hash de l'estat
     */
    private long calcularHash(GameStatusTunned estat) {
        long hash = 7919L;
        int mida = estat.getSize();
        
        for (int y = 0; y < mida; y++) {
            for (int x = 0; x < mida; x++) {
                PlayerType color = estat.getColor(x, y);
                long valor = (color == null) ? 0 : 
                            (color == PlayerType.PLAYER1) ? 1 : 2;
                hash = hash * 31 + valor + x * 97 + y * 193;
            }
        }
        
        return hash;
    }
    
    /**
     * Guarda una entrada a la taula de transposicions.
     */
    private void guardarATaulaTransposicions(long clau, Point moviment, 
                                            float puntuacio, int profunditat) {
        // Limitem la mida de la taula
        if (taulaTransposicions.size() >= MIDA_TAULA) {
            // Estratègia simple: esborrem entrades aleatòries
            if (Math.random() < 0.1) {
                taulaTransposicions.clear();
            }
        }
        
        taulaTransposicions.put(clau, 
            new EntradaTaulaTransposicio(moviment, puntuacio, profunditat));
    }
    
    /**
     * Verifica si s'ha esgotat el temps disponible.
     */
    private void verificarTemps() {
        if (System.currentTimeMillis() - tempsInici >= limitTemps) {
            tempsEsgotat = true;
            throw new ExcepcioTempsEsgotat();
        }
    }
    
    @Override
    public void timeout() {
        tempsEsgotat = true;
    }
    
    // ========== CLASSES AUXILIARS ==========
    
    /**
     * Entrada de la taula de transposicions.
     */
    private static class EntradaTaulaTransposicio {
        Point moviment;
        float puntuacio;
        int profunditat;
        
        EntradaTaulaTransposicio(Point mov, float punt, int prof) {
            this.moviment = mov;
            this.puntuacio = punt;
            this.profunditat = prof;
        }
    }
    
    /**
     * Resultat d'una cerca a una profunditat determinada.
     */
    private static class ResultatCerca {
        List<Point> sequencia;
        float puntuacio;
        
        ResultatCerca(List<Point> seq, float punt) {
            this.sequencia = seq;
            this.puntuacio = punt;
        }
    }
    
    /**
     * Resultat d'una crida a minimax.
     */
    private static class ResultatMinimax {
        Point moviment;
        float puntuacio;
        
        ResultatMinimax(Point mov, float punt) {
            this.moviment = mov;
            this.puntuacio = punt;
        }
    }
    
    /**
     * Moviment amb puntuació associada per a ordenació.
     */
    private static class MovimentPuntuat {
        Point punt;
        int puntuacio;
        
        MovimentPuntuat(Point p, int punt) {
            this.punt = p;
            this.puntuacio = punt;
        }
    }
    
    /**
     * Informació sobre amenaces del rival.
     */
    private static class AmenacesInfo {
        int enemyMoves = 0;
        int enemyCaptureMoves = 0;
        int enemyMaxGreedyChain = 0;
    }
    
    /**
     * Excepció llançada quan s'esgota el temps.
     */
    private static class ExcepcioTempsEsgotat extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
