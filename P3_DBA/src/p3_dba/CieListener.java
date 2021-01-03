/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package p3_dba;

import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import Map2D.Map2DGrayscale;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;

/**
 *
 * @author ivan
 */
public class CieListener extends IntegratedAgent{
    private static String IDENTITY_MANAGER = "Sphinx" ;
    private String status;
    private YellowPages pags;
    private String worldManager;
    private String worldName;
    private String convID;
    //private ArrayList wallsList;
    private Map2DGrayscale map;
    private JsonObject mapJsonFormat;
    private ACLMessage identityManagerSender, identityManagerReceiver;
    private ACLMessage worldManagerSender, worldManagerReceiver;
    private ACLMessage droneSender, droneReceiver;
    
    @Override
    public void setup(){
        super.setup();
        
        worldName = "Playground1";
        status = "deploy";
        
        identityManagerSender = new ACLMessage();
        worldManagerSender = new ACLMessage();
        droneSender = new ACLMessage();
        
        map = new Map2DGrayscale();
        
        //wallsList = new ArrayList<>();
        
        pags = new YellowPages();
        _exitRequested = false ;
    }
    
    @Override
    public void takeDown() {
        
        super.takeDown();
    }
    
    @Override
    public void plainExecute() {
        switch(status){
            case "deploy":
                deploy();
            break;
            case "sendInitialInfo":
                sendInitialInfo();
            break;
            //case "createWallsList":
            //    createWallsList();
            //break;
            case "logoutWM":
                logoutWM();
            break;
            case "logoutIM":
                logoutIM();
            break;
            case "exit":
                _exitRequested = true;
            break;
        }
    } 
    
    public void deploy(){
        // ***** IDENTITY MANAGER *****
        
        // Suscripción a Identity Manager
        identityManagerSender.setSender(this.getAID());
        identityManagerSender.addReceiver(new AID(IDENTITY_MANAGER,AID.ISLOCALNAME));
        identityManagerSender.setPerformative(ACLMessage.SUBSCRIBE);
        identityManagerSender.setProtocol("ANALYTICS");
        identityManagerSender.setEncoding(_myCardID.getCardID());
        identityManagerSender.setContent("{}");
        
        // Creamos template para detectar en el futuro aquellas respuestas que referencien a la clave key
        String key = "L_IM";
        identityManagerSender.setReplyWith(key);
        
        MessageTemplate template = MessageTemplate.MatchInReplyTo(key);
        
        this.send(identityManagerSender);
        
        identityManagerReceiver = this.blockingReceive(template);
        
        //Si la respuesta es REFUSE o NOT_UNDERSTOOD, salimos
        if (identityManagerReceiver.getPerformative() != ACLMessage.CONFIRM && 
                identityManagerReceiver.getPerformative() != ACLMessage.INFORM){
            
            status = "exit";
            return;
        }
        
        // Creamos respesta al IM para obtener páginas amarillas
        ACLMessage getYP = identityManagerReceiver.createReply();
        getYP.setPerformative(ACLMessage.QUERY_REF);
        getYP.setContent("{}");

        getYP.setReplyWith(key);
        
        //template = MessageTemplate.MatchInReplyTo(key);

        this.send(getYP);

        identityManagerReceiver = this.blockingReceive(template);

        //Si la respuesta es REFUSE o NOT_UNDERSTOOD, hacemos logout en la plataforma
        if(identityManagerReceiver.getPerformative() != ACLMessage.CONFIRM && 
                identityManagerReceiver.getPerformative() != ACLMessage.INFORM){
            
            status = "logoutIM";
            return;
        }
        
        // Actualizamos páginas amarillas a partir de la respuesta del IM
        pags.updateYellowPages(identityManagerReceiver) ;
        System.out.println(pags.prettyPrint());
        worldManager = pags.queryProvidersofService("Analytics group Cie Automotive").toArray()[0].toString();
        Info("El World Manager es " + worldManager);

        // ***** WORLD MANAGER *****
        
        // Suscripción al World Manager
        worldManagerSender.setSender(this.getAID());
        worldManagerSender.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        worldManagerSender.setPerformative(ACLMessage.SUBSCRIBE);
        worldManagerSender.setProtocol("ANALYTICS");
        //worldManagerSender.setEncoding(_myCardID.getCardID());
        String content = this.getDeploymentMessage().toString();
        worldManagerSender.setContent(content);
        this.send(worldManagerSender);
        
        worldManagerReceiver = this.blockingReceive();
        
        // Si la respuesta es REFUSE o NOT_UNDERSTOOD, hacemos logout en la plataforma
        if(worldManagerReceiver.getPerformative() != ACLMessage.INFORM){
            status = "logoutIM";
            return;
        }
        System.out.println("Listener logueado con éxito en WM!");
        
        String deployInfo = worldManagerReceiver.getContent();
        Info(deployInfo);
       
        // Esperando respuesta
        JsonObject parsedDeployInfo;
        parsedDeployInfo = Json.parse(deployInfo).asObject();
        
        if (parsedDeployInfo.get("result").asString().equals("ok")){
            
            // Inicializamos matriz del mapa
            mapJsonFormat = parsedDeployInfo.get("map").asObject();
            
            // Generamos el mapa
            if (!map.fromJson(mapJsonFormat)) {
                System.out.println("Problema al generar el mapa");
                status = "logoutIM";
                return;
            }
            
            convID = worldManagerReceiver.getConversationId();
            
            ACLMessage awacsSender = new ACLMessage();
            awacsSender.setSender(this.getAID());
            awacsSender.addReceiver(new AID("CieAwacs", AID.ISLOCALNAME));
            awacsSender.setPerformative(ACLMessage.QUERY_IF);
            awacsSender.setProtocol("ANALYTICS");
            //worldManagerSender.setEncoding(_myCardID.getCardID());
            awacsSender.setContent("");
            awacsSender.setConversationId(convID);
            this.send(awacsSender);
            
            status = "sendInitialInfo";
        }
        else{
            status = "logoutIM";
        }
    }
    
