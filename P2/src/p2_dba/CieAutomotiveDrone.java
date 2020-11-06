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
import java.awt.Point;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
 */
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
    private int objectiveX, objectiveY;
    private Map actionsCost;
    private Queue<Point> locationsMemory;
    private int nextStepHeight;
    private int timer;

    /**
     * Inicializa los componentes del dron
     * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
     */
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
        nextStepHeight = Integer.MIN_VALUE;
        timer = 210;
        
        panel = new TTYControlPanel(getAID());
        
        _exitRequested = false;
    }

    /**
     * Desloguea al agente de la plataforma
    */
    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }

    /**
     * Este método, en función del estado del agente en cada iteración, delega la ejecución de dicha iteración a un método diferente
     * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
     */
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
    
    /**
     * Evalúa la necesidad de establecer un mini objetivo, en caso de ser necesario, se establecen las coordenadas de éste
     * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
     */
    public void evaluatePossibleMiniObjective(){
        int offset = 3; // Distancia frente al dron en la que se establecerá el posible objetivo
        int nextHeight ; // Altura de la siguiente casilla

        // Valoramos si es necesario establecer un mini objetivo, dependiendo de nuestra orientación
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
    }
    
    /**
     * Determina el objetivo al que el dron debe dirigirse y toma la mejor acción posible para aproximarse a él
     * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
     * @return action Acción a realizar
     */
    public String findingObjective(){
        // Obtenemos inicialmente las coordenadas del objetivo
        if (!objectiveLocated)
            locateObjective();
        
        // Determinamos la distancia euclídea con respecto al objetivo de las casillas que nos rodean
        obtainActionsCost();
        
        nextStepHeight = Integer.MIN_VALUE;
        
        String action = "";
        
        // Evaluamos el posible mini objetivo
        evaluatePossibleMiniObjective();
        
        // Calculamos el ángulo que ha de girar el dron para orientarse hacia la casilla adyacente que presento una menor distancia euclídea con respecto al objetivo
        int angle = getNextStepAngle();
        int dif = (int) (compass - angle);
        
        timer--;
        if (timer <= 0){
            locateObjective();
            timer = 210;
        }
        
        // Si evalúo giro a la derecha
        if (dif == 0){
            action = "moveF";
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
            
            if (nextStepHeight > droneZ)
                action = "moveUP";
        }
        else if (dif < 0){
            if (Math.abs(dif) > 180) // No es conveniente girar a la derecha
                action = "rotateL";
            else
                action = "rotateR";
        }
        // Evalúo giro a la izquierda
        else{
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
            
            if ((double) pair.getValue() < minDist){
                minDist = (double) pair.getValue();
                angle = (int) pair.getKey();
            }
        }
        return angle;
    }
    
    /**
     * Calcula, en base a la posición actual del dron, al ángulo que devuelve el sensor angular y a la distancia que devuelve el sensor distance; la posición en X e Y del objetivo
     * @author Noelia Escalera Mejías, Fº Javier Casado de Amezúa García, Jesús Torres Sánchez, Iván Valero Rodríguez
     */
    public void locateObjective(){
        objectiveX = (int) Math.round(droneX + distance*Math.sin(Math.toRadians(angular)));
        objectiveY = (int) Math.round(droneY - distance*Math.cos(Math.toRadians(angular)));
        objectiveLocated = true;
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
        
        // Modo de búsqueda de Objective
        action = findingObjective();
        
        // Evaluamos si tenemos que recargar y actualizamos la acción consecuentemente
        action = evaluateRecharge(action);
         
        // Si hemos alcanzado el objetivo, descendemos
        action = evaluateObjective(action);
        
        // Actualizamos la energía
        updateEnergy(action);
        
        if (action.equals("rescue")){
            Info("Rescatando al objetivo");
        }
        
        // Manejamos la memoria del dron
        updateMemory();
        
        // Enviamos el mensaje con la acción
        sendAction(action);
       
        // Esperando respuesta
        JsonObject parsedResult = receiveAnswerToAction();
        
        // En función de la respuesta del servidor, actualizamos el estado del agente
        if (parsedResult.get("result").asString().equals("ok") && !(status.equals("logout"))){
            status = "idle";
        }
        else{
            status = "logout";
        }
    }
    
    public String evaluateObjective (String act){
        String action = act;
        
        if (distance == 0){ // Objective = Ludwig
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
        else if (droneX == objectiveX && droneY == objectiveY){ // Objective = Mini Objective
            this.locateObjective();
            action = "rotateR";
        }
        
        return action;
    }
    
    public String evaluateRecharge (String act){
        String action = act;
        
        if (action == "moveF"){
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
            if(Math.floor((droneZ - visualMatrix[3][3])/5.0)*(sensorsEnergyWaste+5) + ((droneZ - visualMatrix[3][3])%5.0) + sensorsEnergyWaste + 5 >= energy){
                if (droneZ - visualMatrix[3][3] >= 5)
                    action = "moveD";
                else if ((droneZ - visualMatrix[3][3]) < 5 && (droneZ - visualMatrix[3][3]) > 0)
                    action = "touchD";
                else
                    action = "recharge";
            }
        }
        
        return action;
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
            
            energy -= sensorsEnergyWaste;
            
            status = "decide";
        }
        else{
            status = "logout";
        }
    }
    
    public JsonObject receiveAnswerToAction (){
        worldReceiver = this.blockingReceive();
        String result = worldReceiver.getContent();
        JsonObject parsedResult;
        parsedResult = Json.parse(result).asObject();
        
        return parsedResult;
    }
    
    public void sendAction (String act){
        String action = act;
        
        JsonObject actionToSend = getActions(action);
        worldSender = worldReceiver.createReply();
        worldSender.setContent(actionToSend.toString());
        this.sendServer(worldSender);
    }
    
    public void updateEnergy (String act){
        String action = act;
        
        if (action == "moveF" || action == "rotateR" || action == "rotateL" || action == "touchD"){
            energy--;
        }
        else if (action == "recharge"){
            energy=1000;
        }
        else{
            energy-=5;
        }
    }
    
    public void updateMemory (){
        Point currentPos = new Point(droneX,droneY);
        
        if(!locationsMemory.contains(currentPos)){
            locationsMemory.add(currentPos);
        }
        
        if (locationsMemory.size() >= MAX_MEMORY_SIZE){
            locationsMemory.remove();
        }
    }
}
