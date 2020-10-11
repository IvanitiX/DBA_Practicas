/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package p1_dba;

import AppBoot.ConsoleBoot;

public class P1_DBA {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("HACKATHON", args);
        app.selectConnection();
        
        app.launchAgent("09079064K", MyHackathon.class);
        app.shutDown();        
    }
    
}
