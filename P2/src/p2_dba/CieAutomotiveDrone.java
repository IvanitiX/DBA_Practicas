package p2_dba;

import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;
import java.util.Scanner;

public class CieAutomotiveDrone extends IntegratedAgent{
    
    String receiver;
    private String status;
    private String key;
    private ArrayList<String> sensorList;
    private String worldName;
    private TTYControlPanel panel;
    
    //Parámetros del mundo
    private int width;
    private int height;
    private int maxHeight;
    
    //Parámetros leídos por sensores
    private int angular;
    private float distance;
    private int compass;
    private int energy;
    private int sensorsEnergyWaste;
    private int droneX, droneY, droneZ;
    private int visualMatrix[][];
    
    

    @Override
    public void setup() {
        super.setup();
        status = "login";
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        sensorList = new ArrayList();       
        worldName = "Playground1";
        
        sensorList.add("angular");
        sensorList.add("compass");
        sensorList.add("distance");
        sensorList.add("gps");
        sensorList.add("visual");
        
        energy = 1000;
        sensorsEnergyWaste = 5;
        visualMatrix = new int[7][7];
        
        panel = new TTYControlPanel(getAID());
        
        _exitRequested = false;
    }

    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }

    
    @Override
    public void plainExecute() {
        switch(status){
            case "login":
                login();
                
            break;
            case "readSensors":
                readSensors();
            break;
            case "decide":
            break;
            case "idle":
                idle();
            break;
            case "logout":
                logout();
            break;
            case "exit":
                Info("Hemos solicitado la salida");
                _exitRequested = true;
                Info("Hemos solicitado la salida desde variable");
            break;
            default:
                Info ("Estado " + status);
            break;
        }
    } 
    
    public void idle(){
        status = "readSensors";
    }
    
    public JsonObject getActions (String action){
        JsonObject actions = new JsonObject();
        
        actions.add("command","execute");
        actions.add("key",key);
        actions.add("action", action);
        
        return actions;
    }
    
    public JsonObject getDeploymentMessage (){
        JsonObject loginInfo = new JsonObject();
        JsonArray sensors = new JsonArray();
        
        for (String s : sensorList){
            sensors.add(s);
        }
        
        loginInfo.add("command","login");
        loginInfo.add("world",worldName);
        loginInfo.add("attach",sensors);
        
        return loginInfo;
    } 
    
    public JsonObject getLogout (){
        JsonObject logoutInfo = new JsonObject();
        
        logoutInfo.add("command","logout");
        logoutInfo.add("key",key);
        
        return logoutInfo;
    }
    
    public JsonObject getSensors (){
        JsonObject sensorsInfo = new JsonObject();
        
        sensorsInfo.add("command","read");
        sensorsInfo.add("key",key);
        
        return sensorsInfo;
    }
    
    public void login(){
        JsonObject loginInfo = getDeploymentMessage();
        
        ACLMessage sendLogin = new ACLMessage();
        sendLogin.setSender(getAID());
        sendLogin.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        sendLogin.setContent(loginInfo.toString());
        this.sendServer(sendLogin);
        
        ACLMessage getLogin = this.blockingReceive();
        String login = getLogin.getContent();
       
        // Esperando respuesta
        JsonObject parsedLogin;
        parsedLogin = Json.parse(login).asObject();
        
        if (parsedLogin.get("result").asString().equals("ok")){
            status = "idle";
            key = parsedLogin.get("key").asString();
            width = parsedLogin.get("width").asInt();
            height = parsedLogin.get("height").asInt();
            maxHeight = parsedLogin.get("maxflight").asInt();
        }
        else{
            status = "exit";
        }
    }
    
    public void logout(){
        JsonObject logoutInfo = getLogout();
        ACLMessage logout = new ACLMessage();
        logout.setSender(getAID());
        logout.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        logout.setContent(logoutInfo.toString());
        this.sendServer(logout);
        Info("Solicitando logout");
        status = "exit";
    }
    
    public void readSensors(){
        JsonObject sensorsRequest = getSensors();
        ACLMessage sensorsMessage = new ACLMessage();
        sensorsMessage.setSender(getAID());
        sensorsMessage.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        sensorsMessage.setContent(sensorsRequest.toString());
        this.sendServer(sensorsMessage);
        
        ACLMessage getSensors = this.blockingReceive();
        panel.feedData(getSensors,width,height,maxHeight);
        panel.fancyShow();
        String sensors = getSensors.getContent();
        
        JsonObject parsedSensors;
        parsedSensors = Json.parse(sensors).asObject();
        
        if (parsedSensors.get("result").asString().equals("ok")){
            Info(sensors);
            JsonArray sensorReadings = parsedSensors.get("details").asObject().get("perceptions").asArray();
            angular = sensorReadings.get(0).asObject().get("data").asArray().get(0).asInt();
            compass = sensorReadings.get(1).asObject().get("data").asArray().get(0).asInt();
            distance = sensorReadings.get(2).asObject().get("data").asArray().get(0).asFloat();
            droneX = sensorReadings.get(3).asObject().get("data").asArray().get(0).asArray().get(0).asInt();
            droneY = sensorReadings.get(3).asObject().get("data").asArray().get(0).asArray().get(1).asInt();
            droneZ = sensorReadings.get(3).asObject().get("data").asArray().get(0).asArray().get(2).asInt();
            for (int i=0; i < 7 ; i++){
                for (int j=0 ; j < 7 ; j++){
                    visualMatrix[i][j] = sensorReadings.get(4).asObject().get("data").asArray().get(i).asArray().get(j).asInt();
                } 
            }
            System.out.println("\n" + angular + "\n" + compass + "\n" + distance + "\n" + droneX + "\n" + droneY + "\n" + droneZ + "\n\n");
            for (int i=0; i < 7 ; i++){
                for (int j=0 ; j < 7 ; j++){
                     System.out.print(visualMatrix[i][j] + "\t");
                } 
                System.out.print("\n");
            }
            status = "logout";
        }
        else{
            status = "logout";
        }
    }
}