    private JsonObject getDeploymentMessage (){
        JsonObject deployInfo = new JsonObject();
        
        deployInfo.add("problem", worldName);
        
        return deployInfo;
    }
    
    private JsonObject getInitialInfoMessage(int posx, int posy){
        JsonObject initialInfo = new JsonObject();
        
        initialInfo.add("convID", convID);
        initialInfo.add("map", mapJsonFormat);
        initialInfo.add("posx", posx);
        initialInfo.add("posy", posy);
        
        return initialInfo;
    }
    
    private void logoutIM(){
        
        // Cancelamos suscripción a Identity Manager
        identityManagerSender = identityManagerReceiver.createReply();
        identityManagerSender.setPerformative(ACLMessage.CANCEL);
        identityManagerSender.setContent("{}");
        this.send(identityManagerSender);
        
        status = "exit";
    }
    
    private void logoutWM(){
        // Antes de desloguearse, debe esperar de forma bloqueante una 
        //confirmación de desbloqueo de todos los drones
        MessageTemplate template1 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneHQ1", AID.ISLOCALNAME)));
        
        droneReceiver = this.blockingReceive(template1);
        
        System.out.println("Listener ha recibido mensaje de CieDroneHQ1");
        
        MessageTemplate template2 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneHQ2", AID.ISLOCALNAME)));
        
        droneReceiver = this.blockingReceive(template2);
        
        System.out.println("Listener ha recibido mensaje de CieDroneHQ2");
        
        MessageTemplate template3 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneHQ3", AID.ISLOCALNAME)));
        
        droneReceiver = this.blockingReceive(template3);
        
        System.out.println("Listener ha recibido mensaje de CieDroneHQ3");
        
        
        MessageTemplate template4 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneDLX", AID.ISLOCALNAME)));
        
        droneReceiver = this.blockingReceive(template4);
        
        System.out.println("Listener ha recibido mensaje de CieDroneDLX");
        
        
        // Cancelamos suscripción a World Manager
        worldManagerSender = worldManagerReceiver.createReply();
        worldManagerSender.setPerformative(ACLMessage.CANCEL);
        worldManagerSender.setContent("{}");
        this.send(worldManagerSender);
        
        status = "logoutIM";
    }
    
    private void sendInitialInfo(){
        
        // Enviamos a cada drone el id de la conversación, el mapa y su posición
        // inicial en el mundo
        droneSender.setSender(this.getAID());
        droneSender.setPerformative(ACLMessage.INFORM);
        droneSender.setProtocol("INITIALIZE");
        
        // Enviamos info al drone CieDroneHQ1
        droneSender.addReceiver(new AID("CieDroneHQ1", AID.ISLOCALNAME));
        String content = this.getInitialInfoMessage(map.getWidth()/4, map.getHeight()/4).toString();
        droneSender.setContent(content);
        this.send(droneSender);
        
        // Enviamos info al drone CieDroneHQ2
        droneSender.removeReceiver(new AID("CieDroneHQ1", AID.ISLOCALNAME));
        droneSender.addReceiver(new AID("CieDroneHQ2", AID.ISLOCALNAME));
        content = this.getInitialInfoMessage(3*map.getWidth()/4, map.getHeight()/4).toString();
        droneSender.setContent(content);
        this.send(droneSender);
        
        // Enviamos info al drone CieDroneHQ3
        droneSender.removeReceiver(new AID("CieDroneHQ2", AID.ISLOCALNAME));
        droneSender.addReceiver(new AID("CieDroneHQ3", AID.ISLOCALNAME));
        content = this.getInitialInfoMessage(3*map.getWidth()/4, 3*map.getHeight()/4).toString();
        droneSender.setContent(content);
        this.send(droneSender);
        
        // Enviamos info al drone CieDroneDLX
        
        droneSender.removeReceiver(new AID("CieDroneHQ3", AID.ISLOCALNAME));
        droneSender.addReceiver(new AID("CieDroneDLX", AID.ISLOCALNAME));
        content = this.getInitialInfoMessage(map.getWidth()/4, 3*map.getHeight()/4).toString();
        droneSender.setContent(content);
        this.send(droneSender);
        
        status = "logoutWM";
    }
    
    
    /*
    NOTA: Si finalmente usamos el algoritmo Greedy, estos dos métodos no son necesarios
    
    private void createWallsList(){
        // Creación de la lista de paredes
        // [MODIF] ....
        
        // Enviamos la lista de paredes a los drones
        String content = this.getWallsListMessage().toString();
        droneSender.setContent(content);
        droneSender.setProtocol("REGULAR");
        
        this.send(droneSender);
        
        status = "logoutWM";
    }
    */
    /*
    private JsonObject getWallsListMessage(){
        JsonObject wallsListInfo = new JsonObject();
        JsonArray wallsListJsonFormat = new JsonArray();
        
        for (int i=0; i<wallsList.size(); i++){
            wallsListJsonFormat.add((int) wallsList.get(i));
        }
            
        wallsListInfo.add("wallsList", wallsListJsonFormat);
        
        return wallsListInfo;
    }*/
}
