/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package p3_dba;

import AppBoot.ConsoleBoot;

public class P3_DBA {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("P3", args);
        app.selectConnection();
        
        app.launchAgent("CieAwacs", Awacs.class);
        //app.launchAgent("CieCartographer", Cartographer.class);
        app.launchAgent("CieListener", CieListener.class);
        app.launchAgent("CieDroneHQ1", CieDroneHQ.class);
        app.launchAgent("CieDroneHQ2", CieDroneHQ.class);
        app.launchAgent("CieDroneHQ3", CieDroneHQ.class);
        app.launchAgent("CieDroneDLX", CieDroneDLX.class);
        
        app.shutDown();        
    }
    
}
