/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package p2_dba;

import AppBoot.ConsoleBoot;

public class P2_DBA {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("P2", args);
        app.selectConnection();
        
        app.launchAgent("45923405Hv2", CieAutomotiveDrone.class);
        app.shutDown();        
    }
    
}
