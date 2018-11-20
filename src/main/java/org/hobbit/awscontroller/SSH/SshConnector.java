package org.hobbit.awscontroller.SSH;

import com.google.common.collect.Lists;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class SshConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SshConnector.class);
    private Timer timer;
    private Map<String, HSession> openedConnections = new TreeMap<>();
    static SshConnector sshConnector;
    int attempt =0;
    boolean closing=false;

    public static SshConnector getInstance(){
        if(sshConnector==null)
            sshConnector = new SshConnector();
        return sshConnector;
    }

//    public Session createSession(String username, String host, int port, String keyfilePath){
//        return createSession(username, host, port, keyfilePath, null, null);
//    }
//    public Session createSession(String username, String host, int port, String keyfilePath, Session parentSession, int[] portsToForward){
//
////        UserInfo userInfo = new UserInfoImpl();
////        Session session;
////
////        try {
////
////            JSch jsch = new JSch();
////            jsch.addIdentity(keyfilePath);
////
////            int assinged_port=-1;
////            if (parentSession != null){
////                assinged_port = parentSession.setPortForwardingL(0, host, 22);
////                session = jsch.getSession(username, "127.0.0.1", assinged_port);
////            }else{
////                session = jsch.getSession(username, host, port);
////            }
////            session.setUserInfo(userInfo);
////            //session.setHostKeyAlias(host);
////
////            Map<Integer, Integer> forwardings = new HashMap<>();
////            if(portsToForward!=null)
////                for (int portToForward : portsToForward) {
////                    int forwardedPort = session.setPortForwardingL(0, "127.0.0.1", portToForward);
////                    forwardings.put(portToForward, forwardedPort);
////                }
////
////
////            session.setHostKeyAlias(host);
////            session.openTunnel(30000);
////            //System.out.println("Connected to "+host +(assinged_port>0?" via "+assinged_port:""));
////            LOGGER.debug("Connected to "+host +(assinged_port>0?" via "+assinged_port:""));
////            if(forwardings.size()>0)
////                LOGGER.debug("Ports forwarded: ");
////            for (int portToForward : forwardings.keySet())
////                LOGGER.debug(portToForward+" -> "+forwardings.get(portToForward));
////                //System.out.println(portToForward+" -> "+forwardings.get(portToForward));
////
////            return session;
//
////        }
////        catch(Exception e){
////            System.out.println(e);
////        }
//        return null;
//    }

    public void openTunnel(HSession hsession) throws Exception {
        openTunnel(hsession, null, null);
    }
    public void openTunnel(HSession hsession, Function <HSession, String> onConnect, Function <HSession, String> onDisconnectConnect) throws Exception {
        openTunnel(hsession, 180000, onConnect);
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                    Boolean reachable = false;
                    try {
                        ChannelExec testChannel = (ChannelExec) hsession.getSession().openChannel("exec");
                        testChannel.setCommand("true");
                        testChannel.connect();
                        reachable = true;
                    } catch (Exception e) {
                        LOGGER.debug(e.getMessage());
                    }

                    if (!reachable) {
                        if (openedConnections.containsKey(hsession.getHost()))
                            openedConnections.remove(hsession.getHost());

                        if(attempt<5){
                            try {
                                openTunnel(hsession, 180000, onConnect);
                            } catch (Exception e) {
                                LOGGER.info("Cannot open a tunnel to {}", hsession.getHost());
                            }
                            attempt++;
                        }else{
                            LOGGER.info("Closing tunnel");
                            closeSessions();
                            if(onDisconnectConnect!=null)
                                onDisconnectConnect.apply(null);
                        }
                    }


            }
        }, 0, 3000);

    }

    public void stopTunnelMonitoring(){
        LOGGER.info("Stopping tunnels monitoring");
        if(timer!=null) {
            timer.purge();
            timer.cancel();
            LOGGER.info("Tunnels monitoring stopped");
        }
    }

    public void openTunnel(HSession session, int connectTimeout, Function <HSession, String> onConnect) throws JSchException {
        List<HSession> connectionOrder = new ArrayList<>();
        HSession currentSession = session;
        connectionOrder.add(currentSession);

        while (currentSession.getParentSession()!=null){
            currentSession = currentSession.getParentSession();
            connectionOrder.add(currentSession);
        }

        for (HSession hSession: Lists.reverse(connectionOrder)){
//            if(session.getSession().isConnected()) {
//                session.getSession().disconnect();
//                LOGGER.debug("Trying to reconnect {}", hSession.getHost());
//            }else
//                LOGGER.debug("Trying to openTunnel {}", hSession.getHost());

            if(!hSession.getSession().isConnected())
                hSession.getSession().connect(30000);

            //System.out.println("Connected to "+host +(assinged_port>0?" via "+assinged_port:""));
            LOGGER.debug("Connected to " + hSession.getHost() + (hSession.getSshForwardPort() > 0 ? " via " + hSession.getSshForwardPort(): ""));
            if (hSession.getForwardings().size() > 0)
                LOGGER.debug("Ports forwarded: ");
            for (int portToForward : hSession.getForwardings().keySet())
                LOGGER.debug(portToForward + " -> " + hSession.getForwardings().get(portToForward));
            //System.out.println(portToForward+" -> "+forwardings.get(portToForward));
        }

        openedConnections.put(session.getHost(), session);

        if(onConnect!=null)
            onConnect.apply(session);

    }

    public Map<String, HSession> getOpenedConnections() {
        return openedConnections;
    }

    public void closeSessions(){
        closing = true;
        stopTunnelMonitoring();

        for(String key : openedConnections.keySet()){
            HSession hSession = openedConnections.get(key);
            hSession.getSession().disconnect();
            openedConnections.remove(key);
            LOGGER.info("{} disconnected", hSession.host);
        }



    }


}
