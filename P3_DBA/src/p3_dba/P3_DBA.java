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
        
        app.launchAgent("45925763F-Listener", CieListener.class);
        
        app.shutDown();        
    }
    
}
