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
import java.util.Collections;
import java.util.List;

/**
 *
 * @author ivan
 */
public class CieDroneDLX extends IntegratedAgent{
    private static String IDENTITY_MANAGER = "Sphinx" ;
    private String worldManager;
    private String status;
    private YellowPages pags;
    private String convID;
    private int posx, posy;
    private Map2DGrayscale map;
    //private ArrayList wallsList;
    
    private List<String> coins; 
    private String marketplaces[];
    private ArrayList<String> catalogueAngular, catalogueDistance, catalogueEnergy;
    
    private String infoAngular, infoDistance;
    private List<String> infoEnergy;
    
    private ACLMessage identityManagerSender, identityManagerReceiver;
    private ACLMessage marketplaceSender, marketplaceReceiver;
    private ACLMessage worldManagerSender, worldManagerReceiver;
    private ACLMessage listenerSender, listenerReceiver;
    
    @Override
    public void setup(){
        super.setup();
        
        status = "deploy";
        
        listenerSender = new ACLMessage();
        identityManagerSender = new ACLMessage();
        marketplaceSender = new ACLMessage();
        worldManagerSender = new ACLMessage();
        
        map = new Map2DGrayscale();
        
        //wallsList = new ArrayList<>();
        coins = new ArrayList<>();
        catalogueAngular = new ArrayList<>();
        catalogueDistance = new ArrayList<>();
        catalogueEnergy = new ArrayList<>();
        infoEnergy = new ArrayList<>();
        
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
            case "market":
                market();
            break;
            case "login":
                login();
            break;
            case "readSensors":
                readSensors();
            break;
            case "fase1":
                status = "readSensors";
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
        JsonObject deployInfo = new JsonObject();
        
        deployInfo.add("type", "RESCUER");
        
        return deployInfo;
    }
    
    private JsonObject getExtraCoinsMessage(){
        JsonObject extraCoinsInfo = new JsonObject();
        JsonArray coinsJsonFormat = new JsonArray();
        
        for (int i=0; i<coins.size(); i++){
            coinsJsonFormat.add(coins.get(0));
        }
            
        extraCoinsInfo.add("coins", coinsJsonFormat);
        
        return extraCoinsInfo;
    }
    
    private JsonObject getLoginInfo(){
        JsonObject loginInfo = new JsonObject();
        JsonArray sensorListJsonFormat = new JsonArray();
        
        loginInfo.add("operation", "login");
        loginInfo.add("posx", posx);
        loginInfo.add("posy", posy);
        
        sensorListJsonFormat.add(infoAngular);
        sensorListJsonFormat.add(infoDistance);
        
        for (int i=0; i<infoEnergy.size(); i++){
            sensorListJsonFormat.add(infoEnergy.get(i));
        }
            
        loginInfo.add("attach", sensorListJsonFormat);
        
        return loginInfo;
    }
    
    private JsonObject getProductListMessage(String info){
        JsonObject purchaseInfo = new JsonObject();
        String [] splitInfo = info.split("@");
        JsonArray coinsJsonFormat = new JsonArray();
        
        purchaseInfo.add("operation", "buy");
        purchaseInfo.add("reference", splitInfo[1]);
        
        if (coins.size() < Integer.parseInt(splitInfo[0]))
            return null;
           
        
        for (int i=0; i<Integer.parseInt(splitInfo[0]); i++){
            coinsJsonFormat.add(coins.get(i));
        }
            
        purchaseInfo.add("payment", coinsJsonFormat);
        
        return purchaseInfo;
    }
    
    public void deploy(){
        // Esperamos inicialmente el mensaje de CieListener con el mapa, posición e id de la conversación
        MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchProtocol("INITIALIZE"), 
                MessageTemplate.MatchSender(new AID("CieListener", AID.ISLOCALNAME)));
        
        listenerReceiver = this.blockingReceive(template);
        
