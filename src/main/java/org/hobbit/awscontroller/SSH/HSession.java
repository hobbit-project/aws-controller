package org.hobbit.awscontroller.SSH;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HSession {
    private HSession parentSession;
    private Session session;
    private Map<Integer, Integer> forwardings = new HashMap<>();
    private int sshForwardPort = -1;
    UserInfo userInfo = new UserInfoImpl();
    String username;
    String host;
    int port;

    private static final Logger LOGGER = LoggerFactory.getLogger(HSession.class);

    public HSession(String username, String host, int port, String keyfilePath, String[] portsToForward, HSession parentSession){
        this.parentSession = parentSession;
        this.username = username;
        this.host = host;
        this.port = port;

        try {
            if(!new File(keyfilePath).exists())
                throw new Exception(keyfilePath+" not exists!");
            JSch jsch = new JSch();
            jsch.addIdentity(keyfilePath);


            if (parentSession != null) {
                sshForwardPort = parentSession.getSession().setPortForwardingL(0, host, 22);
                session = jsch.getSession(username, "127.0.0.1", sshForwardPort);
            } else {
                session = jsch.getSession(username, host, port);
            }
            session.setUserInfo(userInfo);
            //session.setHostKeyAlias(host);

            if (portsToForward != null)
                for (String portToForward0 : portsToForward) {
                    String hostToForward = "127.0.0.1";
                    String portToForward = portToForward0;
                    if(portToForward0.contains(":")) {
                        String[] splitted = portToForward0.split(":");
                        hostToForward=splitted[0];
                        portToForward = splitted[1];
                    }

                    int intPort = Integer.parseInt(portToForward);
                    int forwardedPort = session.setPortForwardingL(0, hostToForward, intPort);
                    //int forwardedPort = session.setPortForwardingL(0, "127.0.0.1", portToForward);
                    forwardings.put(intPort, forwardedPort);
                }


            session.setHostKeyAlias(host);

        }
        catch (Exception e){
            LOGGER.error("Cound not create session: {}", e.getLocalizedMessage());
            e.getStackTrace();
        }

    }

    public HSession(String username, String host, int port, String keyfilePath){
        this(username, host, port, keyfilePath, null, null);
    }

    public Session getSession(){
        return session;
    }

    public HSession getParentSession(){
        return parentSession;
    }

    public int getSshForwardPort(){
        return sshForwardPort;
    }

    public Map<Integer, Integer> getForwardings(){
        return forwardings;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }


    public static class UserInfoImpl implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String s) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String s) {
            return false;
        }

        @Override
        public boolean promptYesNo(String s) {
            return true;
        }

        @Override
        public void showMessage(String s) {

        }
    };

}
