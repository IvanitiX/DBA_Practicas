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
public class CieDroneHQ extends IntegratedAgent{
    private static String IDENTITY_MANAGER = "Sphinx" ;
    private String status;
    private YellowPages pags;
    private String worldManager;
    private String convID;
    private int mapWidth, mapHeight, mapSize;
    //private int map[][];
    private int map[];
    private String marketplaces[];
    private String catalogues[];
    private int ncoins;
    private String coins[]; 
    private ArrayList wallsList;
    private ACLMessage identityManagerSender, identityManagerReceiver;
    private ACLMessage marketplaceSender, marketplaceReceiver;
    private ACLMessage worldManagerSender, worldManagerReceiver;
    private ACLMessage listenerSender, listenerReceiver;
    private ACLMessage allRescuersSender, allRescuersReceiver;
    
    @Override
    public void setup(){
        super.setup();
        
        status = "login";
        
        listenerSender = new ACLMessage();
        identityManagerSender = new ACLMessage();
        marketplaceSender = new ACLMessage();
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
            case "market":
                market();
            break;
            case "logout":
                logout();
            break;
            case "exit":
                _exitRequested = true;
            break;
        }
    } 
    
    private JsonObject getDeploymentMessage (){
        JsonObject loginInfo = new JsonObject();
        
        loginInfo.add("type", "RESCUER");
        
        return loginInfo;
    }
    
    public void login(){
        System.out.println("Login del agente " + this.getAID().getLocalName());
        
        // Esperamos inicialmente el mensaje de CieListener con el mapa y el id de la conversación
        MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchProtocol("INITIALIZE"), 
                MessageTemplate.MatchSender(new AID("CieListener", AID.ISLOCALNAME)));
        
        listenerReceiver = this.blockingReceive(template);
        
        System.out.println("Agente " + this.getAID().getLocalName() + " ha recibido el mensaje del listener");
        
        String initialInfo = listenerReceiver.getContent();
        JsonObject parsedInitialInfo = Json.parse(initialInfo).asObject();
        
        convID = parsedInitialInfo.get("convID").asString();
        
        // Calculamos dimensiones del mapa 
        //mapWidth = parsedInitialInfo.get("map").asArray().size();
        //mapHeight = parsedInitialInfo.get("map").asArray().get(0).asArray().size();
        mapSize = parsedInitialInfo.get("map").asArray().size();
        //map = new int[mapWidth][mapHeight];

        // Obtenemos información del mapa
        /*for (int i=0; i<mapWidth; i++){
            for (int j=0; j<mapHeight; j++){
                map[i][j] = parsedInitialInfo.get("map").asArray().get(i).asArray().get(j).asInt();
            }
        }*/
        /*for (int i=0; i<mapSize; i++){
            map[i] = parsedInitialInfo.get("map").asArray().get(i).asInt();
        }*/
        System.out.println("Mapa: " + parsedInitialInfo.get("map").asArray());
        
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
        
        template = MessageTemplate.MatchInReplyTo(key);
        
        this.send(identityManagerSender);
        
        identityManagerReceiver = this.blockingReceive(template);
        
        //Si la respuesta es REFUSE o NOT_UNDERSTOOD, salimos
        if (identityManagerReceiver.getPerformative() != ACLMessage.CONFIRM && 
                identityManagerReceiver.getPerformative() != ACLMessage.INFORM){
            
            status = "exit";
            return;
        }
        
        
        System.out.println("Agente " + this.getAID().getLocalName() + " suscrito a IM");
        
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
            
            status = "logout";
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
        worldManagerSender.setProtocol("REGULAR");
        worldManagerSender.setConversationId(convID);
        //worldManagerSender.setEncoding(_myCardID.getCardID());
        String content = this.getDeploymentMessage().toString();
        worldManagerSender.setContent(content);
        this.send(worldManagerSender);
        
        worldManagerReceiver = this.blockingReceive();
        
        // Si la respuesta es REFUSE o NOT_UNDERSTOOD, hacemos logout en la plataforma
        if(worldManagerReceiver.getPerformative() != ACLMessage.INFORM){
            
            status = "logout";
            return;
        }
        
        String loginInfo = worldManagerReceiver.getContent();
        System.out.println("Agente " + this.getAID().getLocalName() + " ha obtenido respuesta del WM: " + loginInfo);
       
        // Esperando respuesta
        JsonObject parsedLoginInfo;
        parsedLoginInfo = Json.parse(loginInfo).asObject();
        
        if (parsedLoginInfo.get("result").asString().equals("ok")){
            ncoins = parsedLoginInfo.get("coins").asArray().size();
            
            // Obtenemos recursos
            coins = new String[ncoins];
            
            for (int i=0; i<ncoins; i++){
                coins[i] = parsedLoginInfo.get("coins").asArray().get(i).asString();
            }
            
            status = "market";
        }
        else{
            status = "logout";
        }
    }
    
    
    private void logout(){
        // Cancelamos suscripción a Identity Manager
        identityManagerSender = identityManagerReceiver.createReply();
        identityManagerSender.setPerformative(ACLMessage.CANCEL);
        identityManagerSender.setContent("");
        this.send(identityManagerSender);
        
        // Informamos al listener para que cancele la suscripción
        listenerSender.addReceiver(new AID("CieListener", AID.ISLOCALNAME));
        listenerSender.setPerformative(ACLMessage.INFORM);
        listenerSender.setContent("loggingOut");
        this.send(listenerSender);
        
        status = "exit";
    }
    
    private void market(){
        // Obtenemos los nombres de los marketplaces con los que queremos comunicarnos
        marketplaces = new String[pags.queryProvidersofService("shop@"+convID).toArray().length];
        
        for (int i=0; i<marketplaces.length; i++){
            marketplaces[i] = (String) pags.queryProvidersofService("shop@"+convID).toArray()[i];
        }
        
        /*
        // Enviamos una solicitud a todos los marketplaces para obtener su catálogo
        
        marketplaceSender.setSender(this.getAID());
        marketplaceSender.setPerformative(ACLMessage.QUERY_REF);
        marketplaceSender.setConversationId(convID);
        marketplaceSender.setReplyWith("CIE_CATALOGUE");
        marketplaceSender.setProtocol("REGULAR");
        marketplaceSender.setContent("");
        for (int i=0; i<marketplaces.length; i++){
            marketplaceSender.addReceiver(new AID (marketplaces[i], AID.ISLOCALNAME));
        }
        this.send(marketplaceSender);
        
        // Recibimos respuesta de cada marketplace
        
        MessageTemplate template;
        catalogues = new String[marketplaces.length];
        
        for (int i=0; i<marketplaces.length; i++){
            template = MessageTemplate.and(MessageTemplate.MatchInReplyTo("CIE_CATALOGUE"), 
                MessageTemplate.MatchSender(new AID(marketplaces[0], AID.ISLOCALNAME)));
            
            marketplaceReceiver = this.blockingReceive(template);
            
            if (marketplaceReceiver.getPerformative() != ACLMessage.INFORM){
                Info ("Fallo al obtener el catálogo de " + marketplaces[i]);
            }
            else{
                catalogues[i] = Json.parse(marketplaceReceiver.getContent()).asObject().get("details").asString();
            }
        }
        
        // Falta buscar dentro de cada catálogo el contenido que más nos interesa
        // y realizar una solicitud al mismo
        // Nota: si todos los catálogos están vacíos, nos deslogueamos del sistema (status=logout)
        
        */
        
        status = "logout";
    }
}
