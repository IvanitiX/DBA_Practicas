package p2_dba;

import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.awt.Point;
import java.util.LinkedList;
import java.util.Queue;

public class CieAutomotiveDrone extends IntegratedAgent{
    
    static int MAX_MEMORY_SIZE = 100;
    
    String receiver;
    private String status;
    private String key;
    private ArrayList<String> sensorList;
    private String worldName;
    private TTYControlPanel panel;
    private ACLMessage worldSender;
    private ACLMessage worldReceiver;
    
    //Parámetros del mundo
    private int width;
    private int height;
    private int maxHeight;
    
    //Parámetros leídos por sensores
    private int angular;
    private float distance;
    private int compass;
    private int droneX, droneY, droneZ;
    private int visualMatrix[][];
    
    // Parámetros interpretados
    private boolean objectiveLocated;
    private int sensorsEnergyWaste;
    private int energy;
    private double objectiveX, objectiveY;
    private Map actionsCost;
    private Queue<Point> locationsMemory;
    private boolean borderingMode;
    private boolean miniObjective;
    private int nextStepHeight;
    private int timer;

    @Override
    public void setup() {
        super.setup();
        status = "login";
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        sensorList = new ArrayList();       
        worldName = "World9";
        
        worldSender = new ACLMessage();
        worldSender.setSender(getAID());
        worldSender.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        
        worldReceiver = new ACLMessage();
        
        sensorList.add("angular");
        sensorList.add("compass");
        sensorList.add("distance");
        sensorList.add("gps");
        sensorList.add("visual");
        
        energy = 1000;
        sensorsEnergyWaste = 5;
        objectiveLocated = false;
        visualMatrix = new int[7][7];
        locationsMemory = new LinkedList<Point>();
        actionsCost = new HashMap<Integer, Double>();
        borderingMode = false;
        miniObjective = false;
        nextStepHeight = Integer.MIN_VALUE;
        timer = 210;
        
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
                decide();
            break;
            case "idle":
                idle();
            break;
            case "logout":
                logout();
            break;
            case "exit":
                _exitRequested = true;
            break;
        }
    } 
    
    public String borderingObstacle(){
        String action = "";
        
        return action;
    }
    
    public void locateObjective(){
        miniObjective = false;
        System.out.println("Angular : " + angular + ", Sin/Cos : " + Math.sin(Math.toRadians(angular)) + "/" + Math.cos(Math.toRadians(angular)));
        System.out.println(droneX + "," + distance*Math.cos(angular));
        System.out.println(droneY + "," + distance*Math.sin(angular));
        objectiveX = droneX + distance*Math.sin(Math.toRadians(angular));
        objectiveY = droneY - distance*Math.cos(Math.toRadians(angular));
        System.out.println(objectiveX + "," + objectiveY);
        objectiveLocated = true;
    }
    
    public String findingObjective(){
        // Obtenemos inicialmente las coordenadas del objetivo
        if (!objectiveLocated || !miniObjective)
            locateObjective();
        
        // Determinamos acción a realizar
        this.obtainActionsCost();
        int angle = this.getNextStepAngle();
        nextStepHeight = Integer.MIN_VALUE;
        
        
        int dif = (int) (compass - angle);
        String action = "";
        

        //Offset se tiene que adecuar por si hubiera un fin de mapa
        int offset = 3;
        int nextHeight = 0; 
        //borderingMode = true;

        switch(compass){
            case 0: 
                nextHeight = visualMatrix[2][3];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX;
                    objectiveY = droneY - offset ;
                }
            break;
            case 45: 
                nextHeight = visualMatrix[2][4];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX + offset;
                    objectiveY = droneY - offset ;
                }
            break;
            case 90: 
                nextHeight = visualMatrix[3][4];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX + offset;
                    objectiveY = droneY ;
                }
            break;
            case 135:
                nextHeight = visualMatrix[4][4];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX + offset;
                    objectiveY = droneY + offset ;
                }
            break;
            case 180:
                nextHeight = visualMatrix[4][3];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX;
                    objectiveY = droneY + offset ;
                }
            break;
            case -135:
                nextHeight = visualMatrix[4][2];
                if(nextHeight >= maxHeight){ 
                    objectiveX = droneX - offset;
                    objectiveY = droneY + offset ;
                }
            break;
            case -90: 
                nextHeight = visualMatrix[3][2];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX - offset;
                    objectiveY = droneY ;
                }
            break;
            case -45:
                nextHeight = visualMatrix[2][2];
                if(nextHeight >= maxHeight){
                    objectiveX = droneX - offset ;
                    objectiveY = droneY - offset ;
                }
            break;

        }
        
        if (nextHeight >= maxHeight){
            miniObjective = true;
        }
            
            
        timer--;
        if (timer <= 0){
            locateObjective();
            timer = 210;
        }
            
        System.out.println("Objetivo actual -> (" + objectiveX + "," + objectiveY + ")");
        
        // Si evalúo giro a la derecha
        if (dif == 0){
            action = "moveF";
            System.out.println(angle);
            if (compass - 0 < 0.1 && compass - 0 > -0.1)
                nextStepHeight = visualMatrix[2][3];
            else if (compass - 45 < 0.1 && compass - 45 > -0.1)
                nextStepHeight = visualMatrix[2][4];
            else if (compass - 90 < 0.1 && compass - 90 > -0.1)
                nextStepHeight = visualMatrix[3][4];
            else if (compass - 135 < 0.1 && compass - 135 > -0.1)
                nextStepHeight = visualMatrix[4][4];
            else if (compass - 180 < 0.1 && compass - 180 > -0.1)
                nextStepHeight = visualMatrix[4][3];
            else if (compass - (-135) < 0.1 && compass - (-135) > -0.1)
                nextStepHeight = visualMatrix[4][2];
            else if (compass - (-90) < 0.1 && compass - (-90) > -0.1)
                nextStepHeight = visualMatrix[3][2];
            else if (compass - (-45) < 0.1 && compass - (-45) > -0.1)
                nextStepHeight = visualMatrix[2][2];
            
            System.out.println("Altura dron Z : " + droneZ + "/ Siguiente posición en Z : " + nextStepHeight + "\n Distancia al objetivo : " + distance);
            if (nextStepHeight > droneZ)
                action = "moveUP";
        }
        else if (dif < 0){
            System.out.println("Evalúo girar a la derecha con D=" + dif);
            if (Math.abs(dif) > 180) // No es conveniente girar a la derecha
                action = "rotateL";
            else
                action = "rotateR";
        }
        // Evalúo giro a la izquierda
        else{
            System.out.println("Evalúo girar a la izquierda con D=" + dif);
            if (Math.abs(dif) > 180) // No es conveniente girar a la izquierda
                action = "rotateR";
            else
                action = "rotateL";
        }
        
        
        return action;
    }
    
    
    public int getNextStepAngle(){
        int angle = 0;
        
        Iterator it = actionsCost.entrySet().iterator();
        
        double minDist = Double.MAX_VALUE;
        
        while(it.hasNext()){
            Map.Entry pair = (Map.Entry) it.next();
            System.out.println(pair.getValue()+" "+pair.getKey());
            if ((double) pair.getValue() < minDist){
                minDist = (double) pair.getValue();
                angle = (int) pair.getKey();
            }
        }
        System.out.println("Cojo el iterador con ángulo " + angle + " y dist. " + minDist);
        return angle;
    }
    
    
    public void obtainActionsCost(){
        if (visualMatrix[2][3] < -maxHeight || visualMatrix[2][3] >= maxHeight || locationsMemory.contains(new Point(droneX, droneY-1))){
            actionsCost.put(0, Double.MAX_VALUE);
        }
        else actionsCost.put(0, this.getDistanceToObjective(droneX, droneY-1));
        
        if (visualMatrix[2][4] < -maxHeight || visualMatrix[2][4] >= maxHeight || locationsMemory.contains(new Point(droneX+1, droneY-1))){
            actionsCost.put(45, Double.MAX_VALUE);
        }
        else actionsCost.put(45, this.getDistanceToObjective(droneX+1, droneY-1));
        
        if (visualMatrix[3][4] < -maxHeight || visualMatrix[3][4] >= maxHeight || locationsMemory.contains(new Point(droneX+1, droneY))){
            actionsCost.put(90, Double.MAX_VALUE);
        }
        else actionsCost.put(90, this.getDistanceToObjective(droneX+1, droneY));
        
        if (visualMatrix[4][4] < -maxHeight || visualMatrix[4][4] >= maxHeight || locationsMemory.contains(new Point(droneX+1, droneY+1))){
            actionsCost.put(135, Double.MAX_VALUE);
        }
        else actionsCost.put(135, this.getDistanceToObjective(droneX+1, droneY+1));
        
        if (visualMatrix[4][3] < -maxHeight || visualMatrix[4][3] >= maxHeight || locationsMemory.contains(new Point(droneX, droneY+1))){
            actionsCost.put(180, Double.MAX_VALUE);
        }
        else actionsCost.put(180, this.getDistanceToObjective(droneX, droneY+1));
        
        if (visualMatrix[4][2] < -maxHeight || visualMatrix[4][2] >= maxHeight || locationsMemory.contains(new Point(droneX-1, droneY+1))){
            actionsCost.put(-135, Double.MAX_VALUE);
        }
        else actionsCost.put(-135, this.getDistanceToObjective(droneX-1, droneY+1));
        
        if (visualMatrix[3][2] < -maxHeight || visualMatrix[3][2] >= maxHeight || locationsMemory.contains(new Point(droneX-1, droneY))){
            actionsCost.put(-90, Double.MAX_VALUE);
        }
        else actionsCost.put(-90, this.getDistanceToObjective(droneX-1, droneY));
        
        if (visualMatrix[2][2] < -maxHeight || visualMatrix[2][2] >= maxHeight || locationsMemory.contains(new Point(droneX-1, droneY-1))){
            actionsCost.put(-45, Double.MAX_VALUE);
        }
        else actionsCost.put(-45, this.getDistanceToObjective(droneX-1, droneY-1));
    }
    
    
    public void decide(){
        String action = "";
        
        if(!borderingMode){
            /*Modo de búsqueda de Objective*/
            action = findingObjective();
        }
        else{
            /*Modo Bordeo*/
            action = borderingObstacle();
        }
        
        
        // Estimación de la energía
        System.out.println("Energía restante : " + energy);
        if (action == "moveF"){
            
            /*if ((droneZ - nextStepHeight + 5)*(sensorsEnergyWaste/5.0+1/5.0) >= (energy + sensorsEnergyWaste*6+6)){*/
            if(Math.floor((droneZ - nextStepHeight)/5.0)*(sensorsEnergyWaste+5) + ((droneZ - nextStepHeight)%5.0) + sensorsEnergyWaste + 5 >= energy){
                if (droneZ - visualMatrix[3][3] >= 5)
                    action = "moveD";
                else if ((droneZ - visualMatrix[3][3]) < 5 && (droneZ - visualMatrix[3][3]) > 0)
                    action = "touchD";
                else
                    action = "recharge";
            }
        }
        else{
            /*if ((droneZ - visualMatrix[3][3] + 5)*(sensorsEnergyWaste/5.0+1/5.0) >= (energy + sensorsEnergyWaste*6+6)){*/
            if(Math.floor((droneZ - visualMatrix[3][3])/5.0)*(sensorsEnergyWaste+5) + ((droneZ - visualMatrix[3][3])%5.0) + sensorsEnergyWaste + 5 >= energy){
                if (droneZ - visualMatrix[3][3] >= 5)
                    action = "moveD";
                else if ((droneZ - visualMatrix[3][3]) < 5 && (droneZ - visualMatrix[3][3]) > 0)
                    action = "touchD";
                else
                    action = "recharge";
            }
        }
        
        
        // Si hemos alcanzado el objetivo, descendemos
        if (distance == 0){
            if (droneZ - visualMatrix[3][3] >= 5)
                action = "moveD";
            else if ((droneZ - visualMatrix[3][3]) < 5 && (droneZ - visualMatrix[3][3]) > 0)
                action = "touchD";
            else if (energy < 10)
                action = "recharge";
            else{
                action = "rescue";
                status = "logout";
            }
        }
        else if (droneX == Math.round(objectiveX) && droneY == Math.round(objectiveY)){
            this.locateObjective();
            action = "rotateR";
        }
        
        
        // Actualizamos la energía
        if (action == "moveF" || action == "rotateR" || action == "rotateL" || action == "touchD"){
            energy--;
        }
        else if (action == "recharge"){
            energy=1000;
        }
        else{
            energy-=5;
        }
        
        if (action.equals("rescue")){
            Info("Rescatando al objetivo");
        }
        
        /*Manejamos la memoria del dron*/
        Point currentPos = new Point(droneX,droneY);
        
        if(!locationsMemory.contains(currentPos)){
            locationsMemory.add(currentPos);
        }
        
        if (locationsMemory.size() >= MAX_MEMORY_SIZE){
            locationsMemory.remove();
        }
        
        System.out.println("Tamaño de cola de memoria = " + locationsMemory.size());
        for (Point p : locationsMemory){
            System.out.print("(" + p.getX() + "," + p.getY() + ")\t");
        }
        System.out.println("\n________\n\n");
        
        JsonObject actionToSend = getActions(action);
        worldSender = worldReceiver.createReply();
        worldSender.setContent(actionToSend.toString());
        this.sendServer(worldSender);
        
        worldReceiver = this.blockingReceive();
        String result = worldReceiver.getContent();
       
        // Esperando respuesta
        JsonObject parsedResult;
        parsedResult = Json.parse(result).asObject();
        
        if (parsedResult.get("result").asString().equals("ok") && !(status.equals("logout"))){
            status = "idle";
        }
        else{
            status = "logout";
        }
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
    
    public double getDistanceToObjective(int posX, int posY){
        System.out.println(posX + " " + posY + " " +Math.sqrt(Math.pow(objectiveX-posX,2) + Math.pow(objectiveY-posY,2)));
        return Math.sqrt(Math.pow(objectiveX-posX,2) + Math.pow(objectiveY-posY,2));
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
    
    public void idle(){
        status = "readSensors";
    }
    
    public void login(){
        JsonObject loginInfo = getDeploymentMessage();
        
        worldSender.setContent(loginInfo.toString());
        this.sendServer(worldSender);
        
        worldReceiver = this.blockingReceive();
        String login = worldReceiver.getContent();
       
        // Esperando respuesta
        JsonObject parsedLogin;
        parsedLogin = Json.parse(login).asObject();
        
        if (parsedLogin.get("result").asString().equals("ok")){
            status = "idle";
            key = parsedLogin.get("key").asString();
            width = parsedLogin.get("width").asInt();
            height = parsedLogin.get("height").asInt();
            maxHeight = parsedLogin.get("maxflight").asInt();
            System.out.println("Altura máxima : " + maxHeight + "\n\n");
        }
        else{
            status = "exit";
        }
    }
    
    public void logout(){
        this.readSensors();
        JsonObject logoutInfo = getLogout();
        worldSender.setContent(logoutInfo.toString());
        this.sendServer(worldSender);
        Info("Solicitando logout");
        status = "exit";
    }
    
    public void readSensors(){
        JsonObject sensorsRequest = getSensors();
        worldSender.setContent(sensorsRequest.toString());
        this.sendServer(worldSender);
        
        worldReceiver = this.blockingReceive();
        panel.feedData(worldReceiver,width,height,maxHeight);
        panel.fancyShow();
        String sensors = worldReceiver.getContent();
        
        JsonObject parsedSensors;
        parsedSensors = Json.parse(sensors).asObject();
        
        if (parsedSensors.get("result").asString().equals("ok")){
            Info(sensors);
            JsonArray sensorReadings = parsedSensors.get("details").asObject().get("perceptions").asArray();
            angular = (int) sensorReadings.get(0).asObject().get("data").asArray().get(0).asFloat();
            compass = (int) sensorReadings.get(1).asObject().get("data").asArray().get(0).asFloat();
            distance = sensorReadings.get(2).asObject().get("data").asArray().get(0).asFloat();
            droneX = sensorReadings.get(3).asObject().get("data").asArray().get(0).asArray().get(0).asInt();
            droneY = sensorReadings.get(3).asObject().get("data").asArray().get(0).asArray().get(1).asInt();
            droneZ = sensorReadings.get(3).asObject().get("data").asArray().get(0).asArray().get(2).asInt();
            for (int i=0; i < 7 ; i++){
                for (int j=0 ; j < 7 ; j++){
                    visualMatrix[i][j] = sensorReadings.get(4).asObject().get("data").asArray().get(i).asArray().get(j).asInt();
                } 
            }
            
            /*
            System.out.println("\n" + angular + "\n" + compass + "\n" + distance + "\n" + droneX + "\n" + droneY + "\n" + droneZ + "\n\n");
            for (int i=0; i < 7 ; i++){
                for (int j=0 ; j < 7 ; j++){
                     System.out.print(visualMatrix[i][j] + "\t");
                } 
                System.out.print("\n");
            }
            */
            energy -= sensorsEnergyWaste;
            
            status = "decide";
        }
        else{
            status = "logout";
        }
    }
}
