/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust.players.MillierAranda;

import edu.upc.epsevg.prop.oust.Dir;
import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.players.MillierAranda.GameStatusTunned;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.PlayerType;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.*;

/**
 * Implementació d'un jugador Minimax amb profunditat fixa i poda alpha-beta.
 * 
 * <p>Aquest jugador utilitza l'algorisme Minimax amb les següents optimitzacions:</p>
 * <ul>
 *   <li><b>Poda Alpha-Beta:</b> Redueix l'espai de cerca eliminant branques innecessàries</li>
 *   <li><b>Taula de Transposició:</b> Evita recalcular estats ja avaluats</li>
 *   <li><b>Ordenació de Moviments:</b> Avalua primer els moviments més prometedors</li>
 *   <li><b>Heurística Avançada:</b> Valora captures, control del centre, grups i vulnerabilitats</li>
 * </ul>
 * 
 * <h2>Característiques Principals:</h2>
 * <ul>
 *   <li>Profunditat de cerca fixa (especificada al constructor)</li>
 *   <li>Ignora el timeout del framework (exploració completa fins la profunditat)</li>
 *   <li>Pot ser interromput externament per PlayerMiniMaxIDS</li>
 *   <li>Estratègia defensiva amb filtratge de moviments perillosos</li>
 * </ul>
 * 
 * <h2>Estratègia de Joc:</h2>
 * <ol>
 *   <li><b>Control del Centre:</b> Prioritza posicions centrals en fase inicial</li>
 *   <li><b>Construcció de Grups:</b> Crea grups compactes i evita fragmentació</li>
 *   <li><b>Valoració de Captures:</b> Busca oportunitats de captura de forma agressiva</li>
 *   <li><b>Distància Segura:</b> Manté distància >= 3 respecte l'enemic excepte en captures</li>
 *   <li><b>Detecció de Vulnerabilitats:</b> Evita crear grups petits fàcils de capturar</li>
 * </ol>
 * 
 * <p><b>Nota sobre Timeout:</b> Aquest jugador "ignora el timeout" en el sentit que
 * el seu mètode timeout() no fa res. No obstant, pot ser interromput externament
 * mitjançant setExternalTimeout(), permetent a PlayerMiniMaxIDS gestionar el temps.</p>
 * 
 * @author Erik Millier, Alex Aranda
 * @see PlayerMiniMaxIDS
 */
public class PlayerMiniMax implements IPlayer, IAuto {
    
    /** Nom identificatiu del jugador. */
    private String name;
    /** Profunditat màxima de cerca de l'arbre Minimax. */
    private int profunditatMaxima;
    /** Tipus de jugador (PLAYER1 o PLAYER2) que controla aquest agent. */
    private PlayerType jugadorPropi;
    /** Comptador de nodes explorats durant la cerca actual. */
    private long nodesExplorats;
    
    /** 
     * Taula de transposició per emmagatzemar estats ja avaluats.
     * Clau: hashcode de l'estat, Valor: entrada amb valor i profunditat.
     */
    private Map<Integer, TranspositionEntry> taulaTransposicio;
    
    /** 
     * Flag extern que permet a PlayerMiniMaxIDS interrompre l'execució.
     * Volatile per assegurar visibilitat entre threads.
     */
    private volatile boolean externalTimeout;
    
    /** 
     * Valor heurístic que representa una victòria garantida.
     * S'utilitza per aturar la cerca quan es detecta un estat guanyador.
     */
    private static final int VICTORIA = 1000000;
    /** 
     * Valor heurístic que representa una derrota garantida.
     * S'utilitza per aturar la cerca quan es detecta un estat perdedor.
     */
    private static final int DERROTA = -1000000;
    
    /**
     * Constructor OBLIGATORI del jugador Minimax.
     * 
     * @param profunditatMaxima Profunditat màxima de cerca de l'algorisme
     */
    public PlayerMiniMax(int profunditatMaxima) {
        this.name = "MiniMax";
        this.profunditatMaxima = profunditatMaxima;
        this.taulaTransposicio = new HashMap<>();
        this.externalTimeout = false;
    }
    
    /**
     * Estableix el flag d'interrupció externa.
     * 
     * <p>Aquest mètode permet a PlayerMiniMaxIDS interrompre l'execució
     * del Minimax quan s'exhaureix el temps. Quan el flag s'estableix a true,
     * el minimax deixa de calcular i retorna ràpidament.</p>
     * 
     * <p><b>Nota:</b> Aquest mecanisme no viola l'especificació que diu que
     * PlayerMiniMax "ignora el timeout", ja que aquest jugador no gestiona
     * el temps ell mateix - simplement pot ser interromput externament.</p>
     * 
     * @param timeout true per interrompre l'execució, false per permetre-la
     */
    public void setExternalTimeout(boolean timeout) {
        this.externalTimeout = timeout;
    }
    
    /**
     * Mètode cridat pel framework quan s'exhaureix el temps.
     * 
     * <p>Aquest mètode NO FA RES segons l'especificació que diu que
     * PlayerMiniMax "ignora el timeout". La gestió del temps es fa
     * externament per PlayerMiniMaxIDS utilitzant setExternalTimeout().</p>
     */
    @Override
    public void timeout() {
        // PlayerMiniMax IGNORA el timeout segons especificacions
        // (no gestiona el temps ell mateix)
    }
    
