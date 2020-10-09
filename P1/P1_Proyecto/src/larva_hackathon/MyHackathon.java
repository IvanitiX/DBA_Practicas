package larva_hackathon;

import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class MyHackathon extends LarvaAgent{
    
    @Override
    public void plainExecute() {
        Info("Hablando con Songoanda");
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
        _exitRequested = true;
    }
    
}
