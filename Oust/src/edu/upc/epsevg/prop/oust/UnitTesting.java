package edu.upc.epsevg.prop.oust;


import edu.upc.epsevg.prop.oust.players.MillierAranda.PlayerMiniMax;
import java.awt.Point;
import java.util.List;
/**
 *
 * @author bernat
 */
public class UnitTesting {
    
    
    
    public static void main(String[] args) {
        //game1();
        //game2();
        game3();
    }

    private static void game1() {
        GameStatus gs = new GameStatus(4);
        System.out.println(""+gs);
        
 
        
        gs.placeStone(new Point(1,2));System.out.println(""+gs);
        gs.placeStone(new Point(5,3));System.out.println(""+gs);
        gs.placeStone(new Point(4,2));System.out.println(""+gs);
        gs.placeStone(new Point(4,1));System.out.println(""+gs);
        gs.placeStone(new Point(3,2));System.out.println(""+gs);
    }
    
    private static void game2() {
        GameStatus gs = new GameStatus(4);
        System.out.println(""+gs);
        
        gs.placeStone(new Point(1,2));System.out.println(""+gs);
        gs.placeStone(new Point(5,3));System.out.println(""+gs);
        gs.placeStone(new Point(4,2));System.out.println(""+gs);
        gs.placeStone(new Point(4,1));System.out.println(""+gs);
        gs.placeStone(new Point(5,4));System.out.println(""+gs);
        gs.placeStone(new Point(5,2));System.out.println(""+gs);
        gs.placeStone(new Point(5,6));System.out.println(""+gs);
        gs.placeStone(new Point(4,4));System.out.println(""+gs);
        gs.placeStone(new Point(3,4));System.out.println(""+gs);
        gs.placeStone(new Point(6,4));System.out.println(""+gs);
        gs.placeStone(new Point(0,0));System.out.println(""+gs);
        gs.placeStone(new Point(5,5));System.out.println(""+gs);
        gs.placeStone(new Point(3,1));System.out.println(""+gs);
        
        gs.placeStone(new Point(4,2));System.out.println(""+gs);
        gs.placeStone(new Point(5,4));System.out.println(""+gs);
        gs.placeStone(new Point(1,1));System.out.println(""+gs);

    }
    
    private static void game3() {
        GameStatus gs = new GameStatus(4);

        // Seqüència d'exemple (igual estil que UnitTesting.game2)
        // L'objectiu és arribar a una posició on el següent jugador pugui capturar
        gs.placeStone(new Point(1,2));
        gs.placeStone(new Point(5,3));
        gs.placeStone(new Point(4,2));
        gs.placeStone(new Point(4,1));
        gs.placeStone(new Point(5,4));
        gs.placeStone(new Point(5,2));
        gs.placeStone(new Point(5,6));
        gs.placeStone(new Point(4,4));
        gs.placeStone(new Point(3,4));
        gs.placeStone(new Point(6,4));
        gs.placeStone(new Point(0,0));
        gs.placeStone(new Point(5,5));
        gs.placeStone(new Point(3,1));

        System.out.println("=== Estat abans del move() ===");
        System.out.println(gs);

        PlayerMiniMax p = new PlayerMiniMax(1);

        // IMPORTANT: li passem una còpia, igual que fa el Board/HeadlessGame
        PlayerMove m = p.move(new GameStatus(gs));
        if (m == null) throw new RuntimeException("El jugador ha retornat null (PlayerMove).");
        
        List<Point> path = m.getPoints();
        if (path == null) throw new RuntimeException("El PlayerMove té points=null. Ha de ser una llista (com a mínim buida).");
        if (path.isEmpty()) throw new RuntimeException("El jugador ha retornat path buit (pass). En aquesta posició potser no és acceptable.");

        System.out.println("=== Path retornat pel player ===");
        System.out.println(path);
        System.out.println("Longitud path = " + (path == null ? 0 : path.size()));

        // Validació “a l’estil Board”: aplicar la seqüència i comprovar que el torn acaba bé
        GameStatus sim = new GameStatus(gs);
        PlayerType cp = sim.getCurrentPlayer();
        
        for (Point pt : path) {
            if (cp != sim.getCurrentPlayer()) {
                throw new RuntimeException("Seqüència invàlida: el torn ha canviat abans d’acabar.");
            }
            sim.placeStone(pt);
        }

        if (!sim.isGameOver() && cp == sim.getCurrentPlayer()) {
            throw new RuntimeException("Seqüència invàlida: el torn NO ha acabat (falta non-capturing al final).");
        }

        System.out.println("✅ Seqüència vàlida segons la regla del framework.");
        System.out.println("=== Estat després d’aplicar el path ===");
        System.out.println(sim);
    }
    
}
