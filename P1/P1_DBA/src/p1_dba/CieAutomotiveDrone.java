package p1_dba;

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

    @Override
    public void setup() {
        super.setup();
        status = "login";
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
    }

    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }

    
    @Override
    public void plainExecute() {
        /*Info("Realizando P1");
        ACLMessage out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID("Songoanda", AID.ISLOCALNAME));
        out.setContent("Hello");
        this.sendServer(out);
        ACLMessage in = this.blockingReceive();
        String answer = in.getContent();
        Info("Songoanda dice: "+answer);
        String reply = new StringBuilder(answer).reverse().toString();
        out = in.createReply();
        out.setContent(reply);
        this.sendServer(out);
        _exitRequested = true;*/
        ArrayList<String> sensorList = new ArrayList();
        String worldName = "BasePlayground";
        
        sensorList.add("alive");
        sensorList.add("angular");
        sensorList.add("compass");
        sensorList.add("distance");
        sensorList.add("energy");
        sensorList.add("gps");
        sensorList.add("visual");
        
        switch(status){
            case "login":
                login(sensorList, worldName);
                
            break;
            case "doAction":
                doAction();
            break;
            case "logout":
                logout();
            break;
        }
            
        _exitRequested = true;
        
    }
    
    public JsonObject getDeploymentMessage (ArrayList<String> sensorList, String worldName){
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
    
    public JsonObject getActions (String action){
        JsonObject actions = new JsonObject();
        
        actions.add("command","execute");
        actions.add("key",key);
        actions.add("action", action);
        
        return actions;
    }
    
    public JsonObject getLogout (){
        JsonObject logoutInfo = new JsonObject();
        
        logoutInfo.add("command","logout");
        logoutInfo.add("key",key);
        
        return logoutInfo;
    }
    
    public void login(ArrayList<String> sensorList, String worldName){
        JsonObject loginInfo = getDeploymentMessage(sensorList, worldName);
        
        ACLMessage sendLogin = new ACLMessage();
        sendLogin.setSender(getAID());
        sendLogin.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        sendLogin.setContent(loginInfo.toString());
        this.sendServer(sendLogin);
        
        ACLMessage getLogin = this.blockingReceive();
        String login = getLogin.getContent();
       
        JsonObject parsedLogin;
        parsedLogin = Json.parse(login).asObject();
        
        if (parsedLogin.get("result").asString().equals("ok")){
            status = "doAction";
            key = parsedLogin.get("key").asString();
        }
    }
    
    public void logout(){
        JsonObject logoutInfo = getLogout();
        ACLMessage logout = new ACLMessage();
        logout.setSender(getAID());
        logout.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        logout.setContent(logoutInfo.toString());
        this.sendServer(logout);
    }
    
    public void doAction(){
        JsonObject actions = getActions("rotateL");
        ACLMessage demoAction = new ACLMessage();
        demoAction.setSender(getAID());
        demoAction.addReceiver(new AID(receiver, AID.ISLOCALNAME));
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
}
