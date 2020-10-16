package p1_dba;

import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;

public class CieAutomotiveDrone extends IntegratedAgent{
    
    String receiver;
    private String status;
    private String key;
    private ArrayList<String> sensorList;
    private String worldName;
    private TTYControlPanel panel;
    private int width;
    private int height;

    @Override
    public void setup() {
        super.setup();
        status = "login";
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        sensorList = new ArrayList();
        worldName = "BasePlayground";
        
        sensorList.add("alive");
        sensorList.add("angular");
        sensorList.add("compass");
        sensorList.add("distance");
        sensorList.add("energy");
        sensorList.add("gps");
        sensorList.add("visual");
        
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
            case "doAction":
                doAction();
            break;
            case "logout":
                logout();
            break;
            case "exit":
                panel.close();
                _exitRequested = true;
            break;
        }
    } 
    
    public void doAction(){
        JsonObject sensorsRequest = getSensors();
        ACLMessage sensorsMessage = new ACLMessage();
        sensorsMessage.setSender(getAID());
        sensorsMessage.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        sensorsMessage.setContent(sensorsRequest.toString());
        this.sendServer(sensorsMessage);
        
        ACLMessage getSensors = this.blockingReceive();
        panel.feedData(getSensors,width,height);
        panel.fancyShow();
        String sensors = getSensors.getContent();
        
        JsonObject parsedSensors;
        parsedSensors = Json.parse(sensors).asObject();
        
        if (parsedSensors.get("result").asString().equals("ok")){
            Info(sensors);
        }
        else{
            status = "exit";
        }
        
        JsonObject actions = getActions("rotateL");
        ACLMessage demoAction = getSensors.createReply();
        demoAction.setContent(actions.toString());
        this.sendServer(demoAction);

        ACLMessage getAction = this.blockingReceive();
        String action = getAction.getContent();

        JsonObject parsedAction;
        parsedAction = Json.parse(action).asObject();

        if (parsedAction.get("result").asString().equals("ok")){
            Info("Acción realizada con éxito");
        }
        else{
            Info("La acción no pudo ser realizada");
        }

        status = "logout";
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
            status = "doAction";
            key = parsedLogin.get("key").asString();
            width = parsedLogin.get("width").asInt();
            height = parsedLogin.get("height").asInt();
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
        status = "exit";
    }
}
