/**
 * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
 */
package p2_dba;

import AppBoot.ConsoleBoot;

public class P2_DBA {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("P2", args);
        app.selectConnection();
        
        app.launchAgent("45923405H", CieAutomotiveDrone.class);
        app.shutDown();        
    }
    
}