        System.out.println("Agente " + this.getAID().getLocalName() + " ha recibido el mensaje del listener");
        
        String initialInfo = listenerReceiver.getContent();
        JsonObject parsedInitialInfo = Json.parse(initialInfo).asObject();
        
        convID = parsedInitialInfo.get("convID").asString();
        posx = parsedInitialInfo.get("posx").asInt();
        posy = parsedInitialInfo.get("posy").asInt();
        
        // Obtenemos el mapa
        JsonObject mapJsonFormat = parsedInitialInfo.get("map").asObject();
            
        if (!map.fromJson(mapJsonFormat)) {
            System.out.println("Problema al generar el mapa");
            status = "logoutIM";
            return;
        }
        
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
        getYP.setContent("{}");

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

        
        // ***** WORLD MANAGER *****
        
        // Suscripción al World Manager
        worldManagerSender.setSender(this.getAID());
        worldManagerSender.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        worldManagerSender.setPerformative(ACLMessage.SUBSCRIBE);
        worldManagerSender.setProtocol("REGULAR");
        worldManagerSender.setConversationId(convID);
        worldManagerSender.setEncoding(_myCardID.getCardID());
        String content = this.getDeploymentMessage().toString();
        worldManagerSender.setContent(content);
        this.send(worldManagerSender);
        
        worldManagerReceiver = this.blockingReceive();
        
        // Si la respuesta es REFUSE o NOT_UNDERSTOOD, hacemos logout en la plataforma
        if(worldManagerReceiver.getPerformative() != ACLMessage.INFORM){
            
            status = "logout";
            return;
        }
        
        String deployInfo = worldManagerReceiver.getContent();
        System.out.println("Agente " + this.getAID().getLocalName() + " ha obtenido respuesta del WM: " + deployInfo);
       
        // Esperando respuesta
        JsonObject parsedDeployInfo;
        parsedDeployInfo = Json.parse(deployInfo).asObject();
        