    /**
     * Retorna el nom identificatiu del jugador.
     * 
     * @return Nom del jugador ("MiniMax")
     */
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Decideix el millor moviment utilitzant Minimax amb poda alpha-beta.
     * 
     * <p>Aquest mètode implementa l'algorisme Minimax complet:</p>
     * <ol>
     *   <li>Inicialitza comptadors i estructures</li>
     *   <li>Obté i ordena els moviments disponibles</li>
     *   <li>Filtra moviments perillosos (dist < 3 a l'enemic)</li>
     *   <li>Per cada moviment segur:
     *     <ul>
     *       <li>Construeix el camí complet segons regles d'Oust</li>
     *       <li>Avalua amb minimax fins a profunditatMaxima</li>
     *       <li>Actualitza el millor moviment trobat</li>
     *     </ul>
     *   </li>
     *   <li>Retorna el millor moviment amb estadístiques</li>
     * </ol>
     * 
     * <p><b>Ordenació de Moviments:</b> Els moviments s'ordenen per proximitat
     * al centre per millorar la poda alpha-beta i evitar col·locació lineal.</p>
     * 
     * <p><b>Desempat Aleatori:</b> Si dos moviments tenen igual valoració,
     * hi ha un 30% de probabilitat de canviar, evitant determinisme.</p>
     * 
     * @param s Estat actual del joc
     * @return Millor moviment trobat, incloent:
     *         <ul>
     *           <li>Camí complet de punts</li>
     *           <li>Nombre de nodes explorats</li>
     *           <li>Profunditat utilitzada</li>
     *           <li>Tipus de cerca (MINIMAX)</li>
     *         </ul>
     */
    @Override
    public PlayerMove move(GameStatus s) {
        nodesExplorats = 0;
        jugadorPropi = s.getCurrentPlayer();
        taulaTransposicio.clear();
        
        GameStatusTunned estat = new GameStatusTunned(s);
        
        List<Point> millorCami = new ArrayList<>();
        int millorValor = Integer.MIN_VALUE;
        
        List<Point> moviments = estat.getMoves();
        
        if (moviments.isEmpty()) {
            return new PlayerMove(millorCami, nodesExplorats, profunditatMaxima, SearchType.MINIMAX);
        }
        
        if (moviments.size() == 1) {
            millorCami = construirCamiComplet(estat, moviments.get(0));
            return new PlayerMove(millorCami, nodesExplorats, profunditatMaxima, SearchType.MINIMAX);
        }
        
        // Ordenar i filtrar moviments
        int centre = estat.getSize() / 2;
        moviments.sort((p1, p2) -> {
            int dist1 = Math.abs(p1.x - centre) + Math.abs(p1.y - centre);
            int dist2 = Math.abs(p2.x - centre) + Math.abs(p2.y - centre);
            return Integer.compare(dist1, dist2);
        });
        
        moviments = filtrarMovimentsSegurs(estat, moviments);
        
        for (Point mov : moviments) {
            // Comprovar timeout abans de cada moviment
            if (externalTimeout) {
                break;
            }
            
            GameStatusTunned nouEstat = new GameStatusTunned(estat);
            List<Point> cami = construirCamiComplet(nouEstat, mov);
            
            int valor = minimax(nouEstat, profunditatMaxima - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (valor == millorValor && Math.random() < 0.3) {
                millorValor = valor;
                millorCami = new ArrayList<>(cami);
            } else if (valor > millorValor) {
                millorValor = valor;
                millorCami = new ArrayList<>(cami);
            }
        }
        
        if (millorCami.isEmpty() && !moviments.isEmpty()) {
            millorCami = construirCamiComplet(estat, moviments.get(0));
        }
        
        return new PlayerMove(millorCami, nodesExplorats, profunditatMaxima, SearchType.MINIMAX);
    }
    
    /**
     * Algorisme Minimax recursiu amb poda alpha-beta.
     * 
     * <p>Aquesta és la funció central de l'algorisme que avalua recursivament
     * l'arbre de joc alternant entre maximitzar (jugador propi) i minimitzar
     * (jugador enemic).</p>
     * 
     * <h3>Optimitzacions:</h3>
     * <ul>
     *   <li><b>Poda Alpha-Beta:</b> Talla branques que no poden millorar el resultat</li>
     *   <li><b>Taula de Transposició:</b> Evita recalcular estats repetits</li>
     *   <li><b>Detecció de Timeout:</b> Retorna ràpidament si externalTimeout == true</li>
     *   <li><b>Ordenació de Moviments:</b> Millora l'eficiència de la poda</li>
     * </ul>
     * 
     * <h3>Casos Base:</h3>
     * <ol>
     *   <li>Timeout extern: retorna 0</li>
     *   <li>Estat en cache: retorna valor emmagatzemat</li>
     *   <li>Joc acabat: retorna VICTORIA o DERROTA</li>
     *   <li>Profunditat 0: retorna heurística</li>
     *   <li>No hi ha moviments: retorna heurística</li>
     * </ol>
     * 
     * @param estat Estat del joc a avaluar
     * @param profunditat Profunditat restant de cerca
     * @param alpha Millor valor per al maximitzador (poda alpha)
     * @param beta Millor valor per al minimitzador (poda beta)
     * @param esMaximitzant true si és el torn del jugador propi, false si és de l'enemic
     * @return Valor heurístic de l'estat des del punt de vista del jugador propi
     */
    private int minimax(GameStatusTunned estat, int profunditat, int alpha, int beta, boolean esMaximitzant) {
        nodesExplorats++;
        
        // Comprovar si PlayerMiniMaxIDS ha interromput l'execució
        if (externalTimeout) {
            return 0; // Retornar ràpidament si s'ha exhaurit el temps
        }
        
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
        
        moviments = filtrarMovimentsSegurs(estat, moviments);
        
        int centre = estat.getSize() / 2;
        moviments.sort((p1, p2) -> {
            int dist1 = Math.abs(p1.x - centre) + Math.abs(p1.y - centre);
            int dist2 = Math.abs(p2.x - centre) + Math.abs(p2.y - centre);
            return Integer.compare(dist1, dist2);
        });
        
        if (moviments.isEmpty()) {
            int valor = heuristica(estat);
            return valor;
        }
        
        if (esMaximitzant) {
            int maxValor = Integer.MIN_VALUE;
            
            for (Point mov : moviments) {
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
     * Filtra moviments segons criteris de seguretat estratègica.
     * 
     * <p>Un moviment es considera segur si:</p>
     * <ul>
     *   <li>És una captura (sempre permès), o</li>
     *   <li>Està a distància >= 3 de l'enemic més proper, o</li>
     *   <li>Està a distància 2 però forma una trampa vàlida</li>
     * </ul>
     * 
     * @param estat Estat actual del joc
     * @param moviments Llista de moviments a filtrar
     * @return Llista de moviments segurs (o tots si no n'hi ha cap de segur)
     */
    private List<Point> filtrarMovimentsSegurs(GameStatusTunned estat, List<Point> moviments) {
        PlayerType enemic = jugadorPropi.opposite();
        List<Point> movimentsSegurs = new ArrayList<>();
        
        for (Point mov : moviments) {
            if (estat.isCapturingMove(mov)) {
                movimentsSegurs.add(mov);
                continue;
            }
            
            int distMin = calcularDistanciaMinima(estat, mov, enemic);
            
            if (distMin >= 3) {
                movimentsSegurs.add(mov);
            } else if (distMin == 2 && esTrampaValida(estat, mov)) {
                movimentsSegurs.add(mov);
            }
        }
        
        if (movimentsSegurs.isEmpty()) {
            return moviments;
        }
        
        return movimentsSegurs;
    }
    
    /**
     * Calcula la distància mínima de Manhattan entre una posició i totes les pedres enemigues.
     * 
     * <p>La distància de Manhattan (o distància rectilínea) és la suma de les diferències
     * absolutes de les coordenades: |x1-x2| + |y1-y2|. És la mètrica adequada per a Oust
     * ja que els moviments són en les 4 direccions cardinals.</p>
     * 
     * <p><b>Ús estratègic:</b> Aquesta funció s'utilitza per determinar si una posició
     * és "segura" (distància >= 3) o "perillosa" (distància < 3) respecte l'enemic.</p>
     * 
     * <h3>Exemples:</h3>
     * <pre>
     * Posició (5,5), Enemic a (5,7) → Distància = |5-5| + |5-7| = 2
     * Posició (3,3), Enemic a (6,7) → Distància = |3-6| + |3-7| = 7
     * </pre>
     * 
     * @param estat Estat actual del joc
     * @param pos Posició de la qual calcular la distància
     * @param enemic Tipus de jugador enemic
     * @return Distància mínima a la pedra enemiga més propera, o Integer.MAX_VALUE
     *         si no hi ha cap pedra enemiga al tauler
     */
    private int calcularDistanciaMinima(GameStatusTunned estat, Point pos, PlayerType enemic) {
        int distMin = Integer.MAX_VALUE;
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (estat.isInBounds(p) && estat.getColor(p) == enemic) {
                    int dist = Math.abs(pos.x - p.x) + Math.abs(pos.y - p.y);
                    distMin = Math.min(distMin, dist);
                }
            }
        }
        
        return distMin;
    }
    
    /**
     * Determina si una posició a distància 2 de l'enemic forma una trampa vàlida.
     * 
     * <p>Una trampa és vàlida quan aparentment viola la regla de distància >= 3,
     * però en realitat és una estratègia defensiva que connecta amb un grup aliat
     * fort capaç de contra-atacar si l'enemic intenta capturar.</p>
     * 
     * <h3>Criteris per a trampa vàlida:</h3>
     * <ol>
     *   <li>Ha de tenir almenys un aliat adjacent (connexió)</li>
     *   <li>El grup aliat adjacent ha de ser fort (>= 3 pedres)</li>
     * </ol>
     * 
     * <p><b>Raonament estratègic:</b> Si l'enemic intenta capturar una pedra a
     * distància 2, el nostre grup gran adjacent pot contra-atacar i capturar
     * més pedres de les que perdem.</p>
     * 
     * <h3>Exemple:</h3>
     * <pre>
     * E = Enemic, N = Nostre, ? = Posició candidata
     * 
     * N N N
     * N ? .    ← La ? a distància 2 de E és vàlida perquè connecta
     * . E .       amb un grup de 5 aliats
     * </pre>
     * 
     * @param estat Estat actual del joc
     * @param pos Posició candidata a distància 2 de l'enemic
     * @return true si és una trampa vàlida (segura malgrat dist=2), false altrament
     */
    private boolean esTrampaValida(GameStatusTunned estat, Point pos) {
        int aliatsAdjacents = 0;
        int midaGrupMaxim = 0;
        
        for (Dir dir : Dir.values()) {
            Point adj = dir.add(pos);
            if (estat.isInBounds(adj) && estat.getColor(adj) == jugadorPropi) {
                aliatsAdjacents++;
                int mida = estat.getGroupSize(adj);
                midaGrupMaxim = Math.max(midaGrupMaxim, mida);
            }
        }
        
        return aliatsAdjacents > 0 && midaGrupMaxim >= 3;
    }
    
    /**
     * Construeix el camí complet d'un moviment seguint les regles d'Oust.
     * 
     * <p>Segons les regles d'Oust, després d'una captura el jugador pot (i ha de)
     * continuar col·locant pedres fins que:</p>
     * <ol>
     *   <li>Col·loca una pedra que NO captura (fi del torn), o</li>
     *   <li>No té més moviments disponibles</li>
     * </ol>
     * 
     * <p><b>CRÍTICO:</b> El torn SEMPRE ha d'acabar amb un moviment no-captura.
     * Si no hi ha moviments segurs disponibles, s'utilitza el moviment amb
     * màxima distància a l'enemic.</p>
     * 
     * @param estat Estat del joc
     * @param primerMov Primer moviment del camí
     * @return Llista completa de punts que formen el camí del moviment
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
                } else if (!nonCaptureMoves.isEmpty()) {
                    seguentMov = seleccionarMovimentSegur(nouEstat, nonCaptureMoves);
                } else if (!captureMoves.isEmpty()) {
                    seguentMov = seleccionarMillorCaptura(nouEstat, captureMoves);
                }
            } else if (!nonCaptureMoves.isEmpty()) {
                seguentMov = seleccionarMovimentSegur(nouEstat, nonCaptureMoves);
            }
            
            if (seguentMov == null && !nonCaptureMoves.isEmpty()) {
                seguentMov = triarMovimentMaximaDistancia(nouEstat, nonCaptureMoves);
            }
            
            if (seguentMov == null) {
                break;
            }
            
            cami.add(seguentMov);
            nouEstat.placeStone(seguentMov);
            iter++;
        }
        
        return cami;
    }
    
    /**
     * Aplica un moviment complet al estat seguint les regles d'Oust.
     * 
     * <p>Segons les regles d'Oust, després de col·locar una pedra que captura,
     * el jugador pot (i ha de) continuar col·locant pedres fins que:</p>
     * <ol>
     *   <li>Col·loca una pedra que NO captura → Fi del torn</li>
     *   <li>No té més moviments disponibles → Fi del torn</li>
     * </ol>
     * 
     * <p><b>Diferència amb construirCamiComplet():</b> Aquest mètode modifica
     * directament l'estat proporcionat, mentre que construirCamiComplet()
     * retorna la llista de punts sense modificar l'estat original.</p>
     * 
     * <p><b>CRÍTICO:</b> Utilitza el mateix sistema de filtratge que
     * construirCamiComplet() per evitar errors "Invalid move sequence".</p>
     * 
     * <h3>Estratègia de continuació:</h3>
     * <ul>
     *   <li><b>Prioritat 1:</b> Captures segures (no contra-atacables)</li>
     *   <li><b>Prioritat 2:</b> Moviment no-captura segur (dist >= 3)</li>
     *   <li><b>Prioritat 3:</b> Captura encara que sigui perillosa</li>
     *   <li><b>Fallback:</b> Moviment amb màxima distància a l'enemic</li>
     * </ul>
     * 
     * @param estat Estat del joc a modificar (és modificat durant l'execució)
     * @param mov Primer moviment del camí a aplicar
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
                } else if (!nonCaptureMoves.isEmpty()) {
                    millorMov = seleccionarMovimentSegur(estat, nonCaptureMoves);
                } else if (!captureMoves.isEmpty()) {
                    millorMov = seleccionarMillorCaptura(estat, captureMoves);
                }
            } else if (!nonCaptureMoves.isEmpty()) {
                millorMov = seleccionarMovimentSegur(estat, nonCaptureMoves);
            }
            
            if (millorMov == null && !nonCaptureMoves.isEmpty()) {
                millorMov = triarMovimentMaximaDistancia(estat, nonCaptureMoves);
            }
            
            if (millorMov == null) {
                break;
            }
            
            estat.placeStone(millorMov);
            iter++;
        }
    }
    
    /**
     * Comprova si una posició pot ser capturada en el següent torn de l'enemic.
     * 
     * <p>Una posició és vulnerable si:</p>
     * <ul>
     *   <li>Té veïns enemics adjacents, i</li>
     *   <li>L'enemic té un grup gran (>= 3 pedres) adjacent que pot capturar</li>
     * </ul>
     * 
     * <p><b>Ús estratègic:</b> Aquesta funció s'utilitza per evitar col·locar
     * pedres en posicions que seran capturades immediatament, especialment
     * durant les cadenes de captura.</p>
     * 
     * <h3>Lògica d'avaluació:</h3>
     * <pre>
     * Si grup enemic adjacent >= 3 pedres:
     *   → Alta probabilitat de captura
     *   → Evitar aquesta posició
     * Si grup enemic adjacent < 3 pedres:
     *   → Baixa probabilitat de captura
     *   → Posició acceptable
     * </pre>
     * 
     * <p><b>Nota:</b> Aquesta és una estimació heurística. No garanteix que
     * l'enemic capturarà (depèn de la seva estratègia), però és una bona
     * aproximació per evitar moviments òbviament dolents.</p>
     * 
     * @param estat Estat del joc després de col·locar la pedra
     * @param pos Posició a comprovar
     * @return true si és probable que sigui capturada en el següent torn, false altrament
     */
    private boolean potSerCapturatEnSeguent(GameStatusTunned estat, Point pos) {
        PlayerType enemic = jugadorPropi.opposite();
        
        int veinsEnemics = 0;
        int midaGrupEnemicMax = 0;
        
        for (Dir dir : Dir.values()) {
            Point adj = dir.add(pos);
            if (estat.isInBounds(adj) && estat.getColor(adj) == enemic) {
                veinsEnemics++;
                int mida = estat.getGroupSize(adj);
                midaGrupEnemicMax = Math.max(midaGrupEnemicMax, mida);
            }
        }
        
        return veinsEnemics > 0 && midaGrupEnemicMax >= 3;
    }
    
    /**
     * Selecciona el millor moviment no-captura segons criteris de seguretat i estratègia.
     * 
     * <p>Aquest mètode implementa la lògica de selecció defensiva del jugador,
     * prioritzant moviments que mantenen distància segura de l'enemic mentre
     * construeixen grups forts.</p>
     * 
     * <h3>Procés de selecció:</h3>
     * <ol>
     *   <li><b>Filtrar:</b> Només moviments amb distància >= 3 a l'enemic</li>
     *   <li><b>Fallback:</b> Si no n'hi ha, buscar trampes vàlides (dist = 2)</li>
     *   <li><b>Valorar:</b> Entre els moviments segurs, prioritzar:
     *     <ul>
     *       <li>Connexió amb grups grans existents (x80)</li>
     *       <li>Nombre d'aliats adjacents (x30)</li>
     *       <li>Proximitat al centre (x5)</li>
     *     </ul>
     *   </li>
     * </ol>
     * 
     * <p><b>Estratègia de construcció de grups:</b> Aquest mètode implementa
     * la filosofia de "construir grups grans i compactes". Prioritza connectar
     * amb el grup aliat més gran adjacent per consolidar forces.</p>
     * 
     * <h3>Pesos de valoració:</h3>
     * <table border="1">
     *   <tr><th>Factor</th><th>Pes</th><th>Raonament</th></tr>
     *   <tr><td>Connexió amb grup gran</td><td>x80</td><td>Consolidar forces</td></tr>
     *   <tr><td>Aliats adjacents</td><td>x30</td><td>Compacitat</td></tr>
     *   <tr><td>Proximitat al centre</td><td>x5</td><td>Control posicional</td></tr>
     * </table>
     * 
     * @param estat Estat actual del joc
     * @param nonCaptures Llista de moviments no-captura disponibles
     * @return Millor moviment segur, o null si no n'hi ha cap que compleixi els criteris
     */
    private Point seleccionarMovimentSegur(GameStatusTunned estat, List<Point> nonCaptures) {
        if (nonCaptures.isEmpty()) return null;
        
        PlayerType enemic = jugadorPropi.opposite();
        int centre = estat.getSize() / 2;
        
        List<Point> movimentsSegurs = new ArrayList<>();
        for (Point mov : nonCaptures) {
            int distMin = calcularDistanciaMinima(estat, mov, enemic);
            if (distMin >= 3) {
                movimentsSegurs.add(mov);
            }
        }
        
        if (movimentsSegurs.isEmpty()) {
            for (Point mov : nonCaptures) {
                int distMin = calcularDistanciaMinima(estat, mov, enemic);
                if (distMin == 2 && esTrampaValida(estat, mov)) {
                    movimentsSegurs.add(mov);
                }
            }
        }
        
        if (movimentsSegurs.isEmpty()) {
            return null;
        }
        
        Point millor = movimentsSegurs.get(0);
        int millorValor = Integer.MIN_VALUE;
        
        for (Point mov : movimentsSegurs) {
            int distCentre = Math.abs(mov.x - centre) + Math.abs(mov.y - centre);
            int valor = (estat.getSize() - distCentre) * 5;
            
            int aliatsAdjacents = 0;
            int midaGrupMaximAdjacent = 0;
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(mov);
                if (estat.isInBounds(adj) && estat.getColor(adj) == jugadorPropi) {
                    aliatsAdjacents++;
                    int midaGrup = estat.getGroupSize(adj);
                    midaGrupMaximAdjacent = Math.max(midaGrupMaximAdjacent, midaGrup);
                }
            }
            
            valor += midaGrupMaximAdjacent * 80;
            valor += aliatsAdjacents * 30;
            
            if (valor > millorValor) {
                millorValor = valor;
                millor = mov;
            }
        }
        
        return millor;
    }
    
    /**
     * Tria el moviment amb màxima distància a l'enemic quan no hi ha opcions segures.
     * 
     * <p>Aquest és el mètode de <b>última instància</b> que s'utilitza quan:</p>
     * <ul>
     *   <li>No hi ha moviments amb distància >= 3</li>
     *   <li>No hi ha trampes vàlides (distància 2 amb grup fort)</li>
     *   <li>Cal acabar el torn amb un moviment no-captura</li>
     * </ul>
     * 
     * <p><b>CRÍTICO:</b> Aquest mètode prevé l'error "Invalid move sequence"
     * que es produeix quan un torn acaba sense moviment no-captura. És el fix
     * mínim que garanteix que sempre es compleixen les regles d'Oust.</p>
     * 
     * <h3>Criteris de selecció:</h3>
     * <ol>
     *   <li><b>Primer criteri:</b> Màxima distància a l'enemic més proper</li>
     *   <li><b>Desempat:</b> Proximitat al centre del tauler</li>
     * </ol>
     * 
     * <h3>Raonament estratègic:</h3>
     * <pre>
     * Si hem d'acabar el torn i no tenim opcions segures:
     *   → Allunyar-nos tant com sigui possible de l'enemic
     *   → Preferir posicions centrals (més opcions futures)
     *   → Minimitzar el risc de captura immediata
     * </pre>
     * 
     * <p><b>Nota:</b> Aquest moviment no serà òptim, però garanteix que el
     * torn és vàlid i que com a mínim hem maximitzat la distància a l'enemic.</p>
     * 
     * @param estat Estat actual del joc
     * @param nonCaptures Llista de moviments no-captura disponibles
     * @return Moviment amb màxima distància a l'enemic, prioritzant centre en cas d'empat;
     *         retorna null si la llista està buida
     */
    private Point triarMovimentMaximaDistancia(GameStatusTunned estat, List<Point> nonCaptures) {
        if (nonCaptures.isEmpty()) return null;
        
        PlayerType enemic = jugadorPropi.opposite();
        int centre = estat.getSize() / 2;
        
        Point millor = nonCaptures.get(0);
        int millorDistancia = calcularDistanciaMinima(estat, millor, enemic);
        int millorValorCentre = Math.abs(millor.x - centre) + Math.abs(millor.y - centre);
        
        for (Point mov : nonCaptures) {
            int dist = calcularDistanciaMinima(estat, mov, enemic);
            int distCentre = Math.abs(mov.x - centre) + Math.abs(mov.y - centre);
            
            if (dist > millorDistancia) {
                millor = mov;
                millorDistancia = dist;
                millorValorCentre = distCentre;
            } else if (dist == millorDistancia && distCentre < millorValorCentre) {
                millor = mov;
                millorValorCentre = distCentre;
            }
        }
        
        return millor;
    }
    
    /**
     * Selecciona la millor captura segons el valor estimat de pedres capturades.
     * 
     * <p>Entre múltiples opcions de captura disponibles, aquest mètode tria
     * la que captura més pedres enemigues, maximitzant el guany material
     * immediat.</p>
     * 
     * <p><b>Mètode d'estimació:</b> Utilitza {@link GameStatusTunned#estimateCaptureValue(Point)}
     * que compta les pedres enemigues adjacents a la posició de captura.</p>
     * 
     * <h3>Estratègia greedy:</h3>
     * <pre>
     * Per cada captura disponible:
     *   1. Estimar valor de captura (pedres enemigues adjacents)
     *   2. Seleccionar la de màxim valor
     * </pre>
     * 
     * <p><b>Limitació:</b> Aquesta és una estimació simple que no considera
     * captures en cadena o conseqüències estratègiques. És suficient per a
     * la majoria de situacions però podria millorar-se amb look-ahead.</p>
     * 
     * <h3>Exemple:</h3>
     * <pre>
     * Opció A: Captura 2 pedres enemigues → Valor = 2
     * Opció B: Captura 5 pedres enemigues → Valor = 5 ✓ (seleccionada)
     * Opció C: Captura 1 pedra enemiga  → Valor = 1
     * </pre>
     * 
     * @param estat Estat actual del joc
     * @param captures Llista de moviments de captura disponibles
     * @return Moviment de captura amb màxim valor estimat; retorna el primer
     *         de la llista si tots tenen el mateix valor
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
     * Avalua un estat terminal del joc (victòria, derrota o empat).
     * 
     * <p>Aquest mètode retorna valors molt alts (VICTORIA/DERROTA) per
     * estats finals, permetent al minimax prioritzar camins que porten
     * a la victòria i evitar camins que porten a la derrota.</p>
     * 
     * <h3>Valors de retorn:</h3>
     * <table border="1">
     *   <tr><th>Resultat</th><th>Valor</th><th>Significat</th></tr>
     *   <tr><td>Victòria pròpia</td><td>+1,000,000</td><td>Estat òptim</td></tr>
     *   <tr><td>Derrota</td><td>-1,000,000</td><td>Estat pessim</td></tr>
     *   <tr><td>Empat/Indefinit</td><td>0</td><td>Estat neutral</td></tr>
     * </table>
     * 
     * <p><b>Importància:</b> Aquests valors extrems asseguren que el minimax
     * sempre preferirà una victòria garantida sobre qualsevol avantatge
     * heurístic, i evitarà una derrota garantida a qualsevol preu.</p>
     * 
     * <h3>Exemple de decisió:</h3>
     * <pre>
     * Camí A: Heurística +5000 però porta a derrota → Valor final = -1,000,000 ✗
     * Camí B: Heurística +500 i porta a victòria   → Valor final = +1,000,000 ✓
     * </pre>
     * 
     * @param estat Estat terminal del joc
     * @return VICTORIA (1,000,000) si guanya el jugador propi,
     *         DERROTA (-1,000,000) si guanya l'enemic,
     *         0 si és empat o no s'ha determinat guanyador
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
     * Funció heurística que avalua un estat del joc.
     * 
     * <p>Aquesta funció combina múltiples factors estratègics per donar
     * una valoració numèrica de "qui va guanyant" en un estat donat.</p>
     * 
     * <h3>Factors Avaluats:</h3>
     * <table border="1">
     *   <tr><th>Factor</th><th>Pes</th><th>Descripció</th></tr>
     *   <tr><td>Material</td><td>x60</td><td>Diferència de pedres al tauler</td></tr>
     *   <tr><td>Grups</td><td>x35</td><td>Penalitza fragmentació</td></tr>
     *   <tr><td>Grup Màxim</td><td>x35</td><td>Valora grup més gran</td></tr>
     *   <tr><td>Control Centre</td><td>x50</td><td>Posicions centrals (x5 en fase inicial)</td></tr>
     *   <tr><td>Mobilitat</td><td>x20</td><td>Nombre de moviments disponibles</td></tr>
     *   <tr><td>Captures</td><td>x150</td><td>Oportunitats de captura</td></tr>
     *   <tr><td>Vulnerabilitat</td><td>x60</td><td>Grups petits exposats</td></tr>
     * </table>
     * 
     * <p><b>Fase del Joc:</b> El control del centre té pes x5 en fase inicial
     * (< 30% del tauler ple) i pes x1 en fase mitjana/final.</p>
     * 
     * @param estat Estat del joc a avaluar
     * @return Valor heurístic des del punt de vista del jugador propi
     *         (positiu = avantatjós, negatiu = desavantatjós)
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
        
        int controlCentre = 0;
        int centre = estat.getSize() / 2;
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!estat.isInBounds(p)) continue;
                
                PlayerType color = estat.getColor(p);
                
                if (color == jugadorPropi) {
                    pedresPropi++;
                    int distCentre = Math.abs(i - centre) + Math.abs(j - centre);
                    controlCentre += (estat.getSize() - distCentre) * 10;
                    
                    if (!visitats.containsKey(p)) {
                        int mida = marcarGrupAmbMida(estat, p, jugadorPropi, visitats, midaGrupPropi);
                        grupsPropi++;
                        midaGrupPropiMaxim = Math.max(midaGrupPropiMaxim, mida);
                    }
                } else if (color != null) {
                    pedresEnemic++;
                    int distCentre = Math.abs(i - centre) + Math.abs(j - centre);
                    controlCentre -= (estat.getSize() - distCentre) * 10;
                    
                    if (!visitats.containsKey(p)) {
                        int mida = marcarGrupAmbMida(estat, p, color, visitats, midaGrupEnemic);
                        grupsEnemic++;
                        midaGrupEnemicMaxim = Math.max(midaGrupEnemicMaxim, mida);
                    }
                }
            }
        }
        
        int totalPedres = pedresPropi + pedresEnemic;
        int maxPedres = (int)(estat.getSize() * estat.getSize() * 0.7);
        
        if (totalPedres < maxPedres * 0.3) {
            score += controlCentre * 5;
        } else {
            score += controlCentre;
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
        
        score += avaluarCapturesPotencials(estat) * 150;
        score -= avaluarVulnerabilitat(estat, jugadorPropi) * 60;
        score += avaluarVulnerabilitat(estat, jugadorPropi.opposite()) * 60;
        
        if (pedresEnemic == 0 && pedresPropi > 0) {
            score += VICTORIA / 2;
        }
        if (pedresPropi == 0 && pedresEnemic > 0) {
            score -= VICTORIA / 2;
        }
        
        return score;
    }
    
    /**
     * Avalua quantes captures potencials hi ha disponibles.
     * 
     * <p>Aquesta funció és CLAU per competir amb jugadors agressius com
     * MalaOustiaPlayer. Valora molt positivament la capacitat de capturar
     * pedres enemigues.</p>
     * 
     * <p><b>Valoració:</b></p>
     * <ul>
     *   <li>Cada pedra capturada: +20 punts</li>
     *   <li>Captures grans (>= 3): bonus de valor² * 8</li>
     * </ul>
     * 
     * @param estat Estat del joc
     * @return Puntuació de captures potencials (0 si no és el torn del jugador)
     */
    private int avaluarCapturesPotencials(GameStatusTunned estat) {
        int captureScore = 0;
        
        if (estat.getCurrentPlayer() != jugadorPropi) {
            return 0;
        }
        
        List<Point> captureMoves = estat.getCaptureMoves();
        
        for (Point mov : captureMoves) {
            int valor = estat.estimateCaptureValue(mov);
            
            if (valor > 0) {
                captureScore += valor * 20;
                
                if (valor >= 3) {
                    captureScore += valor * valor * 8;
                }
            }
        }
        
        return captureScore;
    }
    
    /**
     * Compta quants veïns enemics únics té un grup complet.
     * 
     * <p>Aquest mètode utilitza BFS (Breadth-First Search) per recórrer tot
     * el grup començant des d'un punt inicial, i compta el nombre de pedres
     * enemigues diferents que són adjacents a qualsevol pedra del grup.</p>
     * 
     * <h3>Ús estratègic:</h3>
     * <p>El nombre de veïns enemics indica la <b>vulnerabilitat</b> d'un grup:</p>
     * <ul>
     *   <li><b>0 veïns enemics:</b> Grup aïllat (molt segur)</li>
     *   <li><b>1-2 veïns enemics:</b> Grup amb pressió lleu</li>
     *   <li><b>3+ veïns enemics:</b> Grup envoltat (molt vulnerable)</li>
     * </ul>
     * 
     * <h3>Algorisme:</h3>
     * <ol>
     *   <li>Inicialitzar conjunt de pedres visitades del grup</li>
     *   <li>Inicialitzar conjunt de veïns enemics únics</li>
     *   <li>BFS des del punt inicial:
     *     <ul>
     *       <li>Per cada pedra del grup, examinar veïns</li>
     *       <li>Si veí és del mateix color → afegir a BFS</li>
     *       <li>Si veí és enemic → afegir a conjunt de veïns enemics</li>
     *     </ul>
     *   </li>
     *   <li>Retornar mida del conjunt de veïns enemics</li>
     * </ol>
     * 
     * <p><b>Nota:</b> Utilitza conjunts (Set) per evitar comptar la mateixa
     * pedra enemiga múltiples vegades si és adjacent a diverses pedres del grup.</p>
     * 
     * @param estat Estat actual del joc
     * @param inici Qualsevol punt del grup a analitzar
     * @param player Jugador propietari del grup
     * @return Nombre de pedres enemigues úniques adjacents al grup
     */
    private int comptarVeinsEnemicsGrup(GameStatusTunned estat, Point inici, PlayerType player) {
        PlayerType enemic = player.opposite();
        Set<Point> grupVisitat = new HashSet<>();
        Set<Point> veinsEnemics = new HashSet<>();
        Stack<Point> pila = new Stack<>();
        
        pila.push(inici);
        
        while (!pila.isEmpty()) {
            Point actual = pila.pop();
            if (grupVisitat.contains(actual)) continue;
            grupVisitat.add(actual);
            
            for (Dir dir : Dir.values()) {
                Point adj = dir.add(actual);
                if (!estat.isInBounds(adj)) continue;
                
                PlayerType colorAdj = estat.getColor(adj);
                if (colorAdj == player && !grupVisitat.contains(adj)) {
                    pila.push(adj);
                } else if (colorAdj == enemic) {
                    veinsEnemics.add(adj);
                }
            }
        }
        
        return veinsEnemics.size();
    }
    
    /**
     * Avalua la vulnerabilitat dels grups d'un jugador.
     * 
     * <p>Un grup és vulnerable si:</p>
     * <ul>
     *   <li>Té mida <= 3 pedres</li>
     *   <li>Té veïns enemics adjacents</li>
     * </ul>
     * 
     * <p>Grups vulnerables tenen més probabilitat de ser capturats,
     * per tant es penalitzen en l'heurística.</p>
     * 
     * @param estat Estat del joc
     * @param player Jugador del qual avaluar vulnerabilitat
     * @return Puntuació de vulnerabilitat (més alt = més vulnerable)
     */
    private int avaluarVulnerabilitat(GameStatusTunned estat, PlayerType player) {
        int vulnerabilitat = 0;
        Set<Point> visitats = new HashSet<>();
        
        for (int i = 0; i < estat.getSquareSize(); i++) {
            for (int j = 0; j < estat.getSquareSize(); j++) {
                Point p = new Point(i, j);
                if (!estat.isInBounds(p) || visitats.contains(p)) continue;
                if (estat.getColor(p) != player) continue;
                
                int midaGrup = estat.getGroupSize(p);
                
                Stack<Point> pila = new Stack<>();
                pila.push(p);
                while (!pila.isEmpty()) {
                    Point actual = pila.pop();
                    if (visitats.contains(actual)) continue;
                    visitats.add(actual);
                    
                    for (Dir dir : Dir.values()) {
                        Point adj = dir.add(actual);
                        if (estat.isInBounds(adj) && estat.getColor(adj) == player && !visitats.contains(adj)) {
                            pila.push(adj);
                        }
                    }
                }
                
                int veinsEnemics = comptarVeinsEnemicsGrup(estat, p, player);
                
                if (midaGrup <= 3 && veinsEnemics > 0) {
                    vulnerabilitat += (4 - midaGrup) * veinsEnemics * 8;
                }
                
                if (midaGrup >= 4 && midaGrup <= 6 && veinsEnemics >= 3) {
                    vulnerabilitat += veinsEnemics * 3;
                }
            }
        }
        
        return vulnerabilitat;
    }
    
    /**
     * Marca tots els punts d'un grup i retorna la seva mida.
     * 
     * <p>Aquest mètode utilitza BFS per identificar totes les pedres connectades
     * d'un mateix color (un "grup") i simultàniament:</p>
     * <ul>
     *   <li>Marca cada pedra com a visitada (evita processar-la múltiples vegades)</li>
     *   <li>Emmagatzema la mida del grup per a cada pedra</li>
     *   <li>Retorna la mida total del grup</li>
     * </ul>
     * 
     * <h3>Ús en heurística:</h3>
     * <p>Aquest mètode és fonamental per calcular factors de la heurística:</p>
     * <ul>
     *   <li><b>Nombre de grups:</b> Comptar quants grups independents tenim</li>
     *   <li><b>Mida màxima de grup:</b> Identificar el nostre grup més fort</li>
     *   <li><b>Distribució:</b> Evitar fragmentació excessiva</li>
     * </ul>
     * 
     * <h3>Algorisme BFS:</h3>
     * <ol>
     *   <li>Inicialitzar pila amb punt inicial</li>
     *   <li>Mentre hi hagi punts a processar:
     *     <ul>
     *       <li>Extreure punt de la pila</li>
     *       <li>Marcar com a visitat</li>
     *       <li>Afegir a llista de pedres del grup</li>
     *       <li>Per cada veí del mateix color no visitat → afegir a pila</li>
     *     </ul>
     *   </li>
     *   <li>Comptar pedres totals del grup</li>
     *   <li>Assignar aquesta mida a totes les pedres del grup</li>
     * </ol>
     * 
     * <h3>Exemple:</h3>
     * <pre>
     * Tauler:
     * N N .    Grup de 5 pedres connectades
     * N N .    → Mida = 5
     * N . .    → Totes les 5 pedres tindran midaGrup[punt] = 5
     * </pre>
     * 
     * <p><b>Paràmetres modificats:</b> Els mapes 'visitats' i 'midaGrup' són
     * modificats durant l'execució. 'visitats' s'actualitza amb tots els punts
     * del grup, i 'midaGrup' s'actualitza amb la mida per a cada punt.</p>
     * 
     * @param estat Estat actual del joc
     * @param inici Punt inicial del grup (qualsevol pedra del grup)
     * @param color Jugador propietari del grup
     * @param visitats Mapa de punts ja visitats (s'actualitza durant l'execució)
     * @param midaGrup Mapa que emmagatzema la mida del grup per cada punt (s'actualitza)
     * @return Mida total del grup (nombre de pedres connectades)
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
     * Entrada de la taula de transposició.
     * 
     * <p>Emmagatzema el valor heurístic d'un estat ja avaluat juntament amb
     * la profunditat a la qual es va calcular. Això permet reutilitzar
     * càlculs i accelerar significativament la cerca.</p>
     * 
     * <p><b>Nota:</b> Només s'utilitza l'entrada si la profunditat guardada
     * és >= que la profunditat actual de cerca, garantint que el valor
     * és prou precís.</p>
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
