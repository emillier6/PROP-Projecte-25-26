
package edu.upc.epsevg.prop.oust;

import java.awt.Point;

/**
 *
 * @author Usuari
 */
public class GameStatusTunned extends GameStatus{
    
    public GameStatusTunned(GameStatus gs) {
        super(gs);
    }

    @Override
    public void placeStone(Point point) {
        super.placeStone(point); 
        //...
        // les vostes cosetes...
        
        Dir d =Dir.DOWN_L;
    }
    
    
}