        if (parsedDeployInfo.get("result").asString().equals("ok")){
            
            // Obtenemos recursos
            for (int i=0; i<parsedDeployInfo.get("coins").asArray().size(); i++){
                coins.add(parsedDeployInfo.get("coins").asArray().get(i).asString());
            }
            
            status = "market";
        }
        else{
            status = "logout";
        }
    }
    
    private void login(){
        // Nos logueamos en el Identity Manager
        // Nota: MÉTODO NO DEPURADO
        
        worldManagerSender = worldManagerReceiver.createReply();
        worldManagerSender.setPerformative(ACLMessage.REQUEST);
        worldManagerSender.setReplyWith("LOGIN_INFO");
        String content = this.getLoginInfo().toString();
        worldManagerSender.setContent(content);
        this.send(worldManagerSender);
        
        MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchSender(new AID(worldManager,AID.ISLOCALNAME)), 
                MessageTemplate.MatchInReplyTo("LOGIN_INFO"));
        worldManagerReceiver = this.blockingReceive(template);
        
        if (worldManagerReceiver.getPerformative() != ACLMessage.CONFIRM &&
                worldManagerReceiver.getPerformative() != ACLMessage.INFORM){
            System.out.println("El dron no ha podido hacer login en el mundo");
            status = "logout";
            return;
        }
        
        Info("Logueados en el sistema");
        
        status = "fase1";
    }
    
    private void logout(){
        // Cancelamos suscripción a Identity Manager
        identityManagerSender = identityManagerReceiver.createReply();
        identityManagerSender.setPerformative(ACLMessage.CANCEL);
        identityManagerSender.setContent("{}");
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
        
        // Enviamos una solicitud a todos los marketplaces para obtener su catálogo
        
        marketplaceSender.setSender(this.getAID());
        marketplaceSender.setPerformative(ACLMessage.QUERY_REF);
        marketplaceSender.setConversationId(convID);
        marketplaceSender.setReplyWith("CIE_CATALOGUE");
        marketplaceSender.setProtocol("REGULAR");
        marketplaceSender.setContent("{}");
        for (int i=0; i<marketplaces.length; i++){
            marketplaceSender.addReceiver(new AID (marketplaces[i], AID.ISLOCALNAME));
        }
        this.send(marketplaceSender);
        
        // Recibimos respuesta de cada marketplace
        
        MessageTemplate template;
        
        for (int i=0; i<marketplaces.length; i++){
            template = MessageTemplate.and(MessageTemplate.MatchInReplyTo("CIE_CATALOGUE"), 
                MessageTemplate.MatchSender(new AID(marketplaces[i], AID.ISLOCALNAME)));
            
            marketplaceReceiver = this.blockingReceive(template);
            
            System.out.println("Respuesta market: " + ACLMessage.getPerformative(marketplaceReceiver.getPerformative())+"->" + marketplaceReceiver.getContent());
            
            if (marketplaceReceiver.getPerformative() != ACLMessage.INFORM){
                Info ("Fallo al obtener el catálogo de " + marketplaces[i]);
            }
            else{
                JsonObject mkinfo = Json.parse(marketplaceReceiver.getContent()).asObject();
                
                for (int j=0; j<mkinfo.get("products").asArray().size(); j++){
                    
                    String reference = mkinfo.get("products").asArray().get(j).asObject().get("reference").asString();
                    int price = mkinfo.get("products").asArray().get(j).asObject().get("price").asInt();
                    
                    if (reference.contains("ANGULARDELUX")){
                        catalogueAngular.add(price + "@" + reference + "@" + marketplaces[i]);
                    }
                    else if(reference.contains("DISTANCEDELUX")){
                        catalogueDistance.add(price + "@" + reference + "@" + marketplaces[i]);
                    }    
                    else if (reference.contains("ENERGY")){
                        catalogueEnergy.add(price + "@" + reference + "@" + marketplaces[i]);
                    } 
                }
            }
        }
        
        // Ordenamos las listas de menor a mayor precio
        Collections.sort(catalogueAngular);
        Collections.sort(catalogueDistance);
        Collections.sort(catalogueEnergy);
        
        System.out.println("c.angular:"+catalogueAngular);
        System.out.println("c.distance:"+catalogueDistance);
        System.out.println("c.energy:"+catalogueEnergy);
        
        // Realizamos la compra de los sensores correspondientes
        
        marketplaceSender.reset();
        marketplaceSender.setSender(this.getAID());
        marketplaceSender.setPerformative(ACLMessage.REQUEST);
        marketplaceSender.setConversationId(convID);
        marketplaceSender.setReplyWith("CIE_PURCHASE");
        marketplaceSender.setProtocol("REGULAR");
        
        String [] splitInfo;
        JsonObject content;
        
        // Obtenemos sensor ANGULAR:
        if (catalogueAngular.isEmpty()){
            System.out.println("No hay ningún sensor angular disponible");
            status = "logout";
            return;
        }
        
        do{
            splitInfo = catalogueAngular.get(0).split("@");
            marketplaceSender.addReceiver(new AID(splitInfo[2], AID.ISLOCALNAME));
            content = this.getProductListMessage(catalogueAngular.get(0));

            if (content == null){
                System.out.println("No hay recursos disponibles para realizar la compra");
                status = "logout";
                return;
            }

            marketplaceSender.setContent(content.toString());
            this.send(marketplaceSender);

            template = MessageTemplate.and(MessageTemplate.MatchSender(new AID(splitInfo[2], AID.ISLOCALNAME)),
                        MessageTemplate.MatchInReplyTo("CIE_PURCHASE"));

            marketplaceReceiver = this.blockingReceive(template);

            if (marketplaceReceiver.getPerformative() != ACLMessage.INFORM){
                System.out.println("El producto solicitado no estaba disponible");
                catalogueAngular.remove(0);
            }
            else{
                for (int i=0; i<Integer.parseInt(splitInfo[0]); i++){
                    coins.remove(0);
                }
        
                infoAngular = Json.parse(marketplaceReceiver.getContent()).asObject().get("reference").asString();
                System.out.println("infoAngular: "+infoAngular);
            }
        }while(marketplaceReceiver.getPerformative() != ACLMessage.INFORM);
        
        System.out.println("obtenemos sensor distance:");
        // Obtenemos sensor DISTANCE:
        marketplaceSender.removeReceiver(new AID(splitInfo[2], AID.ISLOCALNAME));
        
        if (catalogueDistance.isEmpty()){
            System.out.println("No hay ningún sensor distance disponible");
            status = "logout";
            return;
        }
            
        do{
            splitInfo = catalogueDistance.get(0).split("@");
            marketplaceSender.addReceiver(new AID(splitInfo[2], AID.ISLOCALNAME));
            content = this.getProductListMessage(catalogueDistance.get(0));

            if (content == null){
                System.out.println("No hay recursos disponibles para realizar la compra");
                status = "logout";
                return;
            }

            marketplaceSender.setContent(content.toString());
            this.send(marketplaceSender);

            template = MessageTemplate.and(MessageTemplate.MatchSender(new AID(splitInfo[2], AID.ISLOCALNAME)),
                        MessageTemplate.MatchInReplyTo("CIE_PURCHASE"));

            marketplaceReceiver = this.blockingReceive(template);
            System.out.println("resp:"+marketplaceReceiver.getContent());

            if (marketplaceReceiver.getPerformative() != ACLMessage.INFORM){
                System.out.println("El producto solicitado no estaba disponible");
                catalogueDistance.remove(0);
            }
            else{
        
                for (int i=0; i<Integer.parseInt(splitInfo[0]); i++){
                    coins.remove(0);
                }
                infoDistance = Json.parse(marketplaceReceiver.getContent()).asObject().get("reference").asString();
                System.out.println("infoDistance: "+infoDistance);
            }
        }while(marketplaceReceiver.getPerformative() != ACLMessage.INFORM);
        
        // Obtenemos tickets ENERGÍA:
        
        marketplaceSender.removeReceiver(new AID(splitInfo[2], AID.ISLOCALNAME));
        
        if (catalogueEnergy.isEmpty()){
            System.out.println("No hay ningún sensor energy disponible");
            status = "logout";
            return;
        }
        content = this.getProductListMessage(catalogueEnergy.get(0));
        
        while(content != null && !catalogueEnergy.isEmpty()){
            splitInfo = catalogueEnergy.get(0).split("@");
            marketplaceSender.addReceiver(new AID(splitInfo[2], AID.ISLOCALNAME));
            marketplaceSender.setContent(content.toString());
            this.send(marketplaceSender);

            template = MessageTemplate.and(MessageTemplate.MatchSender(new AID(splitInfo[2], AID.ISLOCALNAME)),
                        MessageTemplate.MatchInReplyTo("CIE_PURCHASE"));

            marketplaceReceiver = this.blockingReceive(template);

            if (marketplaceReceiver.getPerformative() != ACLMessage.INFORM){
                System.out.println("El producto solicitado no estaba disponible");
            }
            else{
                for (int i=0; i<Integer.parseInt(splitInfo[0]); i++){
                    coins.remove(0);
                }
        
                infoEnergy.add(Json.parse(marketplaceReceiver.getContent()).asObject().get("reference").asString());
            }
            
            catalogueEnergy.remove(0);
            
            if (!catalogueEnergy.isEmpty())
                content = this.getProductListMessage(catalogueEnergy.get(0));
        }
        
        System.out.println("energy: "+infoEnergy);
        status = "login";
    }
    
    private void readSensors(){
        
        // COMPLETAR...
        
        // [modif]
        status = "logout";
    }
    
}

