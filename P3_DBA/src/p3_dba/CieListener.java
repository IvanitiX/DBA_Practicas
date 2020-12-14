/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package p3_dba;

import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
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
    private int mapWidth, mapHeight;
    private int map[][];
    private ArrayList wallsList;
    private JsonValue mapJsonFormat;
    private ACLMessage identityManagerSender, identityManagerReceiver;
    private ACLMessage worldManagerSender, worldManagerReceiver;
    private ACLMessage allRescuersSender, allRescuersReceiver;
    
    @Override
    public void setup(){
        super.setup();
        
        worldName = "Playground1";
        status = "login";
        
        identityManagerSender = new ACLMessage();
        worldManagerSender = new ACLMessage();
        allRescuersSender = new ACLMessage();
        
        wallsList = new ArrayList<>();
        
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
            case "login":
                login();
            break;
            case "sendInitialInfo":
                sendInitialInfo();
            break;
            case "createWallsList":
                createWallsList();
            break;
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
    
    private void createWallsList(){
        // Creación de la lista de paredes
        // [MODIF] ....
        
        // Enviamos la lista de paredes a los drones
        String content = this.getWallsListMessage().toString();
        allRescuersSender.setContent(content);
        
        this.send(allRescuersSender);
        
        // La última tarea a realizar consiste en esperar de forma bloqueante
        // una confirmación de desbloqueo de todos los drones
        MessageTemplate template1 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneHQ1", AID.ISLOCALNAME)));
        
        allRescuersReceiver = this.blockingReceive(template1);
        
        MessageTemplate template2 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneHQ2", AID.ISLOCALNAME)));
        
        allRescuersReceiver = this.blockingReceive(template2);
        
        MessageTemplate template3 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneHQ3", AID.ISLOCALNAME)));
        
        allRescuersReceiver = this.blockingReceive(template3);
        
        MessageTemplate template4 = MessageTemplate.and(MessageTemplate.MatchContent("loggingOut"),
                MessageTemplate.MatchSender(new AID("CieDroneDLX", AID.ISLOCALNAME)));
        
        allRescuersReceiver = this.blockingReceive(template4);
        
        status = "logoutWM";
    }
    
    private JsonObject getDeploymentMessage (){
        JsonObject loginInfo = new JsonObject();
        
        loginInfo.add("problem", worldName);
        
        return loginInfo;
    }
    
    private JsonObject getInitialInfoMessage(){
        JsonObject initialInfo = new JsonObject();
        
        initialInfo.add("convID", convID);
        initialInfo.add("map", mapJsonFormat);
        
        return initialInfo;
    }
    
    private JsonObject getWallsListMessage(){
        JsonObject wallsListInfo = new JsonObject();
        JsonArray wallsListJsonFormat = new JsonArray();
        
        for (int i=0; i<wallsList.size(); i++){
            wallsListJsonFormat.add((int) wallsList.get(i));
        }
            
        wallsListInfo.add("wallsList", wallsListJsonFormat);
        
        return wallsListInfo;
    }
    
    public void login(){
        // ***** IDENTITY MANAGER *****
        
        // Suscripción a Identity Manager
        identityManagerSender.setSender(this.getAID());
        identityManagerSender.addReceiver(new AID(IDENTITY_MANAGER,AID.ISLOCALNAME));
        identityManagerSender.setPerformative(ACLMessage.SUBSCRIBE);
        identityManagerSender.setProtocol("ANALYTICS");
        identityManagerSender.setEncoding(_myCardID.getCardID());
        identityManagerSender.setContent("");
        
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
        getYP.setContent("");

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

        // ***** WOLRD MANAGER *****
        
        // Suscripción al World Manager
        worldManagerSender.setSender(this.getAID());
        worldManagerSender.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        worldManagerSender.setPerformative(ACLMessage.SUBSCRIBE);
        worldManagerSender.setProtocol("ANALYTICS");
        //worldManagerSender.setEncoding(_myCardID.getCardID());
        String content = this.getDeploymentMessage().toString();
        worldManagerSender.setContent(content);
        
        worldManagerReceiver = this.blockingReceive();
        
        // Si la respuesta es REFUSE o NOT_UNDERSTOOD, hacemos logout en la plataforma
        if(worldManagerReceiver.getPerformative() != ACLMessage.INFORM){
            
            status = "logoutIM";
            return;
        }
        
        String loginInfo = worldManagerReceiver.getContent();
       
        // Esperando respuesta
        JsonObject parsedLoginInfo;
        parsedLoginInfo = Json.parse(loginInfo).asObject();
        
        if (parsedLoginInfo.get("result").asString().equals("ok")){
            
            // Inicializamos matriz del mapa
            mapJsonFormat = parsedLoginInfo.get("map");
            mapWidth = mapJsonFormat.asArray().size();
            mapHeight = mapJsonFormat.asArray().get(0).asArray().size();
            
            map = new int[mapWidth][mapHeight];
            
            // Obtenemos información del mapa
            for (int i=0; i<mapWidth; i++){
                for (int j=0; j<mapHeight; j++){
                    map[i][j] = mapJsonFormat.asArray().get(i).asArray().get(j).asInt();
                }
            }
            convID = worldManagerReceiver.getConversationId();
            
            status = "sendInitialInfo";
        }
        else{
            status = "logoutIM";
        }
    }
    
    
    private void logoutIM(){
        
        // Cancelamos suscripción a Identity Manager
        identityManagerSender = identityManagerReceiver.createReply();
        identityManagerSender.setPerformative(ACLMessage.CANCEL);
        identityManagerSender.setContent("");
        this.send(identityManagerSender);
        
        status = "exit";
    }
    
    private void logoutWM(){
        
        // Cancelamos suscripción a World Manager
        worldManagerSender = worldManagerReceiver.createReply();
        worldManagerSender.setPerformative(ACLMessage.CANCEL);
        worldManagerSender.setContent("");
        this.send(worldManagerSender);
        
        status = "logoutIM";
    }
    
    private void sendInitialInfo(){
        
        // Enviamos mapa e id de la conversación a todos los drones
        allRescuersSender.setSender(this.getAID());
        allRescuersSender.addReceiver(new AID("CieDroneHQ1", AID.ISLOCALNAME));
        allRescuersSender.addReceiver(new AID("CieDroneHQ2", AID.ISLOCALNAME));
        allRescuersSender.addReceiver(new AID("CieDroneHQ3", AID.ISLOCALNAME));
        allRescuersSender.addReceiver(new AID("CieDroneDLX", AID.ISLOCALNAME));
        allRescuersSender.setPerformative(ACLMessage.INFORM);
        allRescuersSender.setConversationId("INITIAL_INFO");
        String content = this.getInitialInfoMessage().toString();
        allRescuersSender.setContent(content);
       
        this.send(allRescuersSender);
        
        status = "createWallsList";
    }
    
}
